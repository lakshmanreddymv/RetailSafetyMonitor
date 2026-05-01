package com.example.retailsafetymonitor.domain.model

/**
 * Operational status of the real-time camera-based hazard detection pipeline.
 *
 * Used by [MonitorViewModel] and surface-level analytics. The source of truth for
 * current status is [MonitorUiState]; this enum provides a flat representation
 * for logging and WorkManager constraint checks.
 */
// S: Single Responsibility — enumerates the four possible monitoring pipeline states
// D: Dependency Inversion — no framework dependencies; domain-layer pure enum
enum class MonitoringStatus {

    /** CameraX is bound, ML Kit is processing frames, detections are being emitted. */
    ACTIVE,

    /** User paused monitoring via the FAB; CameraX is unbound but WorkManager workers continue. */
    PAUSED,

    /** Monitoring has been stopped and the camera resource fully released. */
    STOPPED,

    /** An unrecoverable error occurred (e.g., camera permission revoked mid-session). */
    ERROR
}
