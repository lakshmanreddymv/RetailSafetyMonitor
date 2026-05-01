package com.example.retailsafetymonitor.data.repository

import com.example.retailsafetymonitor.data.local.HazardDao
import com.example.retailsafetymonitor.data.local.HazardEntity
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [HazardRepository].
 *
 * Each method delegates to [HazardDao] and maps between [HazardEntity] (Room) and
 * [Hazard] (domain). The domain layer never imports [HazardEntity] or [HazardDao]
 * directly — all access is through this class via the [HazardRepository] interface.
 *
 * Bound to [HazardRepository] via `@Binds` in [RepositoryModule].
 */
// S: Single Responsibility — maps Room entity operations to domain model; no business logic
// D: Dependency Inversion — implements [HazardRepository]; callers depend on the interface
@Singleton
class HazardRepositoryImpl @Inject constructor(
    private val hazardDao: HazardDao
) : HazardRepository {

    /** @see HazardRepository.getUnresolvedHazards */
    override fun getUnresolvedHazards(): Flow<List<Hazard>> =
        hazardDao.getUnresolvedHazards().map { list -> list.map { it.toDomain() } }

    /** @see HazardRepository.getRecentHazards */
    override fun getRecentHazards(): Flow<List<Hazard>> =
        hazardDao.getRecentHazards().map { list -> list.map { it.toDomain() } }

    /** @see HazardRepository.getHazardsForWeek */
    override fun getHazardsForWeek(weekStart: Long, weekEnd: Long): Flow<List<Hazard>> =
        hazardDao.getHazardsForWeek(weekStart, weekEnd).map { list -> list.map { it.toDomain() } }

    /** @see HazardRepository.getHazardsAfter */
    override suspend fun getHazardsAfter(since: Long): List<Hazard> =
        hazardDao.getHazardsAfter(since).map { it.toDomain() }

    /** @see HazardRepository.insertHazard */
    override suspend fun insertHazard(hazard: Hazard) =
        hazardDao.insertHazard(HazardEntity.fromDomain(hazard))

    /** @see HazardRepository.markResolved */
    override suspend fun markResolved(id: String, resolvedAt: Long, resolvedBy: String?) =
        hazardDao.markResolved(id, resolvedAt, resolvedBy)

    /** @see HazardRepository.updateLastEscalatedAt */
    override suspend fun updateLastEscalatedAt(id: String, lastEscalatedAt: Long) =
        hazardDao.updateLastEscalatedAt(id, lastEscalatedAt)

    /** @see HazardRepository.getAllUnresolvedSnapshot */
    override suspend fun getAllUnresolvedSnapshot(): List<Hazard> =
        hazardDao.getUnresolvedHazardsSnapshot().map { it.toDomain() }
}
