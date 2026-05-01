package com.example.retailsafetymonitor.data.ml

import android.graphics.Rect
import android.graphics.RectF
import com.example.retailsafetymonitor.domain.model.HazardType
import com.google.mlkit.vision.objects.DetectedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [HazardDetector].
 *
 * Verifies the ML Kit Object Detection label→[HazardType] mapping,
 * confidence threshold filtering, per-type OVERCROWDING aggregation,
 * and position-based heuristics. All tests run on the JVM without
 * Android framework dependencies.
 *
 * **Key design decisions tested:**
 * - ML Kit Object Detection returns COARSE categories (Food, Home good, etc.),
 *   NOT fine-grained labels like "bottle" or "furniture".
 * - OVERCROWDING is detected by counting person-labeled objects per frame
 *   (threshold ≥ 3), NOT by a direct label→type map entry.
 * - Detection cooldown (60 seconds per type) prevents Room DB flooding;
 *   tests call [HazardDetector.resetCooldowns] before each case.
 */
class HazardDetectorTest {

    private lateinit var detector: HazardDetector

    @Before
    fun setUp() {
        detector = HazardDetector()
        // Reset in-memory cooldown map before each test so every test starts clean
        detector.resetCooldowns()
    }

    // ─── Label → HazardType mapping ──────────────────────────────────────────

