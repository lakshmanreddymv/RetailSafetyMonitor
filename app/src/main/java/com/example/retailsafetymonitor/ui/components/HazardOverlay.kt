package com.example.retailsafetymonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.retailsafetymonitor.ui.monitor.HazardOverlayData

/**
 * Canvas composable that draws severity-colored bounding boxes on top of the
 * live camera preview.
 *
 * **Coordinate system:** [HazardOverlayData.boundingBox] values are **normalized**
 * (0.0–1.0). This composable multiplies them by [androidx.compose.ui.geometry.Size]
 * to get actual pixel positions, so the overlay is resolution-independent and
 * correct for any screen size or orientation.
 *
 * Bounding boxes come from [HazardDetector.processFrame], which normalizes ML Kit's
 * pixel-space results using the rotated image dimensions. The `camera-mlkit-vision`
 * bridge library (which provided `COORDINATE_SYSTEM_VIEW_REFERENCED`) does not exist
 * at CameraX 1.3.4 — this normalization approach replaces it.
 *
 * @param detectedHazards Active detections for the current frame. May be empty.
 */
@Composable
fun HazardOverlay(
    detectedHazards: List<HazardOverlayData>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        detectedHazards.forEach { hazard ->
            // boundingBox is normalized 0.0–1.0; scale to canvas pixels
            val norm = hazard.boundingBox
            val left   = norm.left   * w
            val top    = norm.top    * h
            val right  = norm.right  * w
            val bottom = norm.bottom * h
            val boxW   = right - left
            val boxH   = bottom - top

            // Skip degenerate boxes (can occur on first warm-up frames)
            if (boxW <= 0f || boxH <= 0f) return@forEach

            drawRect(
                color = hazard.severity.color,
                topLeft = Offset(left, top),
                size = Size(boxW, boxH),
                style = Stroke(width = 4.dp.toPx())
            )

            // Clamp label above box; fall below if box is at the top of the view
            val textY = if (top > 24.dp.toPx()) top - 8.dp.toPx() else bottom + 16.dp.toPx()

            drawContext.canvas.nativeCanvas.drawText(
                "${hazard.hazardType.displayName} · ${hazard.severity.name}",
                left + 4.dp.toPx(),
                textY,
                SeverityPaintCache.paints[hazard.severity]!!
            )
        }
    }
}
