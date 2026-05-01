package com.example.retailsafetymonitor.domain.model

/**
 * Immutable snapshot pairing a resolved [Hazard] with its resolution details.
 *
 * Created by [ResolveHazardUseCase] after a manager marks a hazard as addressed.
 * Used in [IncidentsScreen] to display audit-trail entries and in
 * [GenerateSafetyReportUseCase] to calculate the weekly resolution rate.
 *
 * @property hazard The original hazard domain object as it was at detection time.
 * @property resolvedAt Epoch millis when the manager acknowledged the resolution.
 * @property resolvedBy Display name of the manager who resolved it, or null if unattributed.
 * @property notes Optional free-text notes added by the manager at resolution time.
 */
// S: Single Responsibility — bundles a hazard with its resolution metadata for audit purposes
// D: Dependency Inversion — no framework dependencies; depends only on domain model [Hazard]
data class IncidentReport(
    val hazard: Hazard,
    val resolvedAt: Long,
    val resolvedBy: String?,
    val notes: String?
)
