package com.example.retailsafetymonitor.domain.model

/**
 * Domain model representing a detected safety hazard.
 *
 * Created by [LogHazardUseCase] on detection and persisted to Room via
 * [HazardEntity]. Resolved hazards are kept for compliance score calculation
 * and audit history.
 *
 * @property id UUID assigned at detection time.
 * @property type The kind of hazard detected — see [HazardType].
 * @property severity OSHA-aligned severity tier — see [Severity].
 * @property imageUri Local URI of the captured evidence photo, or null if not captured.
 * @property locationDescription Optional free-text location (e.g., "Aisle 3").
 * @property timestamp Epoch millis when the hazard was first detected.
 * @property isResolved True once a manager has acknowledged and resolved the hazard.
 * @property resolvedAt Epoch millis of resolution, or null if still open.
 * @property resolvedBy Display name of the manager who resolved it, or null.
 * @property notes Optional manager notes added at resolution.
 * @property lastEscalatedAt Epoch millis of the last escalation notification sent by
 *   [HazardEscalationWorker]. Used for idempotency: the worker skips re-notifying
 *   until [Severity.escalationIntervalMs] has elapsed since this timestamp.
 */
// S: Single Responsibility — represents a detected hazard as a pure domain value object
// D: Dependency Inversion — no framework dependencies; consumed by all layers via this type only
data class Hazard(
    val id: String,
    val type: HazardType,
    val severity: Severity,
    val imageUri: String?,
    val locationDescription: String?,
    val timestamp: Long,
    val isResolved: Boolean = false,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val notes: String? = null,
    val lastEscalatedAt: Long? = null
)
