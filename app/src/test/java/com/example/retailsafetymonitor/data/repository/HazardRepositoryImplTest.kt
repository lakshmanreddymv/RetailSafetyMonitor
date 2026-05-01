package com.example.retailsafetymonitor.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.retailsafetymonitor.data.local.SafetyDatabase
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Integration tests for [HazardRepositoryImpl] backed by an in-memory Room database.
 *
 * Uses Robolectric so Room can run on the JVM without a real Android device.
 * Each test gets a fresh database via [setUp] / [tearDown].
 *
 * These tests verify the full data path:
 * domain model → [HazardEntity] → Room → [HazardEntity] → domain model.
 * The mapping round-trip is exercised implicitly.
 */
@RunWith(RobolectricTestRunner::class)
class HazardRepositoryImplTest {

    private lateinit var db: SafetyDatabase
    private lateinit var repository: HazardRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SafetyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HazardRepositoryImpl(db.hazardDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── Insert and retrieve ──────────────────────────────────────────────────

    @Test
    fun `Inserted hazard is retrievable via getUnresolvedHazards`() = runTest {
        val hazard = fakeHazard()
        repository.insertHazard(hazard)

        val unresolved = repository.getUnresolvedHazards().first()
        assertEquals(1, unresolved.size)
        assertEquals(hazard.id, unresolved.first().id)
        assertEquals(hazard.type, unresolved.first().type)
        assertEquals(hazard.severity, unresolved.first().severity)
    }

    @Test
    fun `Inserted hazard is retrievable via getRecentHazards`() = runTest {
        val hazard = fakeHazard()
        repository.insertHazard(hazard)

        val recent = repository.getRecentHazards().first()
        assertEquals(1, recent.size)
        assertEquals(hazard.id, recent.first().id)
    }

    // ─── markResolved ─────────────────────────────────────────────────────────

    @Test
    fun `markResolved sets isResolved to true and stamps resolvedAt`() = runTest {
        val hazard = fakeHazard()
        repository.insertHazard(hazard)
        val resolvedAt = System.currentTimeMillis()

        repository.markResolved(hazard.id, resolvedAt, "Manager A")

        val after = repository.getRecentHazards().first().first()
        assertTrue(after.isResolved)
        assertNotNull(after.resolvedAt)
        assertEquals("Manager A", after.resolvedBy)
    }

    @Test
    fun `markResolved removes hazard from getUnresolvedHazards`() = runTest {
        val hazard = fakeHazard()
        repository.insertHazard(hazard)
        repository.markResolved(hazard.id, System.currentTimeMillis(), null)

        val unresolved = repository.getUnresolvedHazards().first()
        assertTrue(unresolved.isEmpty())
    }

    // ─── getHazardsAfter ─────────────────────────────────────────────────────

    @Test
    fun `getHazardsAfter returns only hazards after the given timestamp`() = runTest {
        val oldHazard = fakeHazard(timestamp = 1000L)
        val newHazard = fakeHazard(timestamp = 5000L)
        repository.insertHazard(oldHazard)
        repository.insertHazard(newHazard)

        val result = repository.getHazardsAfter(since = 3000L)
        assertEquals(1, result.size)
        assertEquals(newHazard.id, result.first().id)
    }

    // ─── getHazardsForWeek ────────────────────────────────────────────────────

    @Test
    fun `getHazardsForWeek returns only hazards within the week window`() = runTest {
        val inWeek = fakeHazard(timestamp = 2000L)
        val beforeWeek = fakeHazard(timestamp = 500L)
        val afterWeek = fakeHazard(timestamp = 9000L)
        listOf(inWeek, beforeWeek, afterWeek).forEach { repository.insertHazard(it) }

        val result = repository.getHazardsForWeek(weekStart = 1000L, weekEnd = 8000L).first()
        assertEquals(1, result.size)
        assertEquals(inWeek.id, result.first().id)
    }

    // ─── updateLastEscalatedAt ────────────────────────────────────────────────

    @Test
    fun `updateLastEscalatedAt persists the escalation timestamp`() = runTest {
        val hazard = fakeHazard()
        repository.insertHazard(hazard)
        val escalatedAt = System.currentTimeMillis()

        repository.updateLastEscalatedAt(hazard.id, escalatedAt)

        val after = repository.getAllUnresolvedSnapshot().first()
        assertEquals(escalatedAt, after.lastEscalatedAt)
    }

    // ─── getRecentHazards ordering ────────────────────────────────────────────

    @Test
    fun `getRecentHazards returns hazards in descending timestamp order`() = runTest {
        val older = fakeHazard(timestamp = 1000L)
        val newer = fakeHazard(timestamp = 9000L)
        repository.insertHazard(older)
        repository.insertHazard(newer)

        val result = repository.getRecentHazards().first()
        assertEquals(newer.id, result.first().id)
        assertEquals(older.id, result.last().id)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun fakeHazard(timestamp: Long = System.currentTimeMillis()) = Hazard(
        id = UUID.randomUUID().toString(),
        type = HazardType.WET_FLOOR,
        severity = Severity.CRITICAL,
        imageUri = null,
        locationDescription = null,
        timestamp = timestamp,
        isResolved = false
    )
}
