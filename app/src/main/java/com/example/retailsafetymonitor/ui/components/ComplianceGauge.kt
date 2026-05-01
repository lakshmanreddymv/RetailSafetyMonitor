package com.example.retailsafetymonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated circular arc gauge displaying a compliance score from 0 to 100.
 *
 * The arc sweeps 270 degrees (135° start, counter-clockwise gap at bottom).
 * Score color transitions:
 * - ≥ 80 → green (safe)
 * - 60–79 → amber (warning)
 * - < 60 → red (critical)
 *
 * Score changes animate over 800 ms using a linear tween. Used on [DashboardScreen]
 * and [ReportScreen] side-by-side with stat chips.
 *
 * @param score Compliance score in the range [0, 100].
 * @param modifier Optional [Modifier] for layout placement.
 * @param size Diameter of the gauge. Defaults to 160 dp.
 */
@Composable
fun ComplianceGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "compliance_score"
    )

    val gaugeColor = when {
        score >= 80 -> Color(0xFF43A047)
        score >= 60 -> Color(0xFFFB8C00)
        else -> Color(0xFFE53935)
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background arc
            drawArc(
                color = Color(0xFFE0E0E0),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Score arc
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = 270f * (animatedScore / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${score}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = gaugeColor
            )
            Text(
                text = "Compliance",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
