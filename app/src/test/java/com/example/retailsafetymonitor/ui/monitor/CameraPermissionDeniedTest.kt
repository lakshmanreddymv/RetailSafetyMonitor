package com.example.retailsafetymonitor.ui.monitor

import androidx.lifecycle.LifecycleOwner
import com.example.retailsafetymonitor.data.camera.CameraManager
import com.example.retailsafetymonitor.data.ml.HazardDetector
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import com.example.retailsafetymonitor.domain.usecase.DetectHazardUseCase
import com.example.retailsafetymonitor.domain.usecase.LogHazardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests verifying that [MonitorViewModel] handles camera permission denial cleanly.
 *
 * Camera permission denial manifests at runtime as a CameraX binding exception.
 * [CameraManager.startCamera] invokes the [onError] callback when this happens.
 * The ViewModel must:
 *  1. Transition to [MonitorUiState.Error] with a meaningful message.
 *  2. Never crash or leave resources in an inconsistent state.
 *  3. Allow recovery via [MonitorViewModel.retryAfterError] → back to [MonitorUiState.Idle].
 *  4. Clean up the executor safely even when no camera was ever started.
 *
 * [DetectHazardUseCase] is used as a real instance (pure logic, no Android deps).
 * All other dependencies are mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraPermissionDeniedTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cameraManager: CameraManager
    private lateinit var hazardDetector: HazardDetector
    private lateinit var logHazardUseCase: LogHazardUseCase
    private lateinit var viewModel: MonitorViewModel

    /** Captured [onError] callback from the last [CameraManager.startCamera] call. */
    private var capturedOnError: ((Exception) -> Unit)? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraManager   = mock()
        hazardDetector  = mock()
        logHazardUseCase = mock()

        // Capture the onError callback so tests can trigger it directly
        whenever(
            cameraManager.startCamera(
                lifecycleOwner     = any(),
                previewView        = any(),
                cameraExecutor     = any(),
                onHazardsDetected  = any(),
                onModelReady       = any(),
                onError            = any()
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedOnError = invocation.getArgument(5) as (Exception) -> Unit
            Unit
        }

        viewModel = MonitorViewModel(
            cameraManager        = cameraManager,
            hazardDetector       = hazardDetector,
            detectHazardUseCase  = DetectHazardUseCase(),
            logHazardUseCase     = logHazardUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        viewModel.onCleared()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle before any camera interaction`() {
        assertEquals(MonitorUiState.Idle, viewModel.uiState.value)
    }

    // ── Permission denied → Error state ───────────────────────────────────────

    @Test
    fun `startCamera followed by permission error transitions to Error state`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        // Simulate CameraX throwing on binding due to permission denial
        capturedOnError?.invoke(SecurityException("Camera permission denied"))

        val state = viewModel.uiState.value
        assertTrue(
            "State must be Error after permission denial, was: $state",
            state is MonitorUiState.Error
        )
    }

    @Test
    fun `Error state message is not blank after permission denial`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Camera permission denied"))

        val error = viewModel.uiState.value as MonitorUiState.Error
        assertTrue("Error message must not be blank", error.message.isNotBlank())
    }

    @Test
    fun `Error message includes contextual text when permission is denied`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Camera permission denied"))

        val error = viewModel.uiState.value as MonitorUiState.Error
        // The ViewModel prepends "Camera error: " — assert it carries the cause message
        assertTrue(
            "Error message should contain context",
            error.message.contains("Camera", ignoreCase = true) ||
            error.message.contains("permission", ignoreCase = true) ||
            error.message.contains("denied", ignoreCase = true)
        )
    }

    @Test
    fun `generic camera binding failure also transitions to Error state`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(IllegalStateException("No cameras available"))

        assertTrue(viewModel.uiState.value is MonitorUiState.Error)
    }

    @Test
    fun `permission denied mid-session (camera started then error) — state is Error not Monitoring`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        // Verify intermediate Monitoring state
        assertTrue(viewModel.uiState.value is MonitorUiState.Monitoring)

        // Mid-session permission revocation
        capturedOnError?.invoke(SecurityException("Permission revoked by OS"))

        assertFalse(
            "State must NOT be Monitoring after permission revocation",
            viewModel.uiState.value is MonitorUiState.Monitoring
        )
        assertTrue(viewModel.uiState.value is MonitorUiState.Error)
    }

    // ── Recovery after error ──────────────────────────────────────────────────

    @Test
    fun `retryAfterError transitions from Error to Idle then starts camera`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))
        assertTrue(viewModel.uiState.value is MonitorUiState.Error)

        // User grants permission and taps "Retry"
        viewModel.retryAfterError(mockLO, mockPV)

        // retryAfterError sets Idle then immediately calls startCamera → Monitoring
        val state = viewModel.uiState.value
        assertTrue(
            "After retry, state should be Monitoring (camera restarted), was: $state",
            state is MonitorUiState.Monitoring
        )
    }

    @Test
    fun `retryAfterError calls startCamera exactly once`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))

        viewModel.retryAfterError(mockLO, mockPV)

        // Total startCamera calls: 1 (initial) + 1 (retry) = 2
        verify(cameraManager, org.mockito.kotlin.times(2)).startCamera(
            any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `retryAfterError after second denial stays in Error state`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("First denial"))
        viewModel.retryAfterError(mockLO, mockPV)
        // Second denial fires from the new startCamera callback
        capturedOnError?.invoke(SecurityException("Second denial"))

        assertTrue(
            "Repeated permission denial should keep state in Error",
            viewModel.uiState.value is MonitorUiState.Error
        )
    }

    // ── stopCamera from Error state ───────────────────────────────────────────

    @Test
    fun `stopCamera called while in Error state — transitions to Idle without crashing`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))
        assertTrue(viewModel.uiState.value is MonitorUiState.Error)

        // Should not throw
        viewModel.stopCamera(mockLO)

        assertEquals(MonitorUiState.Idle, viewModel.uiState.value)
    }

    // ── Resource cleanup ──────────────────────────────────────────────────────

    @Test
    fun `onCleared shuts down executor even when camera was never successfully started`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))

        viewModel.onCleared()

        assertTrue(
            "Executor must be shut down even after permission denial",
            viewModel.cameraExecutor.isShutdown
        )
    }

    @Test
    fun `onCleared with no prior startCamera — executor shuts down cleanly`() {
        // Never started the camera — permission was denied before the user reached the screen
        viewModel.onCleared()

        assertTrue(
            "Executor must be shut down even when camera was never started",
            viewModel.cameraExecutor.isShutdown
        )
    }

    @Test
    fun `hazardDetector close is called on onCleared after permission error`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))

        viewModel.onCleared()

        verify(hazardDetector).close()
    }

    // ── No hazard processing while in Error state ─────────────────────────────

    @Test
    fun `togglePause while in Error state — no state change`() = runTest {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))
        val errorState = viewModel.uiState.value
        assertTrue(errorState is MonitorUiState.Error)

        viewModel.togglePause(mockLO, mockPV)

        // togglePause has no handler for Error state — state should remain Error
        assertEquals(errorState, viewModel.uiState.value)
    }

    @Test
    fun `startCamera from Error state transitions to Monitoring`() {
        val mockLO = mock<LifecycleOwner>()
        val mockPV = mock<androidx.camera.view.PreviewView>()

        // Enter error
        viewModel.startCamera(mockLO, mockPV)
        capturedOnError?.invoke(SecurityException("Permission denied"))
        assertTrue(viewModel.uiState.value is MonitorUiState.Error)

        // Start camera again (user manually grants permission from settings)
        viewModel.startCamera(mockLO, mockPV)

        assertTrue(
            "startCamera from Error should re-enter Monitoring",
            viewModel.uiState.value is MonitorUiState.Monitoring
        )
    }
}
