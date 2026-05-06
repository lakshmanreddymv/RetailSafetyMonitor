package com.example.retailsafetymonitor.data.ml

import android.graphics.Rect
import com.google.mlkit.vision.objects.DetectedObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Latency tests for [HazardDetector.processFrame].
 *
 * Asserts that hazard detection completes within 500 ms — the SLA defined in
 * CLAUDE.md. These tests run on the JVM, so they measure logic overhead only
 * (no camera I/O, no ML Kit inference). If the JVM timing ever exceeds 500 ms
 * it signals a regression in the detection algorithm (e.g. an accidental O(n²)
 * loop in label mapping or cooldown management).
 *
 * Two device profiles are simulated:
 *  - Standard: 1 frame, normal object count
 *  - Low-end: sustained 60 frames at maximum plausible object count per frame
 *    (simulates a Snapdragon 400-class device under load)
 *
 * DO NOT TOUCH: ML Kit hazard detection (tested elsewhere in HazardDetectorTest).
 */
class HazardDetectionLatencyTest {

    private lateinit var detector: HazardDetector

    @Before
    fun setUp() {
        detector = HazardDetector()
        detector.resetCooldowns()
    }

    // ── Single-frame latency ──────────────────────────────────────────────────

    @Test
    fun `single frame with 5 objects completes within 500ms`() {
        val objects = (1..5).map { makeObject("Food", 0.9f) }

        val elapsed = measureMs { detector.processFrame(objects) }

        assertTrue(
            "Single-frame detection took ${elapsed}ms — must be < 500ms",
            elapsed < 500L
        )
    }

    @Test
    fun `single frame with 20 objects completes within 500ms`() {
        val objects = (1..20).map { i ->
            when (i % 4) {
                0    -> makeObject("Food", 0.9f)
                1    -> makeObject("Home good", 0.85f)
                2    -> makeObject("person", 0.9f)
                else -> makeObject("Fashion good", 0.8f)
            }
        }

        val elapsed = measureMs { detector.processFrame(objects) }

        assertTrue(
            "20-object frame detection took ${elapsed}ms — must be < 500ms",
            elapsed < 500L
        )
    }

    @Test
    fun `empty frame completes well within 500ms`() {
        val elapsed = measureMs { detector.processFrame(emptyList()) }

        assertTrue("Empty frame took ${elapsed}ms — must be < 500ms", elapsed < 500L)
    }

    // ── Sustained low-end device profile ─────────────────────────────────────

    @Test
    fun `60 consecutive frames — average per-frame latency under 500ms`() {
        val objects = listOf(
            makeObject("Food", 0.9f),
            makeObject("person", 0.9f),
            makeObject("Home good", 0.85f)
        )

        var totalMs = 0L
        val frameCount = 60
        repeat(frameCount) {
            detector.resetCooldowns()   // prevent cooldown skewing timing
            totalMs += measureMs { detector.processFrame(objects) }
        }

        val avgMs = totalMs / frameCount
        assertTrue(
            "Average per-frame latency: ${avgMs}ms — must be < 500ms (low-end device profile)",
            avgMs < 500L
        )
    }

    @Test
    fun `100 frames with max objects — total under 10s (100ms avg budget)`() {
        // Aggressive: 100 frames × 10 objects = stress test on algorithm
        val objects = (1..10).map { makeObject("person", 0.9f) }

        var totalMs = 0L
        repeat(100) {
            detector.resetCooldowns()
            totalMs += measureMs { detector.processFrame(objects) }
        }

        assertTrue(
            "100 frames × 10 objects took ${totalMs}ms total — must be < 10,000ms",
            totalMs < 10_000L
        )
    }

    // ── Cooldown logic does not add latency ───────────────────────────────────

    @Test
    fun `repeated detections of same hazard type with cooldown complete within 500ms`() {
        val objects = listOf(makeObject("Food", 0.9f))

        // First detection — no cooldown
        detector.processFrame(objects)

        // Subsequent detections — cooldown path
        val elapsed = measureMs {
            repeat(10) { detector.processFrame(objects) }
        }

        assertTrue(
            "10 cooldown-skipped detections took ${elapsed}ms — must be < 500ms",
            elapsed < 500L
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun measureMs(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }

    private fun makeObject(label: String, confidence: Float): DetectedObject {
        val mockLabel = mock<DetectedObject.Label>()
        whenever(mockLabel.text).thenReturn(label)
        whenever(mockLabel.confidence).thenReturn(confidence)
        val mockObj = mock<DetectedObject>()
        whenever(mockObj.labels).thenReturn(listOf(mockLabel))
        whenever(mockObj.boundingBox).thenReturn(Rect(100, 100, 300, 300))
        whenever(mockObj.trackingId).thenReturn(null)
        return mockObj
    }
}
