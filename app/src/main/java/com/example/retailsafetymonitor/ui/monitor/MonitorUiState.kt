package com.example.retailsafetymonitor.ui.monitor

import android.graphics.RectF
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity

/**
 * State machine for the live camera monitoring screen.
 *
 * ```
 *   Idle ──startCamera()──► Monitoring(isModelReady=false)
 *                                │
 *                  First ML result: isModelReady=true
 *                                │
 *               Detection arrives ──► HazardDetected (5s auto-dismiss)
 *                                │         │
 *                                │    5s elapsed ──► Monitoring
 *                                │
 *                         FAB pause ──► Paused ──► (FAB resume) ──► Monitoring
 *                                │
 *                    Permission denied ──► Error
 *                                          │
 *                             Permission granted ──► Idle (restart)
 * ```
 */
sealed interface MonitorUiState {

    /** Camera is not started. Initial state and state after [MonitorViewModel.stopCamera]. */
    data object Idle : MonitorUiState

    /**
     * CameraX is bound and ML Kit is processing frames.
     *
     * @property detectedHazards Active bounding-box overlays for the current frame.
     * @property isModelReady False during the first-frame warm-up; true once ML Kit fires a result.
     * @property lastDetectionTime Epoch millis of the last non-empty frame, or null if none yet.
     */
    data class Monitoring(
        val detectedHazards: List<HazardOverlayData> = emptyList(),
        val isModelReady: Boolean = false,
        val lastDetectionTime: Long? = null
    ) : MonitorUiState

    /**
     * A hazard has been logged to Room. The bottom sheet expands and auto-dismisses after 5 seconds.
     *
     * @property hazard The newly logged [Hazard] shown in the bottom sheet [HazardCard].
     * @property allActiveHazards Full overlay list for the current frame (may be updated while
     *   the sheet is open).
     */
    data class HazardDetected(
        val hazard: Hazard,
        val allActiveHazards: List<HazardOverlayData>
    ) : MonitorUiState

    /** User has paused monitoring via the FAB. Camera is unbound; workers continue. */
    data object Paused : MonitorUiState

    /**
     * An unrecoverable error (e.g., camera binding failed, permission revoked).
     *
     * @property message Human-readable error description shown in [ErrorView].
     */
    data class Error(val message: String) : MonitorUiState
}

/**
 * Overlay data for a single bounding box rendered by [HazardOverlay].
 *
 * @property boundingBox Normalized bounding box (0.0–1.0). [HazardDetector.processFrame]
 *   normalizes from ML Kit pixel-space using rotated image dimensions.
 *   [HazardOverlay] multiplies by canvas size to get actual pixel coordinates.
 * @property hazardType The [HazardType] label drawn above the box.
 * @property severity The [Severity] that determines the box border color via [Severity.color].
 * @property trackingId ML Kit's per-object tracking ID across frames, or null.
 */
data class HazardOverlayData(
    val boundingBox: RectF,
    val hazardType: HazardType,
    val severity: Severity,
    val trackingId: Int?
)
