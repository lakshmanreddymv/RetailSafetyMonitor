package com.example.retailsafetymonitor.data.repository

import com.example.retailsafetymonitor.data.local.ReportDao
import com.example.retailsafetymonitor.data.local.ReportEntity
import com.example.retailsafetymonitor.domain.model.SafetyReport
import com.example.retailsafetymonitor.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ReportRepository].
 *
 * Delegates to [ReportDao] and maps between [ReportEntity] (Room) and [SafetyReport] (domain).
 * The domain layer never imports [ReportEntity] or [ReportDao] directly.
 *
 * Bound to [ReportRepository] via `@Binds` in [RepositoryModule].
 */
// S: Single Responsibility — maps Room report rows to domain models; no generation or scoring logic
// D: Dependency Inversion — implements [ReportRepository]; callers depend on the interface
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao
) : ReportRepository {

    /** @see ReportRepository.getLatestReport */
    override fun getLatestReport(): Flow<SafetyReport?> =
        reportDao.getLatestReport().map { it?.toDomain() }

    /** @see ReportRepository.getAllReports */
    override fun getAllReports(): Flow<List<SafetyReport>> =
        reportDao.getAllReports().map { list -> list.map { it.toDomain() } }

    /** @see ReportRepository.insertReport */
    override suspend fun insertReport(report: SafetyReport) =
        reportDao.insertReport(ReportEntity.fromDomain(report))
}
