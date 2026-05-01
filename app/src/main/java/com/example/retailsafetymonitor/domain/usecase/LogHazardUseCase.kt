package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.data.notification.HazardNotificationManager
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Persists a newly detected hazard to Room and fires the initial detection notification.
 *
 * Called by [MonitorViewModel] for the highest-severity detection in each camera frame
 * that passes [HazardDetector]'s 60-second per-type cooldown. Each invocation:
 * 1. Generates a UUID for the new hazard.
 * 2. Inserts it via [HazardRepository.insertHazard].
 * 3. Fires an immediate local notification via [HazardNotificationManager].
 *
 * The notification is the first alert; escalation reminders are handled separately
 * by [HazardEscalationWorker] for hazards that remain unresolved.
 */
// S: Single Responsibility — creates and persists one hazard record; no severity mapping logic
// D: Dependency Inversion — depends on [HazardRepository] interface, not [HazardRepositoryImpl]
class LogHazardUseCase @Inject constructor(
    private val hazardRepository: HazardRepository,
    private val notificationManager: HazardNotificationManager
) {
    /**
     * Creates a [Hazard], persists it to Room, and fires the detection notification.
     *
     * @param hazardType The [HazardType] as classified by [HazardDetector].
     * @param severity The [Severity] as determined by [DetectHazardUseCase].
     * @param imageUri Optional local URI of a captured evidence photo.
     * @param locationDescription Optional free-text location string (e.g., "Aisle 3").
     * @return The newly created and persisted [Hazard] domain object.
     */
    suspend fun execute(
        hazardType: HazardType,
        severity: Severity,
        imageUri: String? = null,
        locationDescription: String? = null
    ): Hazard {
        val hazard = Hazard(
            id = UUID.randomUUID().toString(),
            type = hazardType,
            severity = severity,
            imageUri = imageUri,
            locationDescription = locationDescription,
            timestamp = System.currentTimeMillis()
        )
        hazardRepository.insertHazard(hazard)
        notificationManager.sendHazardDetectedNotification(hazard)
        return hazard
    }
}
