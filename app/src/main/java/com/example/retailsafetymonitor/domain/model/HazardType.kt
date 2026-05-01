package com.example.retailsafetymonitor.domain.model

/**
 * Retail safety hazard categories detected by [HazardDetector].
 *
 * Mapped from ML Kit Object Detection coarse category labels (Food, Home good,
 * Fashion good, Plant) via position heuristics and per-frame aggregation.
 * OVERCROWDING is detected by counting person-labeled bounding boxes (≥3 per frame),
 * not by a direct label entry.
 *
 * Each value is documented with the relevant OSHA regulation and penalty range
 * for a single unaddressed citation.
 *
 * @property displayName Human-readable name shown on bounding box overlays and
 *   notification titles.
 */
// S: Single Responsibility — enumerates every hazard category the app can detect and report
// D: Dependency Inversion — no framework dependencies; referenced by all layers as a pure value
enum class HazardType(val displayName: String) {

    /**
     * Liquid on a walking surface, creating a slip-and-fall risk.
     *
     * **OSHA:** 29 CFR 1910.22(a)(1) — walking-working surfaces must be kept clean and dry.
     * **Cost:** Wilful/repeat violation up to \$156,259 per citation; serious up to \$15,625.
     * NFPA 101 slip hazard clauses may also apply in jurisdictions that adopt the Life Safety Code.
     */
    WET_FLOOR("Wet Floor"),

    /**
     * Merchandise, equipment, or fixtures obstructing a fire exit or egress path.
     *
     * **OSHA:** 29 CFR 1910.37(a)(3) — exit routes must remain free of obstructions at all times.
     * **Cost:** Up to \$156,259 per wilful/repeat citation; OSHA issues immediate abatement orders
     * for blocked exits as they directly threaten evacuation capacity.
     */
    BLOCKED_EXIT("Blocked Exit"),

    /**
     * Product or object has fallen from shelving onto the floor or aisle.
     *
     * **OSHA:** 29 CFR 1910.22 — general housekeeping standards; items that create trip or
     * struck-by hazards must be removed promptly.
     * **Cost:** Up to \$15,625 per other-than-serious citation; higher if workers or customers
     * are injured (recordable incident, OSHA 300 log entry).
     */
    FALLEN_ITEM("Fallen Item"),

    /**
     * Customer density exceeds safe evacuation capacity in a defined zone.
     *
     * **OSHA:** 29 CFR 1910.36 — maximum occupant load must be posted and observed; IFC Section
     * 1004 sets occupant load calculation methodology for retail.
     * **Cost:** Fire marshal can issue immediate closure orders; OSHA penalties up to \$15,625
     * per citation, plus local code fines that can exceed \$5,000/day.
     */
    OVERCROWDING("Overcrowding"),

    /**
     * Liquid or substance spilled in an aisle without cleanup materials deployed.
     *
     * **OSHA:** 29 CFR 1910.22(a)(1) — same housekeeping standard as WET_FLOOR; "unattended"
     * implies no wet-floor sign present, increasing OSHA's assessed culpability.
     * **Cost:** Serious violation up to \$15,625; repeat violation up to \$156,259 if prior
     * citations exist for the same standard.
     */
    UNATTENDED_SPILL("Unattended Spill"),

    /**
     * Power cord, pallet corner, floor mat edge, or other object creating a trip hazard.
     *
     * **OSHA:** 29 CFR 1910.22(a)(2) — floors must be free of hazardous projections and
     * irregular surfaces.
     * **Cost:** Other-than-serious citation up to \$15,625; escalates to serious if prior
     * corrective actions were documented and not taken.
     */
    TRIP_HAZARD("Trip Hazard"),

    /**
     * Combustible material near heat source, improperly stored flammable, or blocked sprinkler.
     *
     * **OSHA:** 29 CFR 1910.157 (fire extinguishers), NFPA 10, NFPA 30 (flammable liquids).
     * **Cost:** Wilful/repeat violation up to \$156,259; local fire marshal can issue immediate
     * stop-work orders. Insurance riders may void coverage if cited hazards are unaddressed.
     */
    FIRE_HAZARD("Fire Hazard"),

    /**
     * ML Kit returned a bounding box that did not match any known label or position heuristic.
     *
     * Logged at [Severity.LOW]; does not trigger escalation until reclassified.
     * Used as a catch-all to surface detections for manual operator review.
     */
    UNKNOWN("Unknown Hazard")
}
