package com.example.retailsafetymonitor.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.retailsafetymonitor.BuildConfig
import com.example.retailsafetymonitor.domain.usecase.GenerateSafetyReportUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * Periodic WorkManager job that generates an AI-powered weekly safety report.
 *
 * **Schedule:**
 * - Release: every 7 days with [NetworkType.CONNECTED] constraint.
 * - Debug: every 15 minutes (platform minimum) for demo and testing purposes.
 *   The "week" window is also shortened to the last 15 minutes in debug
 *   so the report is always populated with recent data.
 *
 * **Failure handling:**
 * - If [GenerateSafetyReportUseCase.execute] throws (DB error, Gemini API down),
 *   [Result.retry] triggers WorkManager's exponential backoff.
 * - Network failures are handled inside [GenerateSafetyReportUseCase] with a
 *   fallback summary — the worker only retries on unexpected exceptions.
 *
 * Uses [@HiltWorker][dagger.hilt.android.AndroidEntryPoint] + [@AssistedInject] for
 * Hilt injection. Requires [HiltWorkerFactory] registered in [RetailSafetyMonitorApp].
 */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val generateSafetyReportUseCase: GenerateSafetyReportUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val weekStart = getWeekStart()
            val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000
            generateSafetyReportUseCase.execute(weekStart, weekEnd)
            Result.success()
        } catch (e: Exception) {
            // Network failure or Gemini API unavailable — retry with backoff
            Result.retry()
        }
    }

    /**
     * Returns the epoch millis for the start of the current reporting window.
     *
     * In debug builds this is the last 15 minutes (fast demo cycle). In release
     * builds this is Monday 00:00:00 of the current week in the device's timezone.
     *
     * @return Epoch millis of the window start (inclusive lower bound).
     */
    private fun getWeekStart(): Long {
        // In debug builds with fast interval, use last 15 minutes as "week"
        if (BuildConfig.IS_DEBUG) {
            return System.currentTimeMillis() - 15 * 60 * 1000L
        }
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
