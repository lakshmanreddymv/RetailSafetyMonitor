package com.example.retailsafetymonitor.domain.repository

import com.example.retailsafetymonitor.domain.model.Hazard
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for hazard persistence operations.
 *
 * Reactive queries return [Flow] backed by Room's SQLite change notifications;
 * the UI layer collects these as [androidx.lifecycle.compose.collectAsStateWithLifecycle]
 * or [kotlinx.coroutines.flow.StateFlow] via ViewModel.
 *
 * Snapshot queries ([getHazardsAfter], [getAllUnresolvedSnapshot]) are suspend functions
 * used by WorkManager background jobs that run outside a Flow collection scope.
 *
 * Implemented by [HazardRepositoryImpl]; bound via Hilt in [RepositoryModule].
 */
// S: Single Responsibility — declares hazard CRUD and query operations; no UI or ML logic
// D: Dependency Inversion — ViewModels and use cases depend on this interface, not on Room DAOs
interface HazardRepository {

    /**
     * Returns a live [Flow] of all hazards with [Hazard.isResolved] == false,
     * ordered newest-first. Emits on every Room write to the hazards table.
     */
    fun getUnresolvedHazards(): Flow<List<Hazard>>

    /**
     * Returns a live [Flow] of the 50 most recently detected hazards (resolved or not).
     * Used by [IncidentsViewModel] for the incident history list.
     */
    fun getRecentHazards(): Flow<List<Hazard>>

    /**
     * Returns a live [Flow] of hazards detected between [weekStart] (inclusive) and
     * [weekEnd] (exclusive). Used by [DashboardViewModel] for the current-week compliance score.
     *
     * @param weekStart Epoch millis of Monday 00:00:00.
     * @param weekEnd Epoch millis of the following Monday 00:00:00.
     */
    fun getHazardsForWeek(weekStart: Long, weekEnd: Long): Flow<List<Hazard>>

    /**
     * One-shot snapshot of hazards detected after [since] epoch millis.
     * Called by [GenerateSafetyReportUseCase] inside a coroutine (not a Flow collection).
     *
     * @param since Epoch millis lower bound (exclusive).
     * @return List of matching [Hazard] objects, may be empty.
     */
    suspend fun getHazardsAfter(since: Long): List<Hazard>

    /**
     * Persists a newly detected [hazard] to Room. Called by [LogHazardUseCase] on every
     * detection that passes the 60-second cooldown check in [HazardDetector].
     *
     * @param hazard The domain hazard to insert; uses [Hazard.id] as the primary key.
     */
    suspend fun insertHazard(hazard: Hazard)

    /**
     * Marks the hazard with [id] as resolved and records the resolution metadata.
     *
     * @param id UUID of the hazard to resolve.
     * @param resolvedAt Epoch millis of resolution (caller provides for testability).
     * @param resolvedBy Display name of the resolving manager, or null.
     */
    suspend fun markResolved(id: String, resolvedAt: Long, resolvedBy: String?)

    /**
     * Updates the [Hazard.lastEscalatedAt] timestamp after [HazardEscalationWorker]
     * fires a notification. Used for idempotency: prevents duplicate escalations within
     * [Severity.escalationIntervalMs].
     *
     * @param id UUID of the hazard that was just escalated.
     * @param lastEscalatedAt Epoch millis when the escalation notification was sent.
     */
    suspend fun updateLastEscalatedAt(id: String, lastEscalatedAt: Long)

    /**
     * One-shot snapshot of all currently unresolved hazards. Called by
     * [HazardEscalationWorker] which needs a point-in-time list, not a continuous flow.
     *
     * @return List of unresolved [Hazard] objects ordered newest-first, may be empty.
     */
    suspend fun getAllUnresolvedSnapshot(): List<Hazard>
}
