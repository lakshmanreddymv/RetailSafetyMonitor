package com.example.retailsafetymonitor.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.retailsafetymonitor.data.notification.HazardNotificationManager
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * SAFETY CRITICAL: Part of real-time hazard detection.
 * Any changes must be accompanied by unit tests.
 */

/**
 * Periodic WorkManager job that escalates unresolved hazards via local notifications.
 * Runs every 15 minutes (platform minimum). Does NOT access the camera — camera access
 * requires a while-in-use permission unavailable to WorkManager on Android 12+.
 *
 * **Idempotency contract:**
 * Each hazard has a [Hazard.lastEscalatedAt] field. The worker only fires a
 * notification if both:
 * 1. The hazard's age ≥ [Severity.escalationIntervalMs] (age threshold), AND
 * 2. [now - lastEscalatedAt] ≥ [Severity.escalationIntervalMs] (cooldown elapsed).
 *
 * This prevents a CRITICAL hazard from generating 8 notifications in 2 hours when
 * the manager is unavailable. Each hazard generates at most one notification per
 * escalation interval.
 *
 * **Failure handling:**
 * - DB exception → [Result.retry] (WorkManager applies exponential backoff)
 * - Notifications disabled → log and return [Result.success] (don't fail the job)
 *
 * Uses [@HiltWorker][dagger.hilt.android.AndroidEntryPoint] + [@AssistedInject] for
 * Hilt injection. Requires [HiltWorkerFactory] registered in [RetailSafetyMonitorApp].
 */
@HiltWorker
class HazardEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val hazardRepository: HazardRepository,
    private val notificationManager: HazardNotificationManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val unresolvedHazards = hazardRepository.getAllUnresolvedSnapshot()
            val now = System.currentTimeMillis()

            unresolvedHazards.forEach { hazard ->
                val ageMs = now - hazard.timestamp
                val escalationIntervalMs = hazard.severity.escalationIntervalMs
                val lastEscalated = hazard.lastEscalatedAt ?: 0L

                // Check age threshold AND idempotency: only notify if interval has elapsed
                val ageThresholdMet = ageMs >= escalationIntervalMs
                val cooldownElapsed = (now - lastEscalated) >= escalationIntervalMs

                if (ageThresholdMet && cooldownElapsed) {
                    val ageMinutes = ageMs / 60_000L
                    notificationManager.sendEscalationNotification(hazard, ageMinutes)
                    hazardRepository.updateLastEscalatedAt(hazard.id, now)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
