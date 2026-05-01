package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import javax.inject.Inject

/**
 * SAFETY CRITICAL: Part of real-time hazard detection.
 * Any changes must be accompanied by unit tests.
 */

/**
 * Maps a [HazardType] to its corresponding [Severity] tier based on OSHA risk assessment.
 *
 * This is the single authoritative source of the [HazardType] → [Severity] mapping.
 * Called by [MonitorViewModel.onHazardsDetected] on every camera frame that produces
 * a detection, and by [LogHazardUseCase] before persisting to Room.
 *
 * **Mapping rationale:**
 * - CRITICAL: immediate life-safety or exit-blockage risk (OSHA wilful/repeat fine tier)
 * - HIGH: occupancy risk requiring prompt but not emergency response
 * - MEDIUM: common housekeeping hazards — addressable within the current shift
 * - LOW: unclassified detections requiring manual operator review
 */
// S: Single Responsibility — classifies hazard severity; no persistence, camera, or UI logic
// D: Dependency Inversion — no repository dependency; pure function injected via Hilt
class DetectHazardUseCase @Inject constructor() {

    /**
     * Returns the [Severity] tier for the given [hazardType].
     *
     * @param hazardType The [HazardType] reported by [HazardDetector].
     * @return [Severity] tier governing notification priority and escalation interval.
     */
    fun execute(hazardType: HazardType): Severity = when (hazardType) {
        HazardType.WET_FLOOR,
        HazardType.BLOCKED_EXIT,
        HazardType.FIRE_HAZARD -> Severity.CRITICAL

        HazardType.OVERCROWDING -> Severity.HIGH

        HazardType.FALLEN_ITEM,
        HazardType.TRIP_HAZARD,
        HazardType.UNATTENDED_SPILL -> Severity.MEDIUM

        HazardType.UNKNOWN -> Severity.LOW
    }
}
