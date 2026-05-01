package com.example.retailsafetymonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.retailsafetymonitor.ui.components.ComplianceGauge
import com.example.retailsafetymonitor.ui.components.HazardCard

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

        // Compliance Score + Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComplianceGauge(score = uiState.complianceScore)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(label = "Detected", value = "${uiState.totalDetected}")
                    StatChip(label = "Resolved", value = "${uiState.totalResolved}")
                    StatChip(
                        label = "Unresolved",
                        value = "${uiState.totalDetected - uiState.totalResolved}"
                    )
                    uiState.topHazardType?.let {
                        StatChip(label = "Top Risk", value = it.displayName)
                    }
                }
            }
        }

        // Generate Report Button
        item {
            Button(
                onClick = viewModel::generateWeeklyReport,
                enabled = !uiState.isGeneratingReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isGeneratingReport) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Generate Weekly AI Report")
            }
            uiState.reportError?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No hazards detected this week.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.weeklyHazards.take(10)) { hazard ->
                HazardCard(hazard = hazard, onResolve = { /* handle in incidents screen */ })
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

/**
 * Small card chip displaying a metric value and its label.
 * Used in [DashboardScreen] for Detected / Resolved / Unresolved / Top Risk counts.
 *
 * @param label Descriptive text shown below the value (e.g., "Detected").
 * @param value The metric value as a formatted string (e.g., "12").
 */
@Composable
private fun StatChip(label: String, value: String) {
    Card {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
