package com.example.retailsafetymonitor.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.retailsafetymonitor.data.api.GeminiReportApi
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.repository.ReportRepository
import com.example.retailsafetymonitor.domain.usecase.GenerateSafetyReportUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Tests verifying that the Gemini weekly-report pipeline degrades gracefully on failure.
 *
 * Two classes are exercised:
 *  1. [GenerateSafetyReportUseCase] — Gemini API throws → fallback summary used, report
 *     still persisted to Room. No exception leaks to the caller.
 *  2. [WeeklyReportWorker] — if [GenerateSafetyReportUseCase.execute] throws an
 *     unexpected exception (e.g. DB error) the worker returns [Result.retry] so
 *     WorkManager applies exponential backoff. Normal completion returns [Result.success].
 *
 * Gemini failures are handled INSIDE [GenerateSafetyReportUseCase] — the worker
 * only retries on truly unexpected failures (DB errors, OOM, etc.).
 */
@RunWith(RobolectricTestRunner::class)
class GeminiReportFailureTest {

    private lateinit var hazardRepository: HazardRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var geminiApi: GeminiReportApi
    private lateinit var context: Context

    private val weekStart = 1_000_000L
    private val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000

    // A single unresolved hazard to populate the report
    private val sampleHazard = Hazard(
        id = UUID.randomUUID().toString(),
        type = HazardType.WET_FLOOR,
        severity = Severity.HIGH,
        imageUri = null,
        locationDescription = "Aisle 4",
        timestamp = System.currentTimeMillis() - 30 * 60 * 1000L,
        isResolved = false
    )

    @Before
    fun setUp() {
        hazardRepository = mock()
        reportRepository = mock()
        geminiApi = mock()
        context = ApplicationProvider.getApplicationContext()
    }

    // ── Use-case: Gemini failure → fallback, report still saved ──────────────

