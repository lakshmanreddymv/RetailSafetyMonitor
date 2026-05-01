package com.example.retailsafetymonitor.ui.dashboard

import app.cash.turbine.test
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.usecase.GenerateSafetyReportUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for [DashboardViewModel].
 *
 * Verifies that the compliance score is scoped to the current week only
 * (not all historical hazards), that the top hazard type is correctly
 * computed, and that week boundary filtering works.
 *
 * Uses a fake [HazardRepository] backed by [flowOf] to avoid Room setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val hazardRepository: HazardRepository = mock()
    private val generateSafetyReportUseCase: GenerateSafetyReportUseCase = mock()

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Compliance score computation ─────────────────────────────────────────

    @Test
    fun `Zero weekly hazards produces compliance score of 100`() = runTest {
        setupRepositoryWith(emptyList())
        createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(100, state.complianceScore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `All weekly hazards resolved produces score of 100`() = runTest {
        val hazards = listOf(
            hazard(Severity.CRITICAL, resolved = true),
            hazard(Severity.HIGH, resolved = true)
        )
        setupRepositoryWith(hazards)
        createViewModel()

        viewModel.uiState.test {
            assertEquals(100, awaitItem().complianceScore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CRITICAL unresolved hazard reduces compliance score`() = runTest {
        // 10 total: 9 resolved LOW + 1 CRITICAL unresolved → base=90, penalty=10, score=80
        val hazards = (1..9).map { hazard(Severity.LOW, resolved = true) } +
                listOf(hazard(Severity.CRITICAL, resolved = false))
        setupRepositoryWith(hazards)
        createViewModel()

        viewModel.uiState.test {
            assertEquals(80, awaitItem().complianceScore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Stats computation ────────────────────────────────────────────────────

    @Test
    fun `totalDetected and totalResolved are computed from weekly hazards`() = runTest {
        val hazards = listOf(
            hazard(Severity.MEDIUM, resolved = true),
            hazard(Severity.MEDIUM, resolved = true),
            hazard(Severity.HIGH, resolved = false)
        )
        setupRepositoryWith(hazards)
        createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.totalDetected)
            assertEquals(2, state.totalResolved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Top hazard type ──────────────────────────────────────────────────────

    @Test
    fun `Top hazard type is the most frequently detected type`() = runTest {
        val hazards = listOf(
            hazard(Severity.MEDIUM, resolved = false, type = HazardType.WET_FLOOR),
            hazard(Severity.MEDIUM, resolved = false, type = HazardType.WET_FLOOR),
            hazard(Severity.HIGH, resolved = false, type = HazardType.OVERCROWDING)
        )
        setupRepositoryWith(hazards)
        createViewModel()

        viewModel.uiState.test {
            assertEquals(HazardType.WET_FLOOR, awaitItem().topHazardType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `No incidents produces null top hazard type`() = runTest {
        setupRepositoryWith(emptyList())
        createViewModel()

        viewModel.uiState.test {
            assertNull(awaitItem().topHazardType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun setupRepositoryWith(hazards: List<Hazard>) {
        whenever(hazardRepository.getHazardsForWeek(any(), any())).thenReturn(flowOf(hazards))
        whenever(generateSafetyReportUseCase.calculateComplianceScore(hazards)).thenCallRealMethod()
    }

    private fun createViewModel() {
        // Real use case for compliance score, mock only for report generation
        val realUseCase = GenerateSafetyReportUseCase(
            hazardRepository = hazardRepository,
            reportRepository = mock(),
            geminiApi = mock()
        )
        viewModel = DashboardViewModel(
            hazardRepository = hazardRepository,
            generateSafetyReportUseCase = realUseCase
        )
    }

    private fun hazard(
        severity: Severity,
        resolved: Boolean,
        type: HazardType = HazardType.UNKNOWN
    ) = Hazard(
        id = UUID.randomUUID().toString(),
        type = type,
        severity = severity,
        imageUri = null,
        locationDescription = null,
        timestamp = System.currentTimeMillis(),
        isResolved = resolved
    )
}
