package com.example.retailsafetymonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.retailsafetymonitor.domain.model.Hazard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Material 3 card displaying a single [Hazard] with detection time, severity badge,
 * and an optional "Mark Resolved" action.
 *
 * Visual state:
 * - Unresolved: standard surface elevation, primary action visible.
 * - Resolved: [MaterialTheme.colorScheme.surfaceVariant] background, resolution time shown,
 *   no action button.
 *
 * Used in [IncidentsScreen] (with resolve callback), [DashboardScreen] (read-only),
 * and [MonitorScreen]'s bottom sheet (read-only).
 *
 * @param hazard The [Hazard] to render.
 * @param onResolve Callback invoked with the hazard's UUID when "Mark Resolved" is tapped.
 *   Pass a no-op lambda `{}` in read-only contexts.
 * @param modifier Optional [Modifier] for layout customization.
 */
@Composable
fun HazardCard(
    hazard: Hazard,
    onResolve: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hazard.isResolved)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hazard.isResolved) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hazard.type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SeverityBadge(hazard.severity)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detected: ${dateFormat.format(Date(hazard.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hazard.isResolved && hazard.resolvedAt != null) {
                Text(
                    text = "Resolved: ${dateFormat.format(Date(hazard.resolvedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = { onResolve(hazard.id) }) {
                    Text("Mark Resolved")
                }
            }
        }
    }
}
