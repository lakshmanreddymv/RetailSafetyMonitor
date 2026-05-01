package com.example.retailsafetymonitor.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.usecase.GetHazardHistoryUseCase
import com.example.retailsafetymonitor.domain.usecase.ResolveHazardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable snapshot of the Incidents screen state.
 *
 * @property hazards Filtered list of hazards matching the active filter criteria.
 * @property filterType Active [HazardType] filter chip selection, or null for all types.
 * @property filterSeverity Active [Severity] filter chip selection, or null for all severities.
 * @property showResolvedOnly true = show only resolved, false = only open, null = show all.
 */
data class IncidentsUiState(
    val hazards: List<Hazard> = emptyList(),
    val filterType: HazardType? = null,
    val filterSeverity: Severity? = null,
    val showResolvedOnly: Boolean? = null
)

/**
 * ViewModel for [IncidentsScreen]. Provides the hazard history list with interactive
 * filter-chip support for type, severity, and resolution state.
 *
 * Filtering is applied in-memory via [combine] over the raw Room stream and three
 * [MutableStateFlow] filter signals. This avoids re-querying the database on every
 * chip tap and keeps the SQL query simple (no dynamic WHERE clauses).
 *
 * Follows Unidirectional Data Flow (UDF):
 * - Events flow UP from UI via public functions
 * - State flows DOWN to UI via [uiState] StateFlow
 * - No direct state mutation from the UI layer
 */
@HiltViewModel
class IncidentsViewModel @Inject constructor(
    private val getHazardHistoryUseCase: GetHazardHistoryUseCase,
    private val resolveHazardUseCase: ResolveHazardUseCase
) : ViewModel() {

    private val _filterType = MutableStateFlow<HazardType?>(null)
    private val _filterSeverity = MutableStateFlow<Severity?>(null)
    private val _showResolvedOnly = MutableStateFlow<Boolean?>(null)

    /**
     * Live [StateFlow] of [IncidentsUiState] combining the Room hazard stream with
     * the three active filter signals.
     */
    val uiState: StateFlow<IncidentsUiState> = combine(
        getHazardHistoryUseCase.execute(),
        _filterType,
        _filterSeverity,
        _showResolvedOnly
    ) { allHazards, type, severity, resolvedOnly ->
        val filtered = allHazards.filter { hazard ->
            (type == null || hazard.type == type) &&
            (severity == null || hazard.severity == severity) &&
            (resolvedOnly == null || hazard.isResolved == resolvedOnly)
        }
        IncidentsUiState(
            hazards = filtered,
            filterType = type,
            filterSeverity = severity,
            showResolvedOnly = resolvedOnly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentsUiState())

    /**
     * Sets or clears the [HazardType] filter chip.
     * @param type The [HazardType] to filter by, or null to show all types.
     */
    fun setTypeFilter(type: HazardType?) { _filterType.value = type }

    /**
     * Sets or clears the [Severity] filter chip.
     * @param severity The [Severity] to filter by, or null to show all severities.
     */
    fun setSeverityFilter(severity: Severity?) { _filterSeverity.value = severity }

    /**
     * Sets or clears the resolved/open filter.
     * @param resolvedOnly true = resolved only, false = open only, null = all.
     */
    fun setResolvedFilter(resolvedOnly: Boolean?) { _showResolvedOnly.value = resolvedOnly }

    /**
     * Marks the hazard with [id] as resolved via [ResolveHazardUseCase].
     * Fire-and-forget; Room update triggers automatic [uiState] recomposition.
     *
     * @param id UUID of the hazard to resolve.
     */
    fun resolveHazard(id: String) {
        viewModelScope.launch { resolveHazardUseCase.execute(id) }
    }
}
