package com.example.retailsafetymonitor.ui.monitor

import android.graphics.RectF
import com.example.retailsafetymonitor.data.camera.CameraManager
import com.example.retailsafetymonitor.data.ml.HazardDetector
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.usecase.DetectHazardUseCase
import com.example.retailsafetymonitor.domain.usecase.LogHazardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.UUID

/**
 * Unit tests for the escalation logic inside [MonitorViewModel.showHazardDetected].
 *
 * **Testing strategy:** [showHazardDetected] is private and sits deep in the camera pipeline.
 * Reaching it through the full `CameraManager → ML Kit → LogHazardUseCase` chain requires
 * mocking concrete Kotlin final classes — brittle and noisy. Instead, this class drives the
 * private method directly via reflection, and reads [MonitorViewModel.uiState] to assert
 * the resulting state. This isolates the escalation rule from unrelated infrastructure.
 *
 * **Escalation rule under test:**
 * ```
 * val shouldUpgrade = current is HazardDetected &&
 *         newHazard.severity.ordinal < current.hazard.severity.ordinal
 * // Upgrade when: new ordinal is SMALLER (= higher priority)
 * // CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3
 * ```
 *
 * **Dismiss-timer rule under test:**
 * - On upgrade: existing [viewModelScope] delay is cancelled, a new 5s timer starts.
 * - On no-op (lower severity arrives, no upgrade): existing timer is NOT reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HazardUiStateEscalationTest {

    // UnconfinedTestDispatcher: coroutines run eagerly on the calling thread;
    // virtual time still works so advanceTimeBy() drives the 5-second dismiss delay.
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var viewModel: MonitorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MonitorViewModel(
            cameraManager     = mock(),         // never called in these tests
            hazardDetector    = mock(),
            detectHazardUseCase = DetectHazardUseCase(),
            logHazardUseCase  = mock()          // never called in these tests
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        viewModel.cameraExecutor.shutdown()
    }

    // ─── Baseline ─────────────────────────────────────────────────────────────

    @Test
    fun `first call to showHazardDetected from Monitoring transitions to HazardDetected`() =
        testScope.runTest {
            val hazard = fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM)

            showHazardDetected(hazard)

            val state = viewModel.uiState.value
            assertTrue(state is MonitorUiState.HazardDetected)
            assertEquals(Severity.MEDIUM, (state as MonitorUiState.HazardDetected).hazard.severity)
        }

    // ─── Upgrade: lower-severity showing, higher-severity arrives ─────────────

    @Test
    fun `CRITICAL arriving while MEDIUM is shown upgrades to CRITICAL`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))   // ordinal=2
        assertShownSeverity(Severity.MEDIUM)

        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))   // ordinal=0 < 2
        assertShownSeverity(Severity.CRITICAL)
    }

    @Test
    fun `CRITICAL arriving while HIGH is shown upgrades to CRITICAL`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.OVERCROWDING, Severity.HIGH))    // ordinal=1
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))   // ordinal=0 < 1
        assertShownSeverity(Severity.CRITICAL)
    }

    @Test
    fun `HIGH arriving while LOW is shown upgrades to HIGH`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.UNKNOWN, Severity.LOW))           // ordinal=3
        showHazardDetected(fakeHazard(HazardType.OVERCROWDING, Severity.HIGH))    // ordinal=1 < 3
        assertShownSeverity(Severity.HIGH)
    }

    @Test
    fun `MEDIUM arriving while LOW is shown upgrades to MEDIUM`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.UNKNOWN, Severity.LOW))
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))   // ordinal=2 < 3
        assertShownSeverity(Severity.MEDIUM)
    }

    // ─── No upgrade: higher-severity showing, lower-severity arrives ──────────

    @Test
    fun `MEDIUM arriving while CRITICAL is shown does NOT upgrade`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))   // ordinal=0
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))   // ordinal=2 > 0
        assertShownSeverity(Severity.CRITICAL)
    }

    @Test
    fun `LOW arriving while CRITICAL is shown does NOT upgrade`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))
        showHazardDetected(fakeHazard(HazardType.UNKNOWN, Severity.LOW))           // ordinal=3 > 0
        assertShownSeverity(Severity.CRITICAL)
    }

    @Test
    fun `LOW arriving while HIGH is shown does NOT upgrade`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.OVERCROWDING, Severity.HIGH))
        showHazardDetected(fakeHazard(HazardType.UNKNOWN, Severity.LOW))           // ordinal=3 > 1
        assertShownSeverity(Severity.HIGH)
    }

    // ─── No upgrade: same severity ────────────────────────────────────────────

    @Test
    fun `same severity arriving while same is shown does NOT replace the displayed hazard`() =
        testScope.runTest {
            val first = fakeHazard(HazardType.OVERCROWDING, Severity.HIGH, id = "first")
            showHazardDetected(first)

            val second = fakeHazard(HazardType.OVERCROWDING, Severity.HIGH, id = "second")
            showHazardDetected(second)  // ordinal=1 == 1 → shouldUpgrade=false

            assertEquals(
                "first hazard must remain — same severity must not replace the shown one",
                "first",
                (viewModel.uiState.value as MonitorUiState.HazardDetected).hazard.id
            )
        }

    // ─── Upgrade resets the 5-second dismiss timer ────────────────────────────

    @Test
    fun `upgrading to higher severity resets the 5-second dismiss timer`() = testScope.runTest {
        // Show MEDIUM — its dismiss timer T starts now
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))
        advanceTimeBy(3_000) // T+3s — still showing MEDIUM, 2s left on original timer

        // CRITICAL arrives at T+3 — upgrades and resets timer to T+3+5 = T+8
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))
        assertShownSeverity(Severity.CRITICAL)

        // T+7 (4s after upgrade) — inside new 5s window, must still be HazardDetected
        advanceTimeBy(4_000)
        assertTrue(
            "State must remain HazardDetected at T+7 — upgraded timer runs until T+8",
            viewModel.uiState.value is MonitorUiState.HazardDetected
        )

        // T+8.1 (5s+ after upgrade) — new timer fires, must dismiss to Monitoring
        advanceTimeBy(1_100)
        assertTrue(
            "State must dismiss to Monitoring at T+8+ (5s after upgrade, not original T+5)",
            viewModel.uiState.value is MonitorUiState.Monitoring
        )
    }

    @Test
    fun `no-op arrival does NOT reset the 5-second dismiss timer`() = testScope.runTest {
        // Show CRITICAL — timer T starts
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))
        advanceTimeBy(3_000) // T+3s

        // Lower-severity MEDIUM arrives — no upgrade, no timer reset
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))
        assertShownSeverity(Severity.CRITICAL)

        // T+5.1 — original timer must still fire (NOT reset by the no-op)
        advanceTimeBy(2_100)
        assertTrue(
            "State must dismiss at T+5 — no-op arrival must not reset the timer",
            viewModel.uiState.value is MonitorUiState.Monitoring
        )
    }

    // ─── Auto-dismiss still works after an upgrade ────────────────────────────

    @Test
    fun `upgraded HazardDetected auto-dismisses to Monitoring after 5 seconds`() = testScope.runTest {
        showHazardDetected(fakeHazard(HazardType.FALLEN_ITEM, Severity.MEDIUM))
        showHazardDetected(fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL))  // upgrade

        advanceTimeBy(5_001)

        assertTrue(
            "Upgraded HazardDetected must still auto-dismiss to Monitoring",
            viewModel.uiState.value is MonitorUiState.Monitoring
        )
    }

    @Test
    fun `Monitoring state from auto-dismiss carries the overlay hazards`() = testScope.runTest {
        val overlayList = listOf(
            HazardOverlayData(RectF(0f, 0f, 0.5f, 0.5f), HazardType.WET_FLOOR, Severity.CRITICAL, null)
        )
        showHazardDetected(
            hazard      = fakeHazard(HazardType.WET_FLOOR, Severity.CRITICAL),
            overlayData = overlayList
        )

        advanceTimeBy(5_001)

        val monitoring = viewModel.uiState.value as MonitorUiState.Monitoring
        assertEquals(
            "Overlay data from HazardDetected must be preserved in the Monitoring state",
            1, monitoring.detectedHazards.size
        )
        assertEquals(HazardType.WET_FLOOR, monitoring.detectedHazards.first().hazardType)
    }

    // ─── Reflection helpers ───────────────────────────────────────────────────

    /**
     * Calls the private [MonitorViewModel.showHazardDetected] via reflection.
     * This bypasses the full camera → ML Kit → Room pipeline and tests the
     * escalation state-machine logic in complete isolation.
     */
    private fun showHazardDetected(
        hazard: Hazard,
        overlayData: List<HazardOverlayData> = emptyList()
    ) {
        val method = MonitorViewModel::class.java.getDeclaredMethod(
            "showHazardDetected",
            Hazard::class.java,
            List::class.java
        )
        method.isAccessible = true
        method.invoke(viewModel, hazard, overlayData)
    }

    private fun assertShownSeverity(expected: Severity) {
        val state = viewModel.uiState.value
        assertTrue(
            "Expected HazardDetected($expected) but state was ${state::class.simpleName}",
            state is MonitorUiState.HazardDetected
        )
        assertEquals(expected, (state as MonitorUiState.HazardDetected).hazard.severity)
    }

    private fun fakeHazard(
        type: HazardType,
        severity: Severity,
        id: String = UUID.randomUUID().toString()
    ) = Hazard(
        id                  = id,
        type                = type,
        severity            = severity,
        imageUri            = null,
        locationDescription = null,
        timestamp           = System.currentTimeMillis()
    )
}
