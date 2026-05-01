package com.example.retailsafetymonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.HazardType
import com.example.retailsafetymonitor.domain.model.Severity

/**
 * Room entity mapping a [Hazard] to the `hazards` table.
 *
 * String fields [type] and [severity] store [HazardType.name] and
 * [Severity.name] respectively. Enum deserialization uses [HazardType.valueOf]
 * and [Severity.valueOf] inside [toDomain].
 *
 * @property lastEscalatedAt Epoch millis of the last escalation notification from
 *   [HazardEscalationWorker]. Null means this hazard has never been escalated.
 *   Used by the worker's idempotency check.
 */
// S: Single Responsibility — maps one hazard row to/from its domain representation
// D: Dependency Inversion — domain layer never imports this; only repository impl touches it
@Entity(tableName = "hazards")
data class HazardEntity(
    @PrimaryKey val id: String,
    val type: String,
    val severity: String,
    val imageUri: String?,
    val locationDescription: String?,
    val timestamp: Long,
    val isResolved: Boolean,
    val resolvedAt: Long?,
    val resolvedBy: String?,
    val notes: String?,
    val lastEscalatedAt: Long? = null
) {
    /** Converts this Room entity to the [Hazard] domain model. Enum fields are parsed via [valueOf]. */
    fun toDomain() = Hazard(
        id = id,
        type = HazardType.valueOf(type),
        severity = Severity.valueOf(severity),
        imageUri = imageUri,
        locationDescription = locationDescription,
        timestamp = timestamp,
        isResolved = isResolved,
        resolvedAt = resolvedAt,
        resolvedBy = resolvedBy,
        notes = notes,
        lastEscalatedAt = lastEscalatedAt
    )

    companion object {
        /** Converts a [Hazard] domain model to a Room-insertable [HazardEntity]. */
        fun fromDomain(hazard: Hazard) = HazardEntity(
            id = hazard.id,
            type = hazard.type.name,
            severity = hazard.severity.name,
            imageUri = hazard.imageUri,
            locationDescription = hazard.locationDescription,
            timestamp = hazard.timestamp,
            isResolved = hazard.isResolved,
            resolvedAt = hazard.resolvedAt,
            resolvedBy = hazard.resolvedBy,
            notes = hazard.notes,
            lastEscalatedAt = hazard.lastEscalatedAt
        )
    }
}
