package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fetches the hazard history stream for the Incidents screen.
 *
 * Returns the 50 most recent hazards from [HazardRepository.getRecentHazards]; client-side
 * filtering by type, severity, and resolution state is applied downstream in
 * [IncidentsViewModel] via [kotlinx.coroutines.flow.combine], keeping the SQL query simple
 * and avoiding N+1 round-trips for interactive filter changes.
 */
// S: Single Responsibility — retrieves hazard history; no mutation, scoring, or notification logic
// D: Dependency Inversion — depends on [HazardRepository] interface, not [HazardRepositoryImpl]
class GetHazardHistoryUseCase @Inject constructor(
    private val hazardRepository: HazardRepository
) {
    /**
     * Returns a [Flow] of the 50 most recent hazards, ordered newest-first.
     * Collects database change notifications — emits automatically on every insert or update.
     *
     * @return Live [Flow] of up to 50 [Hazard] objects.
     */
    fun execute(): Flow<List<Hazard>> = hazardRepository.getRecentHazards()

    /**
     * Returns the same unfiltered [Flow] as [execute]; ViewModel applies the filters.
     *
     * Filtering is intentionally done in [IncidentsViewModel] via
     * [kotlinx.coroutines.flow.combine] so filter-chip taps do not trigger additional
     * database queries — they only re-evaluate the in-memory list.
     *
     * @param type Optional [HazardType] filter — applied by ViewModel, not this use case.
     * @param severity Optional [Severity] filter — applied by ViewModel, not this use case.
     * @param resolvedOnly If true, show only resolved; if false, only unresolved; null = all.
     * @return Live [Flow] of the full recent hazard list (filters evaluated downstream).
     */
    fun executeFiltered(
        type: HazardType? = null,
        severity: Severity? = null,
        resolvedOnly: Boolean? = null
    ): Flow<List<Hazard>> = hazardRepository.getRecentHazards()
    // Filtering applied downstream in ViewModel via map{}
}
