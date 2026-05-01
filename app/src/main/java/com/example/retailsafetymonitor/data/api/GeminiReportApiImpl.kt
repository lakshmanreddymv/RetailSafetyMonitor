package com.example.retailsafetymonitor.data.api

import com.example.retailsafetymonitor.BuildConfig
import javax.inject.Inject

/**
 * Retrofit-backed implementation of [GeminiReportApi].
 *
 * Builds a structured prompt instructing the model to act as a retail safety AI,
 * then calls [GeminiService.generateContent] using [BuildConfig.GEMINI_API_KEY].
 *
 * **Response extraction:** navigates `candidates[0].content.parts[0].text`. If any
 * step returns null (model declined, API error, empty response), returns a
 * "No analysis available" fallback instead of throwing.
 *
 * Bound to [GeminiReportApi] in [RepositoryModule] via `@Binds`.
 */
// S: Single Responsibility — translates domain summary text into a Gemini API call
// D: Dependency Inversion — implements [GeminiReportApi] interface; callers never import this class
class GeminiReportApiImpl @Inject constructor(
    private val geminiService: GeminiService
) : GeminiReportApi {

    /**
     * Builds a retail-safety prompt from [incidentSummary], calls Gemini 2.5 Flash,
     * and returns the first candidate's text.
     *
     * @param incidentSummary Plain-text incident breakdown produced by [GenerateSafetyReportUseCase].
     * @return AI-generated analysis text (≤300 words), or "No analysis available" on null response.
     * @throws Exception on network failure or HTTP error (caller wraps in try/catch).
     */
    override suspend fun generateWeeklySummary(incidentSummary: String): String {
        val prompt = buildString {
            appendLine("You are a retail safety AI assistant. Analyze the following weekly safety incident report and provide:")
            appendLine("1. A concise summary of the week's safety performance (2-3 sentences)")
            appendLine("2. The top 2-3 safety risks identified")
            appendLine("3. Specific actionable recommendations to prevent future incidents")
            appendLine("4. Any patterns you notice (e.g., time of day, location, hazard type trends)")
            appendLine()
            appendLine("Keep the response under 300 words and make it actionable for a store manager.")
            appendLine()
            append(incidentSummary)
        }
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )
        val response = geminiService.generateContent(
            apiKey = BuildConfig.GEMINI_API_KEY,
            request = request
        )
        return response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: "No analysis available. Please try again."
    }
}
