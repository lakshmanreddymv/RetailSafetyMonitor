package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.domain.repository.HazardRepository
import javax.inject.Inject

/**
 * Marks an open hazard as resolved and records the resolution timestamp.
 *
 * Called by [IncidentsViewModel.resolveHazard] when the manager taps "Mark Resolved"
 * on a [HazardCard]. The resolved hazard remains in the database for compliance
 * scoring and audit history — it is never deleted.
 *
 * Resolution timestamp is stamped here (not passed in by the UI) to ensure the
 * authoritative time comes from the use-case layer, not from view-layer clock reads.
 */
// S: Single Responsibility — resolves exactly one hazard; no detection, scoring, or UI logic
// D: Dependency Inversion — depends on [HazardRepository] interface, not [HazardRepositoryImpl]
class ResolveHazardUseCase @Inject constructor(
    private val hazardRepository: HazardRepository
) {
    /**
     * Marks the hazard with [id] as resolved.
     *
     * @param id UUID of the hazard to resolve.
     * @param resolvedBy Display name of the resolving manager, or null if unattributed.
     */
    suspend fun execute(id: String, resolvedBy: String? = null) {
        hazardRepository.markResolved(
            id = id,
            resolvedAt = System.currentTimeMillis(),
            resolvedBy = resolvedBy
        )
    }
}