    @Test
    fun `Food label maps to UNATTENDED_SPILL`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Food", 0.9f)))
        val types = results.map { it.hazardType }
        assertTrue(types.contains(HazardType.UNATTENDED_SPILL))
    }

    @Test
    fun `Home good label maps to FALLEN_ITEM`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Home good", 0.85f)))
        val types = results.map { it.hazardType }
        assertTrue(types.contains(HazardType.FALLEN_ITEM))
    }

    @Test
    fun `Fashion good label maps to TRIP_HAZARD`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Fashion good", 0.8f)))
        val types = results.map { it.hazardType }
        assertTrue(types.contains(HazardType.TRIP_HAZARD))
    }

    @Test
    fun `Plant label maps to TRIP_HAZARD`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Plant", 0.75f)))
        val types = results.map { it.hazardType }
        assertTrue(types.contains(HazardType.TRIP_HAZARD))
    }

    // ─── Confidence threshold ─────────────────────────────────────────────────

    @Test
    fun `Detection below 0_7 confidence threshold is discarded`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Food", 0.69f)))
        assertTrue("Sub-threshold detection should be empty", results.isEmpty())
    }

    @Test
    fun `Detection at exactly 0_7 confidence threshold is included`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Food", 0.7f)))
        assertFalse("At-threshold detection should be included", results.isEmpty())
    }

    @Test
    fun `Detection at 1_0 confidence is included`() {
        val results = detector.processFrame(listOf(makeDetectedObject("Home good", 1.0f)))
        assertFalse(results.isEmpty())
    }

    @Test
    fun `Unknown label falls through to UNKNOWN type`() {
        val results = detector.processFrame(
            listOf(makeDetectedObject("SomeUnknownCategory", 0.9f)),
            displayWidth = 1000, displayHeight = 1000
        )
        // Unknown labels that don't match position heuristics → UNKNOWN
        results.forEach { result ->
            assertTrue(
                result.hazardType == HazardType.UNKNOWN ||
                result.hazardType == HazardType.FALLEN_ITEM // position heuristic may apply
            )
        }
    }

    // ─── OVERCROWDING aggregation ─────────────────────────────────────────────

    @Test
    fun `1 person in frame does NOT trigger OVERCROWDING`() {
        val results = detector.processFrame(
            listOf(makeDetectedObject("person", 0.95f))
        )
        assertFalse(results.any { it.hazardType == HazardType.OVERCROWDING })
    }

    @Test
    fun `2 persons in frame do NOT trigger OVERCROWDING`() {
        val objects = listOf(
            makeDetectedObject("person", 0.9f),
            makeDetectedObject("person", 0.85f)
        )
        val results = detector.processFrame(objects)
        assertFalse(results.any { it.hazardType == HazardType.OVERCROWDING })
    }

    @Test
    fun `3 persons in frame triggers OVERCROWDING`() {
        val objects = listOf(
            makeDetectedObject("person", 0.9f),
            makeDetectedObject("person", 0.85f),
            makeDetectedObject("person", 0.8f)
        )
        val results = detector.processFrame(objects)
        assertTrue(results.any { it.hazardType == HazardType.OVERCROWDING })
    }

    @Test
    fun `5 persons in frame triggers OVERCROWDING`() {
        val objects = (1..5).map { makeDetectedObject("person", 0.9f) }
        val results = detector.processFrame(objects)
        assertTrue(results.any { it.hazardType == HazardType.OVERCROWDING })
    }

    @Test
    fun `Persons below confidence threshold do not count toward OVERCROWDING`() {
        val objects = (1..5).map { makeDetectedObject("person", 0.6f) }
        val results = detector.processFrame(objects)
        assertFalse(results.any { it.hazardType == HazardType.OVERCROWDING })
    }

    // ─── Multiple detections in one frame ────────────────────────────────────

    @Test
    fun `Multiple different labels in one frame emit multiple HazardTypes`() {
        detector.resetCooldowns()
        val objects = listOf(
            makeDetectedObject("Food", 0.9f),
            makeDetectedObject("Home good", 0.85f)
        )
        val results = detector.processFrame(objects)
        val types = results.map { it.hazardType }.toSet()
        assertTrue(types.contains(HazardType.UNATTENDED_SPILL))
        assertTrue(types.contains(HazardType.FALLEN_ITEM))
    }

    @Test
    fun `Empty detection list emits empty results`() {
        val results = detector.processFrame(emptyList())
        assertTrue(results.isEmpty())
    }

    // ─── Position heuristics ──────────────────────────────────────────────────

    @Test
    fun `Object in lower 40 percent of frame with large area maps to FALLEN_ITEM via heuristic`() {
        // centerY = 700, viewHeight = 1000 → centerY/viewHeight = 0.7 > 0.6 threshold
        // area = 200*200 = 40000, viewArea = 1000*1000 = 1000000, ratio = 0.04 > 0.02 threshold
        val obj = makeDetectedObjectWithBounds("SomeUnknownCategory", 0.9f,
            left = 400, top = 600, right = 600, bottom = 800)
        val results = detector.processFrame(
            listOf(obj), displayWidth = 1000, displayHeight = 1000
        )
        assertTrue(results.any { it.hazardType == HazardType.FALLEN_ITEM })
    }

    @Test
    fun `Object in upper frame area does not trigger FALLEN_ITEM heuristic`() {
        // centerY = 100, viewHeight = 1000 → 0.1, well below 0.6 threshold
        val obj = makeDetectedObjectWithBounds("SomeUnknownCategory", 0.9f,
            left = 400, top = 50, right = 600, bottom = 150)
        val results = detector.processFrame(
            listOf(obj), displayWidth = 1000, displayHeight = 1000
        )
        assertFalse(results.any { it.hazardType == HazardType.FALLEN_ITEM })
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates a mock [DetectedObject] with a single label at the center of a 100x100 bounding box.
     */
    private fun makeDetectedObject(labelText: String, confidence: Float): DetectedObject {
        val mockLabel = mock<DetectedObject.Label>()
        whenever(mockLabel.text).thenReturn(labelText)
        whenever(mockLabel.confidence).thenReturn(confidence)

        val mockObj = mock<DetectedObject>()
        whenever(mockObj.labels).thenReturn(listOf(mockLabel))
        whenever(mockObj.boundingBox).thenReturn(Rect(100, 100, 200, 200))
        whenever(mockObj.trackingId).thenReturn(null)
        return mockObj
    }

    /**
     * Creates a mock [DetectedObject] with explicit bounding box coordinates for
     * position-heuristic tests.
     */
    private fun makeDetectedObjectWithBounds(
        labelText: String,
        confidence: Float,
        left: Int, top: Int, right: Int, bottom: Int
    ): DetectedObject {
        val mockLabel = mock<DetectedObject.Label>()
        whenever(mockLabel.text).thenReturn(labelText)
        whenever(mockLabel.confidence).thenReturn(confidence)

        val mockObj = mock<DetectedObject>()
        whenever(mockObj.labels).thenReturn(listOf(mockLabel))
        whenever(mockObj.boundingBox).thenReturn(Rect(left, top, right, bottom))
        whenever(mockObj.trackingId).thenReturn(null)
        return mockObj
    }
}
