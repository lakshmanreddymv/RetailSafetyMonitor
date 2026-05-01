package com.example.retailsafetymonitor.domain.usecase

import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DetectHazardUseCase].
 *
 * Verifies the [HazardType] → [Severity] mapping defined by the
 * OSHA violation severity model:
 * - **CRITICAL**: WET_FLOOR, BLOCKED_EXIT, FIRE_HAZARD (up to $156,259/citation)
 * - **HIGH**: OVERCROWDING (up to $15,625/citation)
 * - **MEDIUM**: FALLEN_ITEM, TRIP_HAZARD, UNATTENDED_SPILL (up to $1,000/citation)
 * - **LOW**: UNKNOWN (advisory only)
 *
 * These tests have zero Android dependencies and run on the JVM.
 */
class DetectHazardUseCaseTest {

    private lateinit var useCase: DetectHazardUseCase

    @Before
    fun setUp() {
        useCase = DetectHazardUseCase()
    }

    // ─── CRITICAL severity ───────────────────────────────────────────────────

    @Test
    fun `WET_FLOOR maps to CRITICAL severity`() {
        assertEquals(Severity.CRITICAL, useCase.execute(HazardType.WET_FLOOR))
    }

    @Test
    fun `BLOCKED_EXIT maps to CRITICAL severity`() {
        assertEquals(Severity.CRITICAL, useCase.execute(HazardType.BLOCKED_EXIT))
    }

    @Test
    fun `FIRE_HAZARD maps to CRITICAL severity`() {
        assertEquals(Severity.CRITICAL, useCase.execute(HazardType.FIRE_HAZARD))
    }

    // ─── HIGH severity ────────────────────────────────────────────────────────

    @Test
    fun `OVERCROWDING maps to HIGH severity`() {
        assertEquals(Severity.HIGH, useCase.execute(HazardType.OVERCROWDING))
    }

    // ─── MEDIUM severity ─────────────────────────────────────────────────────

    @Test
    fun `FALLEN_ITEM maps to MEDIUM severity`() {
        assertEquals(Severity.MEDIUM, useCase.execute(HazardType.FALLEN_ITEM))
    }

    @Test
    fun `TRIP_HAZARD maps to MEDIUM severity`() {
        assertEquals(Severity.MEDIUM, useCase.execute(HazardType.TRIP_HAZARD))
    }

    @Test
    fun `UNATTENDED_SPILL maps to MEDIUM severity`() {
        assertEquals(Severity.MEDIUM, useCase.execute(HazardType.UNATTENDED_SPILL))
    }

    // ─── LOW severity ─────────────────────────────────────────────────────────

    @Test
    fun `UNKNOWN maps to LOW severity`() {
        assertEquals(Severity.LOW, useCase.execute(HazardType.UNKNOWN))
    }

    // ─── Exhaustiveness ──────────────────────────────────────────────────────

    @Test
    fun `Every HazardType maps to a non-null severity`() {
        HazardType.entries.forEach { type ->
            val severity = useCase.execute(type)
            // Compile-time exhaustive when(), so this will never fail;
            // test exists to catch accidental null returns if refactored
            @Suppress("USELESS_IS_CHECK")
            assertTrue(severity is Severity)
        }
    }

    private fun assertTrue(value: Boolean) = assertEquals(true, value)
}
