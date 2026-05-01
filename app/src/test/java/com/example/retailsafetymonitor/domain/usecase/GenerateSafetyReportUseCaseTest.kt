package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.data.api.GeminiReportApi
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.SafetyReport
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.repository.ReportRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for [GenerateSafetyReportUseCase].
 *
 * Verifies that the use case:
 * 1. Queries the repository for the specified week
 * 2. Calls the Gemini API with a meaningful incident summary
 * 3. Saves the generated [SafetyReport] to [ReportRepository]
 * 4. Gracefully degrades when the Gemini API throws (network failure)
 *
 * All dependencies are mocked. The compliance score algorithm is exercised
 * indirectly; dedicated cases live in [ComplianceScoreTest].
 */
class GenerateSafetyReportUseCaseTest {

    private val hazardRepository: HazardRepository = mock()
    private val reportRepository: ReportRepository = mock()
    private val geminiApi: GeminiReportApi = mock()

    private lateinit var useCase: GenerateSafetyReportUseCase

    private val weekStart = 1_000_000L
    private val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        useCase = GenerateSafetyReportUseCase(hazardRepository, reportRepository, geminiApi)
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    fun `Empty week generates report with zero hazards and 100 compliance`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(weekStart, weekEnd)).thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("No incidents this week.")

        val report = useCase.execute(weekStart, weekEnd)

        assertEquals(0, report.totalHazards)
        assertEquals(100, report.complianceScore)
        assertEquals("No incidents this week.", report.aiSummary)
        verify(reportRepository).insertReport(any())
    }

    @Test
    fun `Full week generates report with correct hazard counts`() = runTest {
        val hazards = listOf(
            hazard(Severity.CRITICAL, resolved = true),
            hazard(Severity.HIGH, resolved = false),
            hazard(Severity.MEDIUM, resolved = true)
        )
        whenever(hazardRepository.getHazardsForWeek(weekStart, weekEnd)).thenReturn(flowOf(hazards))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("AI analysis here.")

        val report = useCase.execute(weekStart, weekEnd)

        assertEquals(3, report.totalHazards)
        assertEquals(2, report.resolvedHazards)
        assertEquals("AI analysis here.", report.aiSummary)
    }

    @Test
    fun `Report is saved to ReportRepository after generation`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Summary.")

        useCase.execute(weekStart, weekEnd)

        val captor = argumentCaptor<SafetyReport>()
        verify(reportRepository).insertReport(captor.capture())
        assertNotNull(captor.firstValue.id)
        assertEquals(weekStart, captor.firstValue.weekStartDate)
    }

    @Test
    fun `Gemini API returning success saves AI summary to report`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Pattern: Monday mornings are highest risk.")

        val report = useCase.execute(weekStart, weekEnd)

        assertTrue(report.aiSummary.contains("Monday mornings"))
    }

    // ─── Graceful degradation ─────────────────────────────────────────────────

    @Test
    fun `Gemini API failure produces fallback summary and still saves report`() = runTest {
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(geminiApi.generateWeeklySummary(any())).thenThrow(RuntimeException("Network unavailable"))

        val report = useCase.execute(weekStart, weekEnd)

        assertTrue(
            "Fallback summary should mention failure",
            report.aiSummary.contains("failed", ignoreCase = true)
        )
        // Report must still be saved even when Gemini fails
        verify(reportRepository).insertReport(any())
    }

    // ─── Top hazard types ─────────────────────────────────────────────────────

    @Test
    fun `Top hazard types are ordered by frequency descending`() = runTest {
        val hazards = listOf(
            hazard(Severity.MEDIUM, resolved = false, type = HazardType.WET_FLOOR),
            hazard(Severity.MEDIUM, resolved = false, type = HazardType.WET_FLOOR),
            hazard(Severity.HIGH, resolved = false, type = HazardType.OVERCROWDING)
        )
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(hazards))
        whenever(geminiApi.generateWeeklySummary(any())).thenReturn("Summary.")

        val report = useCase.execute(weekStart, weekEnd)

        assertEquals(HazardType.WET_FLOOR, report.topHazardTypes.first())
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun hazard(
        severity: Severity,
        resolved: Boolean,
        type: HazardType = HazardType.UNKNOWN
    ) = Hazard(
        id = UUID.randomUUID().toString(),
        type = type,
        severity = severity,
        imageUri = null,
        locationDescription = null,
        timestamp = weekStart + 1000L,
        isResolved = resolved
    )
}
