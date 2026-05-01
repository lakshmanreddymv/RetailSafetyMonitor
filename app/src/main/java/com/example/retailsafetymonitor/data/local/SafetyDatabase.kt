package com.example.retailsafetymonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the Retail Safety Monitor app.
 *
 * Contains two tables:
 * - `hazards` ([HazardEntity]) — every detected safety event, retained permanently for audit.
 * - `reports` ([ReportEntity]) — weekly AI-generated safety reports.
 *
 * Schema version is 1 (initial release). Migrations are not required until the schema changes.
 * Schema export is disabled because this is a single-device app with no schema migration CI.
 *
 * Built and injected as a singleton by [AppModule.provideSafetyDatabase].
 */
@Database(
    entities = [HazardEntity::class, ReportEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SafetyDatabase : RoomDatabase() {

    /** Returns the [HazardDao] for hazard CRUD and query operations. */
    abstract fun hazardDao(): HazardDao

    /** Returns the [ReportDao] for report insert and query operations. */
    abstract fun reportDao(): ReportDao

    companion object {
        /** File name for the Room database on device storage. */
        const val DATABASE_NAME = "safety_monitor.db"
    }
}
