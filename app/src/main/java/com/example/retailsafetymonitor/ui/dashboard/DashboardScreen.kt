package com.example.retailsafetymonitor.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.retailsafetymonitor.ui.components.ComplianceGauge
import com.example.retailsafetymonitor.ui.components.HazardCard
import com.example.retailsafetymonitor.ui.theme.Primary
import com.example.retailsafetymonitor.ui.theme.Secondary
import com.example.retailsafetymonitor.ui.theme.Tertiary

/**
 * Weekly safety dashboard showing the compliance gauge, stat chips, report generation,
 * and the 10 most recent hazard cards.
 *
 * The compliance score is scoped to the **current calendar week** and updates automatically
 * as new hazards are detected or resolved, without requiring a manual refresh.
 *
 * @param onNavigateToIncidents Called when the user taps "See All" to navigate to
 *   [IncidentsScreen] for the full incident history.
 * @param viewModel Hilt-injected [DashboardViewModel].
 */
@Composable
fun DashboardScreen(
    onNavigateToIncidents: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Safety Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "This Week's Performance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Compliance Score
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ComplianceGauge(score = uiState.complianceScore)
            }
        }

        // Metric Cards — horizontal equal-weight row across full screen width
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(label = "Detected", value = "${uiState.totalDetected}", modifier = Modifier.weight(1f))
                    StatChip(label = "Resolved", value = "${uiState.totalResolved}", modifier = Modifier.weight(1f))
                    StatChip(
                        label = "Unresolved",
                        value = "${uiState.totalDetected - uiState.totalResolved}",
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.topHazardType?.let {
                    StatChip(label = "Top Risk", value = it.displayName, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Generate Report Button
        item {
            OutlinedButton(
                onClick = viewModel::generateWeeklyReport,
                enabled = !uiState.isGeneratingReport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (uiState.isGeneratingReport) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Generate Weekly AI Report")
            }
            uiState.reportError?.let {
                Text(
                    text = "AI report unavailable · Check connection",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Recent incidents header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.TextButton(onClick = onNavigateToIncidents) {
                    Text("See All")
                }
            }
        }

        if (uiState.weeklyHazards.isEmpty()) {
            item { ZoneHealthOverview() }
        } else {
            items(uiState.weeklyHazards.take(10)) { hazard ->
                HazardCard(hazard = hazard, onResolve = { /* handle in incidents screen */ })
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Metric card displaying a prominent value and its label.
 * Used in [DashboardScreen] for Detected / Resolved / Unresolved / Top Risk counts.
 *
 * @param label Descriptive text shown below the value (e.g., "Detected").
 * @param value The metric value as a formatted string (e.g., "12").
 * @param modifier Layout modifier — typically [Modifier.weight] for equal-width rows.
 */
@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Zone-level safety status shown in [ZoneHealthOverview]. */
private enum class SafetyLevel { CRITICAL, WARNING, SAFE }

/** Associates a named store zone with its current [SafetyLevel]. */
private data class ZoneStatus(val name: String, val level: SafetyLevel)

/**
 * Displays current safety status for each monitored store zone.
 * Shown as the empty state when no hazards are detected this week.
 *
 * Each zone renders a colored status dot followed by the zone name:
 * CRITICAL → red ([Primary]), WARNING → amber ([Secondary]), SAFE → green ([Tertiary]).
 * A "No hazards detected" confirmation appears below the list when all zones are safe.
 */
@Composable
private fun ZoneHealthOverview() {
    val zones = listOf(
        ZoneStatus("Checkout", SafetyLevel.SAFE),
        ZoneStatus("Loading Dock", SafetyLevel.SAFE),
        ZoneStatus("Floor A", SafetyLevel.SAFE),
        ZoneStatus("Entrance", SafetyLevel.SAFE)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Zone Health Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            zones.forEach { zone ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val dotColor = when (zone.level) {
                        SafetyLevel.CRITICAL -> Primary
                        SafetyLevel.WARNING  -> Secondary
                        SafetyLevel.SAFE     -> Tertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(zone.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (zones.all { it.level == SafetyLevel.SAFE }) {
                Text(
                    text = "No hazards detected this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
