package com.example.retailsafetymonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.retailsafetymonitor.data.worker.HazardEscalationWorker
import com.example.retailsafetymonitor.data.worker.WeeklyReportWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application subclass that bootstraps Hilt and WorkManager for the Retail Safety Monitor.
 *
 * **WorkManager custom initialization:** The manifest disables WorkManager's default
 * [androidx.startup.Initializer] via `tools:node="remove"` so that [HiltWorkerFactory]
 * can be injected here before WorkManager is initialized. Without this, WorkManager
 * would initialize with the default factory that cannot inject Hilt dependencies into
 * [HazardEscalationWorker] or [WeeklyReportWorker].
 *
 * **Background workers scheduled at startup:**
 * - [HazardEscalationWorker]: every 15 minutes (platform minimum), battery-not-low constraint.
 * - [WeeklyReportWorker]: every 7 days in release (15 minutes in debug), network required.
 *
 * Both workers use [ExistingPeriodicWorkPolicy.KEEP] — re-scheduling on re-launch has no
 * effect if a pending work request with the same unique name already exists.
 */
@HiltAndroidApp
class RetailSafetyMonitorApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Supplies WorkManager with the [HiltWorkerFactory] so that [@HiltWorker][dagger.hilt.android.AndroidEntryPoint]
     * workers can receive their injected dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundWorkers()
    }

    /**
     * Enqueues the two periodic WorkManager jobs on every app start.
     *
     * [ExistingPeriodicWorkPolicy.KEEP] ensures that if a job is already scheduled
     * (e.g., from the previous launch), the existing schedule is preserved and the
     * interval is not reset.
     */
    private fun scheduleBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)

        // HazardEscalationWorker: every 15 min (platform minimum), no network required
        val escalationConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val escalationRequest = PeriodicWorkRequestBuilder<HazardEscalationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(escalationConstraints).build()

        workManager.enqueueUniquePeriodicWork(
            "hazard_escalation",
            ExistingPeriodicWorkPolicy.KEEP,
            escalationRequest
        )

        // WeeklyReportWorker: 15 min in debug for testing, 7 days in release
        val reportIntervalDays = if (BuildConfig.IS_DEBUG) 15L to TimeUnit.MINUTES
            else 7L to TimeUnit.DAYS
        val reportConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val reportRequest = PeriodicWorkRequestBuilder<WeeklyReportWorker>(
            reportIntervalDays.first, reportIntervalDays.second
        ).setConstraints(reportConstraints).build()

        workManager.enqueueUniquePeriodicWork(
            "weekly_report",
            ExistingPeriodicWorkPolicy.KEEP,
            reportRequest
        )
    }
}