    @Test
    fun `Gemini network timeout — fallback summary used, report still saved`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any()))
            .thenReturn(flowOf(listOf(sampleHazard)))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("Connection timeout"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        // Report is non-null and persisted despite Gemini failure
        assertNotNull(report)
        verify(reportRepository).insertReport(any())
        // Fallback text should be set (not blank, not the successful API response)
        assertTrue("Fallback summary should not be blank", report.aiSummary.isNotBlank())
    }

    @Test
    fun `Gemini 503 Service Unavailable — fallback summary mentions failure`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any()))
            .thenReturn(flowOf(listOf(sampleHazard)))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("503 Service Unavailable"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        // Fallback text should communicate the failure clearly to the store manager
        val summary = report.aiSummary.lowercase()
        assertTrue(
            "Fallback should mention unavailability or failure",
            summary.contains("unavailable") || summary.contains("failed") ||
            summary.contains("error") || summary.contains("manual")
        )
    }

    @Test
    fun `Gemini quota exceeded — report saved with fallback, no exception propagates`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("429 Too Many Requests — quota exceeded"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)

        // Should not throw
        val report = useCase.execute(weekStart, weekEnd)

        assertNotNull("Report must not be null even after Gemini failure", report)
        assertNotNull("AI summary must not be null after Gemini failure", report.aiSummary)
    }

    @Test
    fun `Gemini auth error — hazard data in report is still accurate`() = runTest {
        val resolvedHazard   = sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true)
        val unresolvedHazard = sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = false)

        whenever(hazardRepository.getHazardsForWeek(any(), any()))
            .thenReturn(flowOf(listOf(resolvedHazard, unresolvedHazard)))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("401 Unauthorized — invalid API key"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        // Non-AI fields must be accurate regardless of Gemini failure
        assertEquals(2, report.totalHazards)
        assertEquals(1, report.resolvedHazards)
    }

    @Test
    fun `Gemini fails — compliance score calculation is unaffected`() = runTest {
        // 5 hazards: 4 resolved, 1 critical unresolved → score reduced by penalty
        val hazards = listOf(
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true, severity = Severity.LOW),
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true, severity = Severity.MEDIUM),
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true, severity = Severity.HIGH),
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true, severity = Severity.MEDIUM),
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = false, severity = Severity.CRITICAL)
        )

        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(hazards))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("Gemini API down"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        // base = 80, criticalPenalty = 10 → score = 70
        assertEquals(70, report.complianceScore)
    }

    // ── Worker: doWork() retry / success ──────────────────────────────────────

    @Test
    fun `WeeklyReportWorker returns retry when DB exception is thrown`() = runTest {
        val useCase = mock<GenerateSafetyReportUseCase>()
        whenever(useCase.execute(any(), any())).thenThrow(RuntimeException("Room DB error"))

        val result = buildWorkerAndRun(useCase)

        assertEquals("DB error should cause retry", Result.retry(), result)
    }

    @Test
    fun `WeeklyReportWorker returns success when report generated`() = runTest {
        val useCase = mock<GenerateSafetyReportUseCase>()
        val fakeReport = com.example.retailsafetymonitor.domain.model.SafetyReport(
            id = UUID.randomUUID().toString(),
            weekStartDate = weekStart,
            totalHazards = 0,
            resolvedHazards = 0,
            complianceScore = 100,
            topHazardTypes = emptyList(),
            aiSummary = "All clear.",
            generatedAt = System.currentTimeMillis()
        )
        whenever(useCase.execute(any(), any())).thenReturn(fakeReport)

        val result = buildWorkerAndRun(useCase)

        assertEquals("Successful report generation should return success", Result.success(), result)
    }

    @Test
    fun `WeeklyReportWorker returns retry on OOM error`() = runTest {
        val useCase = mock<GenerateSafetyReportUseCase>()
        whenever(useCase.execute(any(), any()))
            .thenThrow(OutOfMemoryError("Low memory"))

        val result = try {
            buildWorkerAndRun(useCase)
        } catch (e: OutOfMemoryError) {
            // If the worker re-throws OOM, that's also acceptable — WorkManager handles it
            Result.retry()
        }

        // Either retry or exception propagated — the worker must not silently succeed
        assertTrue(
            "Worker must not return success on OOM",
            result != Result.success()
        )
    }

    @Test
    fun `Gemini failure within useCase does NOT cause worker retry`() = runTest {
        // Gemini failures are caught inside the use case → worker sees Result.success()
        val realUseCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        whenever(hazardRepository.getHazardsForWeek(any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any()))
            .thenThrow(RuntimeException("Gemini API down"))
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val result = buildWorkerAndRun(realUseCase)

        // Gemini failure is handled gracefully inside the use case — worker returns success
        assertEquals(
            "Gemini failure is graceful; worker should succeed",
            Result.success(), result
        )
    }

    // ── Use-case: compliance score algorithm (edge cases) ────────────────────

    @Test
    fun `empty hazard list always produces compliance score of 100`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Nothing to report.")
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        assertEquals(100, report.complianceScore)
        assertEquals(0, report.totalHazards)
    }

    @Test
    fun `all hazards resolved — compliance score 100 regardless of count`() = runTest {
        val allResolved = (1..10).map {
            sampleHazard.copy(id = UUID.randomUUID().toString(), isResolved = true)
        }
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(allResolved))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Excellent week.")
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        assertEquals(100, report.complianceScore)
    }

    @Test
    fun `critical penalty is capped at 40 — score never below 0`() = runTest {
        // 10 unresolved CRITICAL hazards → criticalPenalty = min(100, 40) = 40; base = 0 → score = 0
        val allCritical = (1..10).map {
            sampleHazard.copy(
                id = UUID.randomUUID().toString(),
                severity = Severity.CRITICAL,
                isResolved = false
            )
        }
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(allCritical))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Critical week.")
        whenever(reportRepository.insertReport(any())).thenReturn(Unit)

        val useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
        val report = useCase.execute(weekStart, weekEnd)

        assertTrue("Score must be ≥ 0", report.complianceScore >= 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun buildWorkerAndRun(useCase: GenerateSafetyReportUseCase): Result {
        val worker = TestListenableWorkerBuilder<WeeklyReportWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ) = WeeklyReportWorker(appContext, workerParameters, useCase)
            })
            .build()
        return worker.doWork()
    }
}
