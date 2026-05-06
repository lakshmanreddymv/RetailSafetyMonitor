package com.example.retailsafetymonitor.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.retailsafetymonitor.data.notification.HazardNotificationManager
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * WorkManager kill-and-recovery tests for [HazardEscalationWorker].
 *
 * Simulates the OS killing the WorkManager process and restarting it:
 *  - After a retry result, a subsequent successful run completes normally.
 *  - Hazards pending escalation at kill time are still escalated on restart.
 *  - No duplicate alerts are sent for hazards that were already escalated
 *    before the kill (idempotency contract preserved across restarts).
 *
 * Uses [TestListenableWorkerBuilder] + Robolectric for the Android [Context].
 * Two consecutive [doWork] calls simulate kill → restart with the same hazard data.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerKillRecoveryTest {

    private lateinit var hazardRepo: HazardRepository
    private lateinit var notificationManager: HazardNotificationManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        hazardRepo           = mock()
        notificationManager  = mock()
        context              = ApplicationProvider.getApplicationContext()
    }

    // ── Retry then succeed ────────────────────────────────────────────────────

    @Test
    fun `first run throws — returns retry — second run succeeds`() = runTest {
        // Simulate DB failure on first call, success on second
        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenThrow(RuntimeException("DB locked"))
            .thenReturn(emptyList())

        val firstResult  = buildAndRun(hazardRepo, notificationManager)
        val secondResult = buildAndRun(hazardRepo, notificationManager)

        assertEquals("First run should retry on DB failure", Result.retry(), firstResult)
        assertEquals("Second run should succeed after DB recovers", Result.success(), secondResult)
    }

    @Test
    fun `retry result does not fire any notifications`() = runTest {
        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenThrow(RuntimeException("Transient DB error"))

        buildAndRun(hazardRepo, notificationManager)

        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    // ── Pending escalation preserved across kill ──────────────────────────────

    @Test
    fun `hazard pending at kill time is escalated when worker restarts`() = runTest {
        // A CRITICAL hazard that needed escalation was not notified during the killed run
        // (the first run failed before reaching sendEscalationNotification).
        // On restart it should be detected and notified.
        val pendingHazard = hazardWithSeverity(
            Severity.CRITICAL, ageMs = 20 * 60 * 1000L, lastEscalatedAt = null
        )
        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenThrow(RuntimeException("Killed before processing"))  // first run killed
            .thenReturn(listOf(pendingHazard))                         // worker restarted

        whenever(hazardRepo.updateLastEscalatedAt(any(), any())).thenReturn(Unit)

        buildAndRun(hazardRepo, notificationManager)  // killed
        buildAndRun(hazardRepo, notificationManager)  // restarted

        // After recovery the hazard must be escalated
        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    @Test
    fun `multiple pending hazards at kill time — all escalated after restart`() = runTest {
        val hazard1 = hazardWithSeverity(Severity.CRITICAL, ageMs = 30 * 60 * 1000L, lastEscalatedAt = null)
        val hazard2 = hazardWithSeverity(Severity.HIGH,     ageMs = 45 * 60 * 1000L, lastEscalatedAt = null)
        val hazard3 = hazardWithSeverity(Severity.MEDIUM,   ageMs = 90 * 60 * 1000L, lastEscalatedAt = null)

        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenThrow(RuntimeException("OS kill"))
            .thenReturn(listOf(hazard1, hazard2, hazard3))

        whenever(hazardRepo.updateLastEscalatedAt(any(), any())).thenReturn(Unit)

        buildAndRun(hazardRepo, notificationManager)  // killed
        buildAndRun(hazardRepo, notificationManager)  // restarted

        // All three hazards met their thresholds — all three must fire
        verify(notificationManager, times(3)).sendEscalationNotification(any(), any())
    }

    // ── No duplicate alerts after recovery ────────────────────────────────────

    @Test
    fun `hazard escalated just before kill — no duplicate on restart`() = runTest {
        val now = System.currentTimeMillis()
        // lastEscalatedAt = 5 minutes ago; interval = 15 minutes → cooldown not elapsed
        val recentlyEscalated = hazardWithSeverity(
            severity       = Severity.CRITICAL,
            ageMs          = 60 * 60 * 1000L,  // 1 hour old
            lastEscalatedAt = now - 5 * 60 * 1000L   // escalated 5 min ago
        )

        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenReturn(listOf(recentlyEscalated))  // worker restarted, DB has updated timestamp

        buildAndRun(hazardRepo, notificationManager)

        // Cooldown not yet elapsed — no duplicate notification
        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    @Test
    fun `two consecutive runs — second run respects cooldown set by first run`() = runTest {
        val now = System.currentTimeMillis()
        val hazardId = UUID.randomUUID().toString()

        // First run: cooldown not set yet
        val hazardBeforeFirstRun = Hazard(
            id = hazardId, type = HazardType.WET_FLOOR, severity = Severity.CRITICAL,
            imageUri = null, locationDescription = null,
            timestamp = now - 20 * 60 * 1000L, isResolved = false, lastEscalatedAt = null
        )
        // Second run: simulates DB row updated by first run's updateLastEscalatedAt()
        val hazardAfterFirstRun = hazardBeforeFirstRun.copy(
            lastEscalatedAt = now - 1_000L  // just escalated 1 second ago
        )

        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenReturn(listOf(hazardBeforeFirstRun))
            .thenReturn(listOf(hazardAfterFirstRun))

        whenever(hazardRepo.updateLastEscalatedAt(any(), any())).thenReturn(Unit)

        buildAndRun(hazardRepo, notificationManager)  // first run
        buildAndRun(hazardRepo, notificationManager)  // second run (OS restart simulation)

        // Only ONE notification fired across both runs
        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    @Test
    fun `recovered worker — hazard whose cooldown lapsed after kill fires exactly once`() = runTest {
        val now = System.currentTimeMillis()
        // Last escalated 16 minutes ago (just past the 15-min interval) → should notify once
        val hazard = hazardWithSeverity(
            severity        = Severity.CRITICAL,
            ageMs           = 60 * 60 * 1000L,
            lastEscalatedAt = now - 16 * 60 * 1000L
        )

        whenever(hazardRepo.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))
        whenever(hazardRepo.updateLastEscalatedAt(any(), any())).thenReturn(Unit)

        val result = buildAndRun(hazardRepo, notificationManager)

        assertEquals(Result.success(), result)
        verify(notificationManager, times(1)).sendEscalationNotification(any(), any())
    }

    // ── Idempotency across empty runs ─────────────────────────────────────────

    @Test
    fun `kill during empty run — restart with no hazards returns success silently`() = runTest {
        whenever(hazardRepo.getAllUnresolvedSnapshot())
            .thenThrow(RuntimeException("Empty run killed"))
            .thenReturn(emptyList())

        val firstResult  = buildAndRun(hazardRepo, notificationManager)
        val secondResult = buildAndRun(hazardRepo, notificationManager)

        assertEquals(Result.retry(), firstResult)
        assertEquals(Result.success(), secondResult)
        verify(notificationManager, never()).sendEscalationNotification(any(), any())
    }

    // ── Result values ─────────────────────────────────────────────────────────

    @Test
    fun `clean run with no unresolved hazards always returns success`() = runTest {
        whenever(hazardRepo.getAllUnresolvedSnapshot()).thenReturn(emptyList())

        val result = buildAndRun(hazardRepo, notificationManager)

        assertEquals(Result.success(), result)
    }

    @Test
    fun `clean run with qualifying hazards returns success`() = runTest {
        val hazard = hazardWithSeverity(Severity.HIGH, ageMs = 35 * 60 * 1000L, lastEscalatedAt = null)
        whenever(hazardRepo.getAllUnresolvedSnapshot()).thenReturn(listOf(hazard))
        whenever(hazardRepo.updateLastEscalatedAt(any(), any())).thenReturn(Unit)

        val result = buildAndRun(hazardRepo, notificationManager)

        assertEquals(Result.success(), result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun buildAndRun(
        repo: HazardRepository,
        nm: HazardNotificationManager
    ): Result {
        val worker = TestListenableWorkerBuilder<HazardEscalationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ) = HazardEscalationWorker(appContext, workerParameters, repo, nm)
            })
            .build()
        return worker.doWork()
    }

    private fun hazardWithSeverity(severity: Severity, ageMs: Long, lastEscalatedAt: Long?): Hazard =
        Hazard(
            id = UUID.randomUUID().toString(),
            type = HazardType.WET_FLOOR,
            severity = severity,
            imageUri = null,
            locationDescription = null,
            timestamp = System.currentTimeMillis() - ageMs,
            isResolved = false,
            lastEscalatedAt = lastEscalatedAt
        )

    private fun any(): Long = org.mockito.kotlin.any()
}
