package com.example.retailsafetymonitor.domain.model

/**
 * OSHA-aligned severity levels for detected safety hazards.
 *
 * Severity governs two behaviours:
 * 1. **Notification priority** — CRITICAL uses [androidx.core.app.NotificationCompat.PRIORITY_MAX]
 *    (heads-up notification); LOW uses default priority (silent delivery to notification drawer).
 * 2. **Escalation cadence** — [HazardEscalationWorker] re-notifies the manager every
 *    [escalationIntervalMs] milliseconds while the hazard remains unresolved.
 *
 * @property oshaViolationCost Human-readable OSHA fine range for this severity tier.
 * @property escalationIntervalMs Minimum time (ms) before [HazardEscalationWorker]
 *   re-notifies the manager about an unresolved hazard at this severity.
 */
// S: Single Responsibility — defines severity tiers and their operational parameters
// D: Dependency Inversion — no framework dependencies; consumed by all layers as a pure value
enum class Severity(val oshaViolationCost: String, val escalationIntervalMs: Long) {

    /**
     * Immediate life-safety risk requiring evacuation or emergency response.
     *
     * **Examples:** BLOCKED_EXIT, WET_FLOOR with no signage, FIRE_HAZARD.
     * **OSHA fine:** Wilful/repeat — up to \$156,259 per citation.
     * **Notification:** [androidx.core.app.NotificationCompat.PRIORITY_MAX] heads-up alert,
     *   vibration on every delivery.
     * **Escalation:** Every **15 minutes** until resolved; [HazardEscalationWorker] title reads
     *   "UNRESOLVED: [type] still active. Immediate action required."
     */
    CRITICAL("Up to \$156,259 per citation", 15 * 60 * 1000L),

    /**
     * Serious risk with potential for injury if not addressed within the hour.
     *
     * **Examples:** OVERCROWDING exceeding posted occupant load.
     * **OSHA fine:** Serious violation — up to \$15,625 per citation.
     * **Notification:** [androidx.core.app.NotificationCompat.PRIORITY_HIGH] alert.
     * **Escalation:** Every **30 minutes**; escalation message includes elapsed time in minutes.
     */
    HIGH("Up to \$15,625 per citation", 30 * 60 * 1000L),

    /**
     * Moderate risk that should be addressed within the shift.
     *
     * **Examples:** FALLEN_ITEM, TRIP_HAZARD, UNATTENDED_SPILL with signage present.
     * **OSHA fine:** Other-than-serious — up to \$1,000 per citation.
     * **Notification:** Default priority; delivered to notification drawer without heads-up.
     * **Escalation:** Every **60 minutes**; reminder tone only.
     */
    MEDIUM("Up to \$1,000 per citation", 60 * 60 * 1000L),

    /**
     * Advisory-level observation that does not require urgent attention.
     *
     * **Examples:** UNKNOWN detections pending operator review.
     * **OSHA fine:** Advisory only — no mandatory fine; may result in a de-minimis notice.
     * **Notification:** Default priority; no heads-up, no vibration.
     * **Escalation:** Every **120 minutes**; low-priority reminder body text.
     */
    LOW("Advisory only", 120 * 60 * 1000L)
}
