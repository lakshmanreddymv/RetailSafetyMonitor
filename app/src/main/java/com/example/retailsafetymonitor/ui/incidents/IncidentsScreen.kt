package com.example.retailsafetymonitor.ui.incidents

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.ui.components.HazardCard

/**
 * Scrollable incident history screen with interactive filter chips.
 *
 * Displays the 50 most recent hazards from Room, filterable by [Severity] chip row
 * and Open/Resolved toggle chips. Filter state is held in [IncidentsViewModel] and
 * applied in-memory — chip taps do not trigger additional DB queries.
 *
 * Each [HazardCard] shows a "Mark Resolved" button for open hazards, which calls
 * [IncidentsViewModel.resolveHazard] and triggers an automatic list refresh via Room Flow.
 *
 * @param viewModel Hilt-injected [IncidentsViewModel].
 */
@Composable
fun IncidentsScreen(viewModel: IncidentsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Incident History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${uiState.hazards.size} incidents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Severity filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = uiState.filterSeverity == null,
                onClick = { viewModel.setSeverityFilter(null) },
                label = { Text("All") }
            )
            Severity.entries.forEach { sev ->
                FilterChip(
                    selected = uiState.filterSeverity == sev,
                    onClick = { viewModel.setSeverityFilter(if (uiState.filterSeverity == sev) null else sev) },
                    label = { Text(sev.name) }
                )
            }
        }

        // Resolved/Unresolved filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.showResolvedOnly == false,
                onClick = { viewModel.setResolvedFilter(if (uiState.showResolvedOnly == false) null else false) },
                label = { Text("Open") }
            )
            FilterChip(
                selected = uiState.showResolvedOnly == true,
                onClick = { viewModel.setResolvedFilter(if (uiState.showResolvedOnly == true) null else true) },
                label = { Text("Resolved") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.hazards.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("No incidents match the current filter.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.hazards, key = { it.id }) { hazard ->
                    HazardCard(
                        hazard = hazard,
                        onResolve = { viewModel.resolveHazard(it) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
