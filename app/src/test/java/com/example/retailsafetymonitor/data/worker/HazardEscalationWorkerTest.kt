package com.example.retailsafetymonitor.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.retailsafetymonitor.data.notification.HazardNotificationManager
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Unit tests for [HazardEscalationWorker].
 *
 * Verifies idempotency logic (a hazard should fire at most one notification
 * per escalation interval), per-severity age thresholds, and failure handling.
 *
 * Uses [TestListenableWorkerBuilder] from `work-testing` to run the worker
 * without a real [WorkManager] scheduler, and Mockito-Kotlin to mock
 * [HazardRepository] and [HazardNotificationManager].
 *
 * **Critical invariant tested:**
 * The [HazardEscalationWorker] must be idempotent. If it fires every 15 minutes
 * and a hazard is never resolved, the worker should send AT MOST one notification
 * per escalation interval, not one per worker run.
 */
@RunWith(RobolectricTestRunner::class)
class HazardEscalationWorkerTest {

    private val hazardRepository: HazardRepository = mock()
    private val notificationManager: HazardNotificationManager = mock()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ─── Age threshold met + first escalation ─────────────────────────────────

    @Test
    fun `CRITICAL hazard older than 15 min with no prior escalation fires notification`() = runTest {
        val hazard = criticalHazard(ageMs = 16 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        val result = buildAndRun()

        assertEquals(Result.success(), result)
        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
        verify(hazardRepository, times(1)).updateLastEscalatedAt(hazard.id, any())
    }

    @Test
    fun `HIGH hazard older than 30 min fires notification`() = runTest {
        val hazard = hazardWithSeverity(Severity.HIGH, ageMs = 31 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    @Test
    fun `MEDIUM hazard older than 60 min fires notification`() = runTest {
        val hazard = hazardWithSeverity(Severity.MEDIUM, ageMs = 61 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    @Test
    fun `LOW hazard older than 120 min fires notification`() = runTest {
        val hazard = hazardWithSeverity(Severity.LOW, ageMs = 121 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    // ─── Idempotency: cooldown not yet elapsed ─────────────────────────────────

    @Test
    fun `CRITICAL hazard last escalated 14 min ago does NOT fire again`() = runTest {
        val now = System.currentTimeMillis()
        // lastEscalatedAt = 14 min ago, interval = 15 min → cooldown not elapsed
        val hazard = criticalHazard(
            ageMs = 30 * 60 * 1000L,
            lastEscalatedAt = now - 14 * 60 * 1000L
        )
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    @Test
    fun `CRITICAL hazard last escalated exactly 15 min ago fires again`() = runTest {
        val now = System.currentTimeMillis()
        // lastEscalatedAt = 15 min ago, interval = 15 min → cooldown just elapsed
        val hazard = criticalHazard(
            ageMs = 30 * 60 * 1000L,
            lastEscalatedAt = now - 15 * 60 * 1000L - 1L  // 1ms past interval
        )
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    // ─── Age threshold NOT met ────────────────────────────────────────────────

    @Test
    fun `HIGH hazard only 29 min old does not fire notification`() = runTest {
        val hazard = hazardWithSeverity(Severity.HIGH, ageMs = 29 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))

        buildAndRun()

        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    // ─── No unresolved hazards ────────────────────────────────────────────────

    @Test
    fun `No unresolved hazards fires no notifications and returns success`() = runTest {
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(emptyList())

        val result = buildAndRun()

        assertEquals(Result.success(), result)
        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    // ─── Selective escalation ─────────────────────────────────────────────────

    @Test
    fun `Only hazards meeting threshold are notified not all unresolved`() = runTest {
        val oldCritical = criticalHazard(ageMs = 20 * 60 * 1000L, lastEscalatedAt = null)
        val youngCritical = criticalHazard(ageMs = 5 * 60 * 1000L, lastEscalatedAt = null) // too young
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenReturn(listOf(oldCritical, youngCritical))

        buildAndRun()

        // Only oldCritical should be notified
        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    // ─── Failure handling ─────────────────────────────────────────────────────

    @Test
    fun `Repository exception causes retry result`() = runTest {
        whenever(hazardRepository.getAllUnresolvedSnapshot()).thenThrow(RuntimeException("DB error"))

        val result = buildAndRun()

        assertEquals(Result.retry(), result)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun buildAndRun(): Result {
        val worker = TestListenableWorkerBuilder<HazardEscalationWorker>(context)
            .setWorkerFactory(
                TestWorkerFactory(hazardRepository, notificationManager)
            )
            .build()
        return worker.doWork()
    }

    private fun criticalHazard(ageMs: Long, lastEscalatedAt: Long?): Hazard {
        return hazardWithSeverity(Severity.CRITICAL, ageMs, lastEscalatedAt)
    }

    private fun hazardWithSeverity(severity: Severity, ageMs: Long, lastEscalatedAt: Long?): Hazard {
        return Hazard(
            id = UUID.randomUUID().toString(),
            type = HazardType.UNKNOWN,
            severity = severity,
            imageUri = null,
            locationDescription = null,
            timestamp = System.currentTimeMillis() - ageMs,
            isResolved = false,
            lastEscalatedAt = lastEscalatedAt
        )
    }

    private fun any(): Long = org.mockito.kotlin.any()

    /**
     * Minimal [WorkerFactory] for testing that injects mocked dependencies
     * without Hilt.
     */
    private inner class TestWorkerFactory(
        private val repo: HazardRepository,
        private val notifications: HazardNotificationManager
    ) : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: androidx.work.WorkerParameters
        ) = HazardEscalationWorker(appContext, workerParameters, repo, notifications)
    }
}
