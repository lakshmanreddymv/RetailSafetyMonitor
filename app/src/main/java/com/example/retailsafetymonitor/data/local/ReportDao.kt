package com.example.retailsafetymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [ReportEntity] operations.
 *
 * Reports are write-once: generated weekly by [WeeklyReportWorker] or on demand by
 * [DashboardViewModel]. The REPLACE conflict strategy means re-generating a report
 * for the same [ReportEntity.id] overwrites the previous row rather than failing.
 *
 * Both queries return [Flow] so [ReportViewModel] re-renders automatically when
 * a new report is inserted.
 */
@Dao
interface ReportDao {

    /** Inserts or replaces a report row. REPLACE handles re-generation of the same weekly period. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    /** Live [Flow] of the single most recent report by [ReportEntity.generatedAt], or null. */
    @Query("SELECT * FROM reports ORDER BY generatedAt DESC LIMIT 1")
    fun getLatestReport(): Flow<ReportEntity?>

    /** Live [Flow] of all reports ordered newest-first. Used by [ReportViewModel]. */
    @Query("SELECT * FROM reports ORDER BY generatedAt DESC")
    fun getAllReports(): Flow<List<ReportEntity>>
}
