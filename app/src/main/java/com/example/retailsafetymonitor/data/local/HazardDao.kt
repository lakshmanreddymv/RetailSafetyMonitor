package com.example.retailsafetymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [HazardEntity] operations.
 *
 * Reactive queries ([getUnresolvedHazards], [getRecentHazards], [getHazardsForWeek])
 * return [kotlinx.coroutines.flow.Flow] and emit on every database change.
 * Snapshot queries ([getUnresolvedHazardsSnapshot], [getHazardsAfter]) are suspend
 * functions used by [HazardEscalationWorker] which runs in a non-Flow coroutine context.
 */
@Dao
interface HazardDao {

    /** Inserts or replaces a hazard row. REPLACE strategy handles re-detection of the same ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHazard(hazard: HazardEntity)

    /** Live [Flow] of all unresolved hazards, newest-first. Emits on every table change. */
    @Query("SELECT * FROM hazards WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedHazards(): Flow<List<HazardEntity>>

    /** One-shot snapshot of unresolved hazards used by [HazardEscalationWorker]. */
    @Query("SELECT * FROM hazards WHERE isResolved = 0 ORDER BY timestamp DESC")
    suspend fun getUnresolvedHazardsSnapshot(): List<HazardEntity>

    /**
     * One-shot snapshot of hazards detected after [since] epoch millis.
     * @param since Epoch millis lower bound (exclusive).
     */
    @Query("SELECT * FROM hazards WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getHazardsAfter(since: Long): List<HazardEntity>

    /**
     * Live [Flow] of hazards in the given week window. Used by [DashboardViewModel].
     * @param weekStart Monday 00:00:00 epoch millis (inclusive).
     * @param weekEnd Following Monday 00:00:00 epoch millis (exclusive).
     */
    @Query("SELECT * FROM hazards WHERE timestamp >= :weekStart AND timestamp < :weekEnd ORDER BY timestamp DESC")
    fun getHazardsForWeek(weekStart: Long, weekEnd: Long): Flow<List<HazardEntity>>

    /** Live [Flow] of the 50 most recent hazards regardless of resolution state. */
    @Query("SELECT * FROM hazards ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHazards(): Flow<List<HazardEntity>>

    /**
     * Marks a hazard as resolved in-place, preserving the full row for audit history.
     * @param id UUID of the hazard to resolve.
     * @param resolvedAt Epoch millis of resolution.
     * @param resolvedBy Display name of the resolving manager, or null.
     */
    @Query("UPDATE hazards SET isResolved = 1, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy WHERE id = :id")
    suspend fun markResolved(id: String, resolvedAt: Long, resolvedBy: String?)

    /**
     * Updates the escalation timestamp used by [HazardEscalationWorker] for idempotency.
     * @param id UUID of the hazard that was just escalated.
     * @param lastEscalatedAt Epoch millis of the escalation notification.
     */
    @Query("UPDATE hazards SET lastEscalatedAt = :lastEscalatedAt WHERE id = :id")
    suspend fun updateLastEscalatedAt(id: String, lastEscalatedAt: Long)
}
