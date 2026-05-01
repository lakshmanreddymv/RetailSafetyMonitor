package com.example.retailsafetymonitor.data.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.retailsafetymonitor.data.ml.HazardDetectionResult
import com.example.retailsafetymonitor.data.ml.HazardDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the CameraX session lifecycle for the hazard detection pipeline.
 *
 * Binds [Preview] (live viewfinder) and [ImageAnalysis] (ML Kit feed) to the provided
 * [LifecycleOwner]. Analysis frames are delivered on [cameraExecutor] — never on the
 * main thread — and forwarded to [HazardDetector.bindToImageAnalysis].
 *
 * **Target resolution:** 640×480. This is the minimum resolution required for ML Kit
 * Object Detection to produce bounding boxes with < 300ms latency on mid-range hardware.
 * CameraX selects the closest supported resolution; it does not guarantee an exact match.
 *
 * Called exclusively by [MonitorViewModel]; never by UI composables directly.
 */
// S: Single Responsibility — owns CameraX bind/unbind; no ML inference or UI state logic
// D: Dependency Inversion — [MonitorViewModel] depends on this concrete class injected by Hilt
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hazardDetector: HazardDetector
) {
    /**
     * Binds [Preview] and [ImageAnalysis] to the provided [lifecycleOwner].
     *
     * [onModelReady] fires once on the first successful ML Kit inference (model is warm).
     * [onHazardsDetected] fires for every frame — including frames with zero detections.
     * [onError] fires if [ProcessCameraProvider.bindToLifecycle] throws.
     *
     * @param lifecycleOwner The [LifecycleOwner] controlling the CameraX session duration.
     * @param previewView The [PreviewView] that displays the live camera feed.
     * @param cameraExecutor Single-thread executor for frame analysis callbacks.
     * @param onHazardsDetected Callback invoked with detection results on [cameraExecutor] thread.
     * @param onModelReady Callback invoked once after the first successful inference.
     * @param onError Callback invoked if camera binding fails.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraExecutor: ExecutorService,
        onHazardsDetected: (List<HazardDetectionResult>) -> Unit,
        onModelReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Target 640x480 — required for <300ms ML Kit latency target
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                hazardDetector.bindToImageAnalysis(
                    imageAnalysis = imageAnalysis,
                    executor = cameraExecutor,
                    onHazardsDetected = onHazardsDetected,
                    onModelReady = onModelReady
                )

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Unbinds all use cases from the [lifecycleOwner], releasing the camera hardware.
     * Safe to call from any thread; delegates to the main executor internally.
     *
     * @param lifecycleOwner The [LifecycleOwner] whose camera session should be stopped.
     */
    fun stopCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching { cameraProviderFuture.get().unbindAll() }
        }, ContextCompat.getMainExecutor(context))
    }
}
