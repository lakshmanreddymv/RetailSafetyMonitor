package com.example.retailsafetymonitor.data.ml

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.retailsafetymonitor.domain.model.HazardType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detection result for a single hazard in one camera frame.
 *
 * @property hazardType The classified hazard category.
 * @property boundingBox **Normalized** (0.0–1.0) bounding box in display orientation.
 *   Multiply by view width/height in [HazardOverlay] to get pixel coordinates.
 * @property trackingId ML Kit's per-object tracking ID across frames, or null.
 * @property confidence Classification confidence (0.0–1.0).
 */
data class HazardDetectionResult(
    val hazardType: HazardType,
    val boundingBox: RectF,
    val trackingId: Int?,
    val confidence: Float
)

/**
 * SAFETY CRITICAL: Part of real-time hazard detection.
 * Any changes must be accompanied by unit tests.
 */

/**
 * Wraps ML Kit [ObjectDetection] and owns the [ObjectDetector] lifecycle.
 *
 * Implements the detection pipeline without the `camera-mlkit-vision` bridge library
 * (artifact `androidx.camera:camera-mlkit-vision` does not exist at CameraX 1.3.4).
 * Instead, follows the same pattern as FakeProductDetector:
 * ```
 * ImageProxy → InputImage.fromMediaImage(mediaImage, rotationDegrees)
 *           → objectDetector.process(inputImage)
 *           → addOnCompleteListener { imageProxy.close() }
 * ```
 *
 * **Coordinate system:** ML Kit returns bounding boxes in display-orientation pixel
 * coordinates (i.e. already rotated by `rotationDegrees`). [processFrame] normalizes
 * them to 0.0–1.0 using the rotated image dimensions. [HazardOverlay] re-scales to
 * actual view pixels. This makes the overlay orientation-independent.
 *
 * **ML Kit label taxonomy:** [ObjectDetection] with [enableClassification] returns
 * COARSE categories: "Fashion good", "Food", "Home good", "Place", "Plant".
 * NOT fine-grained labels like "bottle" or "furniture" — those are ImageLabeling API.
 *
 * **Detection cooldown:** [lastLoggedAt] prevents flooding Room DB when a hazard
 * remains on screen (e.g. OVERCROWDING for 30 s would otherwise insert 450 records).
 */
// S: Single Responsibility — runs ML Kit object detection and maps labels to HazardTypes
// D: Dependency Inversion — injected as @Singleton; callers receive this concrete class from Hilt
@Singleton
class HazardDetector @Inject constructor() {

    /*
     * ML Kit coarse category → HazardType.
     * Position heuristics handle "Place" and objects outside this map.
     */
    private val LABEL_TO_HAZARD = mapOf(
        "Food" to HazardType.UNATTENDED_SPILL,
        "Home good" to HazardType.FALLEN_ITEM,
        "Fashion good" to HazardType.TRIP_HAZARD,
        "Plant" to HazardType.TRIP_HAZARD
    )

    private val CONFIDENCE_THRESHOLD = 0.7f
    private val OVERCROWDING_THRESHOLD = 3
    private val FALLEN_ITEM_Y_RATIO = 0.6f   // centerY / viewHeight > this → low in frame
    private val FALLEN_ITEM_AREA_RATIO = 0.02f  // width*height > 2% of view area
    private val DETECTION_COOLDOWN_MS = 60_000L

    // Per-type cooldown — keyed by HazardType, value = last Room-insert epoch millis
    private val lastLoggedAt = ConcurrentHashMap<HazardType, Long>()

    val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    /**
     * Creates an [ImageAnalysis.Analyzer] that feeds frames directly into [objectDetector].
     * Pattern mirrors FakeProductDetector's barcode scanner:
     * - [InputImage.fromMediaImage] converts the YUV frame with its rotation metadata.
     * - The Task success/failure listeners deliver results on the calling executor thread.
     * - [ImageProxy.close] is always called in [addOnCompleteListener], not in the success
     *   listener, so the proxy is released even if ML Kit throws.
     *
     * @param executor The executor [ImageAnalysis] delivers frames on.
     * @param onHazardsDetected Invoked with a list of detections (may be empty).
     * @param onModelReady Invoked once on the first successful inference (model warm-up done).
     */
    fun bindToImageAnalysis(
        imageAnalysis: ImageAnalysis,
        executor: ExecutorService,
        onHazardsDetected: (List<HazardDetectionResult>) -> Unit,
        onModelReady: () -> Unit
    ) {
        imageAnalysis.setAnalyzer(executor, createAnalyzer(onHazardsDetected, onModelReady))
    }

