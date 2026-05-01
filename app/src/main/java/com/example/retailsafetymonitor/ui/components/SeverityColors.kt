package com.example.retailsafetymonitor.ui.components

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import com.example.retailsafetymonitor.domain.model.Severity

/**
 * Maps [Severity] to the Compose [Color] used for bounding box borders in [HazardOverlay]
 * and badge backgrounds in [SeverityBadge].
 *
 * Color semantics match OSHA visual safety standards:
 * - CRITICAL → red (danger)
 * - HIGH → amber (warning)
 * - MEDIUM → yellow (caution)
 * - LOW → green (advisory)
 */
val Severity.color: Color
    get() = when (this) {
        Severity.CRITICAL -> Color(0xFFE53935)
        Severity.HIGH -> Color(0xFFFB8C00)
        Severity.MEDIUM -> Color(0xFFFFEB3B)
        Severity.LOW -> Color(0xFF43A047)
    }

/**
 * Maps [Severity] to a packed ARGB [Int] for use with Android's native [Paint] API
 * in [HazardOverlay]'s Canvas `drawText` calls.
 *
 * Mirrors [color] but in the `android.graphics.Color` integer format required by [SeverityPaintCache].
 */
val Severity.androidColor: Int
    get() = when (this) {
        Severity.CRITICAL -> 0xFFE53935.toInt()
        Severity.HIGH -> 0xFFFB8C00.toInt()
        Severity.MEDIUM -> 0xFFFFEB3B.toInt()
        Severity.LOW -> 0xFF43A047.toInt()
    }

/**
 * Lazily created map of [Severity] → [Paint] instances used by [HazardOverlay] for
 * drawing label text on the Canvas.
 *
 * Allocated once on first access and reused for every frame. Avoids creating a new
 * [Paint] object on each of the ~20 frames/second delivered by [CameraManager].
 */
object SeverityPaintCache {
    val paints: Map<Severity, Paint> by lazy {
        Severity.entries.associateWith { sev ->
            Paint().apply {
                color = sev.androidColor
                textSize = 36f
                isAntiAlias = true
                isFakeBoldText = true
            }
        }
    }
}
