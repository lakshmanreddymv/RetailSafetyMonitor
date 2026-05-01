package com.example.retailsafetymonitor.ui.monitor

import android.graphics.RectF
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.retailsafetymonitor.data.camera.CameraManager
import com.example.retailsafetymonitor.data.ml.HazardDetectionResult
import com.example.retailsafetymonitor.data.ml.HazardDetector
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.usecase.DetectHazardUseCase
import com.example.retailsafetymonitor.domain.usecase.LogHazardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * ViewModel for [MonitorScreen]. Owns the CameraX lifecycle and
 * processes real-time ML Kit detections.
 *
 * **Threading model:**
 * - [cameraExecutor] is a single-thread [java.util.concurrent.ExecutorService].
 *   ML Kit analysis runs exclusively on this thread.
 * - [kotlinx.coroutines.flow.StateFlow.update] is called DIRECTLY from [cameraExecutor]
 *   (thread-safe) — no [kotlinx.coroutines.launch] per frame. At 20 FPS, launching a
 *   coroutine per frame generates ~1,200 allocations/min and risks GC-induced jitter.
 * - The 5-second HazardDetected auto-dismiss timer IS launched via [viewModelScope]
 *   after the state update (once per detection event, not per frame).
 *
 * **State machine:** see [MonitorUiState].
 *
 * **Resource cleanup:** [onCleared] shuts down [cameraExecutor] and closes the
 * ML Kit [com.google.mlkit.vision.objects.ObjectDetector] to release native resources.
 *
 * Follows Unidirectional Data Flow (UDF):
 * - Events flow UP from UI via public functions
 * - State flows DOWN to UI via [uiState] StateFlow
 * - No direct state mutation from the UI layer
 */
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val hazardDetector: HazardDetector,
    private val detectHazardUseCase: DetectHazardUseCase,
    private val logHazardUseCase: LogHazardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<MonitorUiState>(MonitorUiState.Idle)
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    // Single-thread executor for camera analysis — never blocked by main thread
    val cameraExecutor = Executors.newSingleThreadExecutor()

    private var dismissJob: Job? = null

    /**
     * Binds CameraX to [lifecycleOwner] and starts ML Kit frame analysis.
     * Transitions state to [MonitorUiState.Monitoring] with [isModelReady] false until
     * the first successful inference fires [onModelReady].
     *
     * @param lifecycleOwner Activity or fragment lifecycle owner for CameraX session.
     * @param previewView The [PreviewView] that receives the camera surface.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        _uiState.update { MonitorUiState.Monitoring(isModelReady = false) }
        cameraManager.startCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraExecutor = cameraExecutor,
            onHazardsDetected = { results -> onHazardsDetected(results) },
            onModelReady = {
                // StateFlow.update{} is thread-safe — called from cameraExecutor thread, no launch needed
                _uiState.update { current ->
                    if (current is MonitorUiState.Monitoring) current.copy(isModelReady = true)
                    else current
                }
            },
            onError = { e ->
                _uiState.update { MonitorUiState.Error("Camera error: ${e.message}") }
            }
        )
    }

    /**
     * Unbinds CameraX and transitions state back to [MonitorUiState.Idle].
     *
     * @param lifecycleOwner The same lifecycle owner passed to [startCamera].
     */
    fun stopCamera(lifecycleOwner: LifecycleOwner) {
        cameraManager.stopCamera(lifecycleOwner)
        _uiState.update { MonitorUiState.Idle }
    }

    /**
     * Toggles between Monitoring/HazardDetected and Paused states.
     * Pausing unbinds the camera; resuming re-calls [startCamera].
     *
     * @param lifecycleOwner Lifecycle owner used to bind/unbind CameraX.
     * @param previewView Preview surface passed to [startCamera] on resume.
     */
    fun togglePause(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        when (_uiState.value) {
            is MonitorUiState.Monitoring,
            is MonitorUiState.HazardDetected -> {
                _uiState.update { MonitorUiState.Paused }
                cameraManager.stopCamera(lifecycleOwner)
            }
            is MonitorUiState.Paused -> startCamera(lifecycleOwner, previewView)
            else -> Unit
        }
    }

    /**
     * Resets [MonitorUiState.Error] back to [MonitorUiState.Idle] and restarts the camera.
     * Called by the "Retry" button in [ErrorView].
     *
     * @param lifecycleOwner Lifecycle owner for the new CameraX session.
     * @param previewView Preview surface for the restarted session.
     */
    fun retryAfterError(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        _uiState.update { MonitorUiState.Idle }
        startCamera(lifecycleOwner, previewView)
    }

    // Called from cameraExecutor thread — StateFlow.update{} is thread-safe
    private fun onHazardsDetected(results: List<HazardDetectionResult>) {
        val overlayData = results.map { result ->
            HazardOverlayData(
                boundingBox = result.boundingBox,
                hazardType = result.hazardType,
                severity = detectHazardUseCase.execute(result.hazardType),
                trackingId = result.trackingId
            )
        }

        // Update overlay on every frame (no cooldown needed — only Room insert is throttled)
        _uiState.update { current ->
            when (current) {
                is MonitorUiState.Monitoring -> current.copy(
                    detectedHazards = overlayData,
                    lastDetectionTime = if (overlayData.isNotEmpty()) System.currentTimeMillis() else current.lastDetectionTime
                )
                is MonitorUiState.HazardDetected -> current.copy(allActiveHazards = overlayData)
                else -> current
            }
        }

        // Log new hazards to Room and show HazardDetected state for highest-severity
        if (results.isNotEmpty()) {
            val highestSeverityResult = results.maxByOrNull {
                detectHazardUseCase.execute(it.hazardType).ordinal
            } ?: return

            viewModelScope.launch {
                try {
                    val severity = detectHazardUseCase.execute(highestSeverityResult.hazardType)
                    val hazard = logHazardUseCase.execute(highestSeverityResult.hazardType, severity)
                    showHazardDetected(hazard, overlayData)
                } catch (e: Exception) {
                    // Log failure is non-fatal — overlay still shows
                }
            }
        }
    }

    private fun showHazardDetected(hazard: Hazard, overlayData: List<HazardOverlayData>) {
        val current = _uiState.value
        // Upgrade if new hazard has higher severity than displayed
        val shouldUpgrade = current is MonitorUiState.HazardDetected &&
                hazard.severity.ordinal < current.hazard.severity.ordinal

        if (current !is MonitorUiState.HazardDetected || shouldUpgrade) {
            dismissJob?.cancel()
            _uiState.update { MonitorUiState.HazardDetected(hazard, overlayData) }
            // 5-second auto-dismiss back to Monitoring
            dismissJob = viewModelScope.launch {
                delay(5_000)
                _uiState.update { s ->
                    if (s is MonitorUiState.HazardDetected)
                        MonitorUiState.Monitoring(detectedHazards = s.allActiveHazards, isModelReady = true)
                    else s
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        hazardDetector.close()
    }
}
