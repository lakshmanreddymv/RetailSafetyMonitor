package com.example.retailsafetymonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retailsafetymonitor.domain.model.Severity

/**
 * Compact colored pill displaying the [Severity] tier name.
 *
 * Background color is drawn from [Severity.color] (via [SeverityColors.kt]).
 * Used inline in [HazardCard] next to the hazard type title.
 *
 * @param severity The [Severity] level to display.
 * @param modifier Optional [Modifier] for layout customization.
 */
@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    Text(
        text = severity.name,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(severity.color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
