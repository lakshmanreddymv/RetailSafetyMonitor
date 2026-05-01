package com.example.retailsafetymonitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.usecase.GenerateSafetyReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Immutable snapshot of the Dashboard screen state.
 *
 * @property weeklyHazards All hazards detected in the current Monday–Sunday window.
 * @property complianceScore Severity-weighted score 0–100 for the current week.
 * @property totalDetected Count of all hazards detected this week.
 * @property totalResolved Count of hazards resolved this week.
 * @property topHazardType The most frequently detected [HazardType] this week, or null.
 * @property isGeneratingReport True while the Gemini API call is in-flight.
 * @property reportError Non-null if the last report generation attempt failed.
 */
data class DashboardUiState(
    val weeklyHazards: List<Hazard> = emptyList(),
    val complianceScore: Int = 100,
    val totalDetected: Int = 0,
    val totalResolved: Int = 0,
    val topHazardType: HazardType? = null,
    val isGeneratingReport: Boolean = false,
    val reportError: String? = null
)

/**
 * ViewModel for [DashboardScreen]. Drives the weekly compliance score, stat chips,
 * and on-demand report generation.
 *
 * The compliance score is scoped to the **current calendar week** (Monday 00:00 →
 * Sunday 23:59) and resets automatically each Monday because [getWeekStart] is
 * evaluated at ViewModel creation time; a new ViewModel instance is created when
 * the app is relaunched after Monday.
 *
 * Follows Unidirectional Data Flow (UDF):
 * - Events flow UP from UI via public functions
 * - State flows DOWN to UI via [uiState] StateFlow
 * - No direct state mutation from the UI layer
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val hazardRepository: HazardRepository,
    private val generateSafetyReportUseCase: GenerateSafetyReportUseCase
) : ViewModel() {

    private val _isGeneratingReport = MutableStateFlow(false)
    private val _reportError = MutableStateFlow<String?>(null)

    /**
     * Live [StateFlow] of [DashboardUiState] combining the current-week hazard stream
     * with report generation progress.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second stop-timeout so the upstream
     * Room query pauses when the screen is in the back stack but resumes immediately on return.
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        hazardRepository.getHazardsForWeek(getWeekStart(), getWeekEnd()),
        _isGeneratingReport,
        _reportError
    ) { weeklyHazards, isGenerating, reportError ->
        val score = generateSafetyReportUseCase.calculateComplianceScore(weeklyHazards)
        val topType = weeklyHazards
            .groupBy { it.type }
            .maxByOrNull { it.value.size }
            ?.key
        DashboardUiState(
            weeklyHazards = weeklyHazards,
            complianceScore = score,
            totalDetected = weeklyHazards.size,
            totalResolved = weeklyHazards.count { it.isResolved },
            topHazardType = topType,
            isGeneratingReport = isGenerating,
            reportError = reportError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /**
     * Triggers on-demand generation of the weekly AI report via [GenerateSafetyReportUseCase].
     * Sets [DashboardUiState.isGeneratingReport] true while the Gemini API call is in-flight.
     * Populates [DashboardUiState.reportError] on failure.
     */
    fun generateWeeklyReport() {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            _reportError.value = null
            try {
                generateSafetyReportUseCase.execute(getWeekStart(), getWeekEnd())
            } catch (e: Exception) {
                _reportError.value = "Report generation failed: ${e.message}"
            } finally {
                _isGeneratingReport.value = false
            }
        }
    }

    /** Returns epoch millis of Monday 00:00:00 for the current week in device timezone. */
    private fun getWeekStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Returns epoch millis of the following Monday 00:00:00 (exclusive week end). */
    private fun getWeekEnd(): Long = getWeekStart() + 7L * 24 * 60 * 60 * 1000
}