    private fun createAnalyzer(
        onHazardsDetected: (List<HazardDetectionResult>) -> Unit,
        onModelReady: () -> Unit
    ): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return@Analyzer
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rawW = imageProxy.width
        val rawH = imageProxy.height
        // ML Kit returns boxes in display orientation (post-rotation), so normalize
        // against the rotated dimensions — swap width/height for portrait captures.
        val displayW = if (rotationDegrees == 90 || rotationDegrees == 270) rawH else rawW
        val displayH = if (rotationDegrees == 90 || rotationDegrees == 270) rawW else rawH

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        objectDetector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                onModelReady()
                val results = processFrame(detectedObjects, displayW, displayH)
                onHazardsDetected(results)
            }
            .addOnFailureListener { /* model warming up on first frames — ignore */ }
            .addOnCompleteListener { imageProxy.close() }  // always release the proxy
    }

    /**
     * Maps a list of [DetectedObject] from one camera frame to [HazardDetectionResult].
     * Bounding boxes are normalized to 0.0–1.0 using [displayWidth] / [displayHeight].
     *
     * Exposed as `internal` so unit tests can call it directly without a live camera.
     *
     * @param displayWidth Width of the image after applying [rotationDegrees].
     * @param displayHeight Height of the image after applying [rotationDegrees].
     */
    internal fun processFrame(
        detectedObjects: List<DetectedObject>,
        displayWidth: Int = 1000,
        displayHeight: Int = 1000
    ): List<HazardDetectionResult> {
        val results = mutableListOf<HazardDetectionResult>()
        val now = System.currentTimeMillis()

        // ── OVERCROWDING: count per-frame person detections ──────────────────
        val personCount = detectedObjects.count { obj ->
            obj.labels.any {
                it.text.equals("person", ignoreCase = true) && it.confidence >= CONFIDENCE_THRESHOLD
            }
        }
        if (personCount >= OVERCROWDING_THRESHOLD) {
            val box = detectedObjects.firstOrNull()?.boundingBox
                ?.let { normalizeRect(it, displayWidth, displayHeight) } ?: RectF()
            if (shouldLog(HazardType.OVERCROWDING, now)) {
                results.add(HazardDetectionResult(HazardType.OVERCROWDING, box, null, 1.0f))
            }
        }

        // ── Label-based mapping ───────────────────────────────────────────────
        detectedObjects.forEach { obj ->
            val normalizedBox = normalizeRect(obj.boundingBox, displayWidth, displayHeight)
            val topLabel = obj.labels
                .filter { it.confidence >= CONFIDENCE_THRESHOLD }
                .maxByOrNull { it.confidence }
                ?: return@forEach

            val hazardType = LABEL_TO_HAZARD[topLabel.text]
                ?: inferFromNormalizedPosition(topLabel.text, normalizedBox)
                ?: HazardType.UNKNOWN

            if (shouldLog(hazardType, now)) {
                results.add(
                    HazardDetectionResult(hazardType, normalizedBox, obj.trackingId, topLabel.confidence)
                )
            }
        }
        return results
    }

    /** Converts a pixel [Rect] to a normalized [RectF] (0.0–1.0). */
    private fun normalizeRect(rect: Rect, displayWidth: Int, displayHeight: Int): RectF {
        if (displayWidth <= 0 || displayHeight <= 0) return RectF(0f, 0f, 1f, 1f)
        return RectF(
            rect.left.toFloat() / displayWidth,
            rect.top.toFloat() / displayHeight,
            rect.right.toFloat() / displayWidth,
            rect.bottom.toFloat() / displayHeight
        )
    }

    /**
     * Position heuristics on normalized coordinates (0.0–1.0):
     * - Object center below 60% of frame height AND area > 2% → [HazardType.FALLEN_ITEM]
     * - "Place" label near left/right edges (< 15% or > 85%) → [HazardType.BLOCKED_EXIT]
     */
    private fun inferFromNormalizedPosition(label: String, box: RectF): HazardType? {
        val centerY = box.centerY()
        val area = box.width() * box.height()
        if (centerY > FALLEN_ITEM_Y_RATIO && area > FALLEN_ITEM_AREA_RATIO) return HazardType.FALLEN_ITEM
        if (label.equals("Place", ignoreCase = true) && (box.left < 0.15f || box.right > 0.85f)) {
            return HazardType.BLOCKED_EXIT
        }
        return null
    }

    private fun shouldLog(hazardType: HazardType, now: Long): Boolean {
        val last = lastLoggedAt[hazardType] ?: 0L
        return if ((now - last) >= DETECTION_COOLDOWN_MS) {
            lastLoggedAt[hazardType] = now
            true
        } else false
    }

    /** Resets the per-type cooldown map. Call in tests before each case. */
    fun resetCooldowns() = lastLoggedAt.clear()

    fun close() = objectDetector.close()
}
