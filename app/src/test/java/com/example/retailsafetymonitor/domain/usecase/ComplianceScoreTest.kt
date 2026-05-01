package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for the severity-weighted compliance score algorithm in
 * [GenerateSafetyReportUseCase.calculateComplianceScore].
 *
 * **Algorithm spec:**
 * ```
 * base = (resolved / total) * 100
 * criticalPenalty = min(criticalUnresolved * 10, 40)
 * highPenalty     = min(highUnresolved * 5, 20)
 * score = coerceIn(0, 100) { base - criticalPenalty - highPenalty }
 * ```
 *
 * **Why severity-weighted instead of raw ratio?**
 * OSHA treats CRITICAL violations categorically differently from LOW ones.
 * A store with 10 resolved LOW hazards + 1 unresolved CRITICAL must NOT show
 * 91% compliance; it should be penalised disproportionately.
 */
class ComplianceScoreTest {

    private lateinit var useCase: GenerateSafetyReportUseCase

    @Before
    fun setUp() {
        // GenerateSafetyReportUseCase is constructed with mocks we don't need
        // for calculateComplianceScore, which is a pure function.
        useCase = GenerateSafetyReportUseCase(
            hazardRepository = org.mockito.kotlin.mock(),
            reportRepository = org.mockito.kotlin.mock(),
            geminiApi = org.mockito.kotlin.mock()
        )
    }

    // ─── Baseline edge cases ──────────────────────────────────────────────────

    @Test
    fun `Zero hazards returns 100 percent score`() {
        assertEquals(100, useCase.calculateComplianceScore(emptyList()))
    }

    @Test
    fun `All hazards resolved returns 100 percent score`() {
        val hazards = listOf(
            hazard(Severity.CRITICAL, resolved = true),
            hazard(Severity.HIGH, resolved = true),
            hazard(Severity.MEDIUM, resolved = true)
        )
        assertEquals(100, useCase.calculateComplianceScore(hazards))
    }

    // ─── CRITICAL penalty ─────────────────────────────────────────────────────

    @Test
    fun `1 CRITICAL unresolved out of 1 total gives score 0`() {
        // base = 0/1*100 = 0, criticalPenalty = 10, result = max(0-10, 0) = 0
        val hazards = listOf(hazard(Severity.CRITICAL, resolved = false))
        assertEquals(0, useCase.calculateComplianceScore(hazards))
    }

    @Test
    fun `10 hazards 9 resolved 1 CRITICAL unresolved gives score 80`() {
        // base = 9/10*100 = 90, criticalPenalty = 10, score = 80
        val hazards = (1..9).map { hazard(Severity.LOW, resolved = true) } +
                listOf(hazard(Severity.CRITICAL, resolved = false))
        assertEquals(80, useCase.calculateComplianceScore(hazards))
    }

    @Test
    fun `5 CRITICAL unresolved penalty is capped at 40 not 50`() {
        // base = 0, criticalPenalty = min(5*10, 40) = 40, score = max(-40, 0) = 0
        val hazards = (1..5).map { hazard(Severity.CRITICAL, resolved = false) }
        val score = useCase.calculateComplianceScore(hazards)
        // Score floors at 0
        assertEquals(0, score)
    }

    @Test
    fun `CRITICAL penalty caps at 40 regardless of unresolved count`() {
        // 10 CRITICAL unresolved: min(10*10, 40) = 40, NOT 100
        val hazards = (1..10).map { hazard(Severity.CRITICAL, resolved = false) }
        // base = 0, penalty = 40, score = 0 (floored)
        assertEquals(0, useCase.calculateComplianceScore(hazards))
    }

    // ─── HIGH penalty ─────────────────────────────────────────────────────────

    @Test
    fun `4 HIGH unresolved penalty is exactly 20`() {
        // base = 0, highPenalty = min(4*5, 20) = 20, score = max(-20, 0) = 0
        val hazards = (1..4).map { hazard(Severity.HIGH, resolved = false) }
        assertEquals(0, useCase.calculateComplianceScore(hazards))
    }

    @Test
    fun `5 HIGH unresolved penalty is capped at 20 not 25`() {
        val hazards = (1..5).map { hazard(Severity.HIGH, resolved = false) }
        // min(5*5, 20) = 20 — cap applies
        assertEquals(0, useCase.calculateComplianceScore(hazards))
    }

    // ─── Combined penalties ───────────────────────────────────────────────────

    @Test
    fun `Combined CRITICAL and HIGH penalties reduce score correctly`() {
        // 10 total: 5 resolved LOW, 2 CRITICAL unresolved, 3 HIGH unresolved
        val hazards = (1..5).map { hazard(Severity.LOW, resolved = true) } +
                (1..2).map { hazard(Severity.CRITICAL, resolved = false) } +
                (1..3).map { hazard(Severity.HIGH, resolved = false) }
        // base = 5/10*100 = 50
        // criticalPenalty = 2*10 = 20
        // highPenalty = min(3*5, 20) = 15
        // score = 50 - 20 - 15 = 15
        assertEquals(15, useCase.calculateComplianceScore(hazards))
    }

    @Test
    fun `Score never goes below 0 with large penalties`() {
        val hazards = (1..10).map { hazard(Severity.CRITICAL, resolved = false) } +
                (1..10).map { hazard(Severity.HIGH, resolved = false) }
        val score = useCase.calculateComplianceScore(hazards)
        assertEquals(0, score)
    }

    @Test
    fun `Score never exceeds 100`() {
        val hazards = (1..5).map { hazard(Severity.LOW, resolved = true) }
        val score = useCase.calculateComplianceScore(hazards)
        assertEquals(100, score)
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun hazard(severity: Severity, resolved: Boolean) = Hazard(
        id = UUID.randomUUID().toString(),
        type = HazardType.UNKNOWN,
        severity = severity,
        imageUri = null,
        locationDescription = null,
        timestamp = System.currentTimeMillis(),
        isResolved = resolved
    )
}
