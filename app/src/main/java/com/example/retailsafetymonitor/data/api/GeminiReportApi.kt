package com.example.retailsafetymonitor.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Domain-layer abstraction for generating AI-powered weekly safety summaries.
 *
 * Implemented by [GeminiReportApiImpl] using Retrofit + Gemini 2.5 Flash.
 * Callers ([GenerateSafetyReportUseCase]) depend on this interface, not on the Retrofit impl,
 * so the underlying model or transport can be swapped without changing use-case logic.
 */
// S: Single Responsibility — declares the single AI summary operation; no HTTP details
// D: Dependency Inversion — use cases depend on this interface, not on [GeminiReportApiImpl]
interface GeminiReportApi {
    /**
     * Generates a plain-text weekly safety analysis from the provided [incidentSummary].
     *
     * @param incidentSummary Structured incident data built by [GenerateSafetyReportUseCase].
     * @return Gemini 2.5 Flash response text (summary, risks, recommendations), or a
     *   fallback message if the API call fails.
     * @throws Exception on network error or API quota exhaustion (caller must handle).
     */
    suspend fun generateWeeklySummary(incidentSummary: String): String
}

/**
 * Retrofit service interface for the Gemini `generateContent` REST endpoint.
 *
 * Points at `https://generativelanguage.googleapis.com/v1beta/`.
 * Instantiated by [AppModule.provideGeminiService] and consumed only by [GeminiReportApiImpl].
 */
interface GeminiService {
    /**
     * Calls the Gemini `generateContent` endpoint.
     *
     * @param apiKey Gemini API key from [com.example.retailsafetymonitor.BuildConfig.GEMINI_API_KEY].
     * @param request Request body wrapping the user prompt and generation config.
     * @return [GeminiResponse] containing one or more candidate completions.
     */
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

/**
 * Top-level request body for the Gemini `generateContent` API.
 *
 * @property contents List of conversation turns; use a single user-role entry for one-shot prompts.
 * @property generationConfig Model sampling parameters.
 */
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GenerationConfig = GenerationConfig()
)

/**
 * A single conversation turn in a [GeminiRequest].
 *
 * @property parts Text parts that compose this turn.
 * @property role Conversation role; "user" for prompts, "model" for responses.
 */
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

/**
 * A single text segment within a [GeminiContent] turn.
 *
 * @property text The text content of this part.
 */
data class GeminiPart(val text: String)

/**
 * Sampling parameters sent with every [GeminiRequest].
 *
 * @property temperature Controls creativity vs. determinism. 0.3 yields focused, factual output
 *   appropriate for safety reports; higher values produce more varied text.
 * @property maxOutputTokens Upper bound on response length. 1024 tokens ≈ 750 words.
 */
data class GenerationConfig(
    val temperature: Float = 0.3f,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 1024
)

/**
 * Top-level response from the Gemini `generateContent` endpoint.
 *
 * @property candidates List of generated completions; typically one for safety report prompts.
 */
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

/**
 * A single candidate completion returned by Gemini.
 *
 * @property content The generated [GeminiContent] turn, or null if the model declined to respond.
 */
data class GeminiCandidate(
    val content: GeminiContent?
)
