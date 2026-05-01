package com.example.retailsafetymonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.SafetyReport

/**
 * Room entity mapping a [SafetyReport] to the `reports` table.
 *
 * [topHazardTypes] is stored as a comma-separated string of [HazardType.name] values
 * because Room does not support `List<Enum>` columns natively. Deserialization uses
 * [runCatching] to silently skip any enum names that no longer exist after an app update.
 *
 * @property topHazardTypes Comma-separated [HazardType.name] values, e.g. "WET_FLOOR,OVERCROWDING".
 */
// S: Single Responsibility — maps one report row to/from its domain representation
// D: Dependency Inversion — domain layer never imports this; only [ReportRepositoryImpl] touches it
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val weekStartDate: Long,
    val totalHazards: Int,
    val resolvedHazards: Int,
    val complianceScore: Int,
    val topHazardTypes: String,  // comma-separated HazardType names
    val aiSummary: String,
    val generatedAt: Long
) {
    /** Converts this Room entity to the [SafetyReport] domain model. */
    fun toDomain() = SafetyReport(
        id = id,
        weekStartDate = weekStartDate,
        totalHazards = totalHazards,
        resolvedHazards = resolvedHazards,
        complianceScore = complianceScore,
        topHazardTypes = if (topHazardTypes.isBlank()) emptyList()
            else topHazardTypes.split(",").mapNotNull { name ->
                runCatching { HazardType.valueOf(name.trim()) }.getOrNull()
            },
        aiSummary = aiSummary,
        generatedAt = generatedAt
    )

    companion object {
        /** Converts a [SafetyReport] domain model to a Room-insertable [ReportEntity]. */
        fun fromDomain(report: SafetyReport) = ReportEntity(
            id = report.id,
            weekStartDate = report.weekStartDate,
            totalHazards = report.totalHazards,
            resolvedHazards = report.resolvedHazards,
            complianceScore = report.complianceScore,
            topHazardTypes = report.topHazardTypes.joinToString(",") { it.name },
            aiSummary = report.aiSummary,
            generatedAt = report.generatedAt
        )
    }
}
