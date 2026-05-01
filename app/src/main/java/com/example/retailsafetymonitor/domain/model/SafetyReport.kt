package com.example.retailsafetymonitor.domain.model

/**
 * AI-generated weekly safety performance summary persisted to Room via [ReportEntity].
 *
 * Produced by [GenerateSafetyReportUseCase], which:
 * 1. Queries [HazardRepository.getHazardsForWeek] for all hazards in the reporting window.
 * 2. Computes [complianceScore] using the severity-weighted algorithm.
 * 3. Calls the Gemini 2.5 Flash API to generate [aiSummary]; falls back to a static
 *    error message if the network or quota is unavailable.
 *
 * @property id UUID generated at report creation time.
 * @property weekStartDate Epoch millis of Monday 00:00:00 for the reported week.
 * @property totalHazards Total hazards detected in the week window (resolved + unresolved).
 * @property resolvedHazards Count of hazards marked resolved within the same window.
 * @property complianceScore Severity-weighted score 0–100. 100 = zero hazards detected.
 *   See [GenerateSafetyReportUseCase.calculateComplianceScore] for the formula.
 * @property topHazardTypes Up to 3 [HazardType] values ranked by detection frequency.
 * @property aiSummary Gemini 2.5 Flash analysis: summary, risks, and actionable recommendations.
 * @property generatedAt Epoch millis when this report was created and saved to Room.
 */
// S: Single Responsibility — holds the complete output of one weekly safety analysis cycle
// D: Dependency Inversion — no framework dependencies; depends only on domain enum [HazardType]
data class SafetyReport(
    val id: String,
    val weekStartDate: Long,
    val totalHazards: Int,
    val resolvedHazards: Int,
    val complianceScore: Int,
    val topHazardTypes: List<HazardType>,
    val aiSummary: String,
    val generatedAt: Long
)
