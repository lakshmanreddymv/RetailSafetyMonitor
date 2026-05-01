package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.data.api.GeminiReportApi
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.SafetyReport
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.repository.ReportRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Generates a weekly [SafetyReport] by querying the hazard history for the
 * given week window, computing the severity-weighted compliance score, and
 * calling the Gemini 2.5 Flash API for an AI-generated summary.
 *
 * **Compliance score algorithm:**
 * ```
 * base = (resolved / total) * 100
 * criticalPenalty = min(criticalUnresolved * 10, 40)
 * highPenalty     = min(highUnresolved * 5, 20)
 * score = coerceIn(0, 100) { base - criticalPenalty - highPenalty }
 * ```
 * Zero hazards → 100. Score floors at 0.
 *
 * **Gemini failure handling:** If the API call throws (network down, quota exceeded),
 * a fallback summary is used and the report is still saved to Room.
 *
 * Called by:
 * - [WeeklyReportWorker] on a 7-day schedule (15 min in debug)
 * - [DashboardViewModel.generateWeeklyReport] on user request
 */
// S: Single Responsibility — generates one weekly report; no camera, UI, or notification logic
// D: Dependency Inversion — depends on [HazardRepository], [ReportRepository], [GeminiReportApi] interfaces
class GenerateSafetyReportUseCase @Inject constructor(
    private val hazardRepository: HazardRepository,
    private val reportRepository: ReportRepository,
    private val geminiApi: GeminiReportApi
) {
    /**
     * Generates, persists, and returns a [SafetyReport] for the given week window.
     *
     * Calls the Gemini API for the AI summary; if that call fails the report is still
     * saved with a fallback "network unavailable" message.
     *
     * @param weekStart Epoch millis of Monday 00:00:00 (inclusive lower bound).
     * @param weekEnd Epoch millis of the following Monday 00:00:00 (exclusive upper bound).
     * @return The completed [SafetyReport] after it has been written to Room.
     */
    suspend fun execute(weekStart: Long, weekEnd: Long): SafetyReport {
        val hazards = hazardRepository.getHazardsForWeek(weekStart, weekEnd).first()
        val totalHazards = hazards.size
        val resolvedHazards = hazards.count { it.isResolved }
        val complianceScore = calculateComplianceScore(hazards)
        val topHazardTypes = hazards
            .groupBy { it.type }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key }
        val incidentSummary = buildIncidentSummary(hazards, weekStart, weekEnd)
        val aiSummary = try {
            geminiApi.generateWeeklySummary(incidentSummary)
        } catch (e: Exception) {
            "Report generation failed. Network or API unavailable. Manual review required. Error: ${e.message}"
        }
        val report = SafetyReport(
            id = UUID.randomUUID().toString(),
            weekStartDate = weekStart,
            totalHazards = totalHazards,
            resolvedHazards = resolvedHazards,
            complianceScore = complianceScore,
            topHazardTypes = topHazardTypes,
            aiSummary = aiSummary,
            generatedAt = System.currentTimeMillis()
        )
        reportRepository.insertReport(report)
        return report
    }

    /**
     * Computes the severity-weighted compliance score for a list of hazards.
     *
     * ```
     * base             = (resolved / total) * 100
     * criticalPenalty  = min(criticalUnresolved * 10, 40)
     * highPenalty      = min(highUnresolved * 5, 20)
     * score            = coerceIn(0, 100) { base - criticalPenalty - highPenalty }
     * ```
     *
     * Exposed as non-private so [DashboardViewModel] can reuse it for the live score widget.
     *
     * @param hazards All hazards in the scoring window (resolved + unresolved).
     * @return Integer score in the range [0, 100]. Returns 100 for an empty list.
     */
    fun calculateComplianceScore(hazards: List<Hazard>): Int {
        if (hazards.isEmpty()) return 100
        val total = hazards.size
        val resolved = hazards.count { it.isResolved }
        val base = (resolved.toFloat() / total * 100).roundToInt()
        val criticalUnresolved = hazards.count { !it.isResolved && it.severity == Severity.CRITICAL }
        val highUnresolved = hazards.count { !it.isResolved && it.severity == Severity.HIGH }
        val criticalPenalty = (criticalUnresolved * 10).coerceAtMost(40)
        val highPenalty = (highUnresolved * 5).coerceAtMost(20)
        return (base - criticalPenalty - highPenalty).coerceIn(0, 100)
    }

    private fun buildIncidentSummary(hazards: List<Hazard>, weekStart: Long, weekEnd: Long): String =
        buildString {
            appendLine("Weekly Safety Incident Report")
            appendLine("Period: $weekStart - $weekEnd")
            appendLine("Total hazards detected: ${hazards.size}")
            appendLine("Resolved: ${hazards.count { it.isResolved }}")
            appendLine("Unresolved: ${hazards.count { !it.isResolved }}")
            appendLine("Compliance score: ${calculateComplianceScore(hazards)}%")
            appendLine()
            appendLine("Breakdown by type:")
            HazardType.entries.forEach { type ->
                val count = hazards.count { it.type == type }
                if (count > 0) appendLine("  ${type.displayName}: $count")
            }
            appendLine()
            appendLine("Breakdown by severity:")
            Severity.entries.forEach { sev ->
                val count = hazards.count { it.severity == sev }
                if (count > 0) appendLine("  ${sev.name}: $count")
            }
        }
}
