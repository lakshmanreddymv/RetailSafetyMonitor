package com.example.retailsafetymonitor.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.retailsafetymonitor.R
import com.example.retailsafetymonitor.domain.model.Hazard
import com.example.retailsafetymonitor.domain.model.Severity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delivers local push notifications for hazard detection and escalation events.
 *
 * Two notification types are sent:
 * 1. **Detection alert** ([sendHazardDetectedNotification]) — fired immediately by
 *    [LogHazardUseCase] when a new hazard is first logged to Room.
 * 2. **Escalation reminder** ([sendEscalationNotification]) — fired by
 *    [HazardEscalationWorker] for hazards that remain unresolved beyond
 *    their [Severity.escalationIntervalMs] threshold.
 *
 * Uses [Hazard.id.hashCode] as the Android notification ID so that subsequent
 * updates (escalations) replace rather than stack the same hazard's notification.
 *
 * Both methods are no-ops if the user has revoked notification permission
 * ([NotificationManagerCompat.areNotificationsEnabled] returns false).
 *
 * The notification channel is created in [init] — safe to call repeatedly;
 * Android is idempotent for existing channel IDs.
 */
// S: Single Responsibility — posts local notifications; no detection, persistence, or UI logic
// D: Dependency Inversion — callers depend on this concrete class injected by Hilt as @Singleton
@Singleton
class HazardNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Notification channel ID for all hazard alerts. */
        const val CHANNEL_ID = "HAZARD_ALERTS"
        /** Human-readable channel name shown in system notification settings. */
        const val CHANNEL_NAME = "Safety Hazard Alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time safety hazard detection alerts"
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts an initial detection notification for a newly logged hazard.
     *
     * Priority is [NotificationCompat.PRIORITY_MAX] for CRITICAL, [NotificationCompat.PRIORITY_HIGH]
     * for HIGH, and [NotificationCompat.PRIORITY_DEFAULT] for MEDIUM/LOW.
     * Title is severity-prefixed for quick triage: "CRITICAL: Wet Floor Detected".
     *
     * @param hazard The newly detected [Hazard] to notify about.
     */
    fun sendHazardDetectedNotification(hazard: Hazard) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val priority = when (hazard.severity) {
            Severity.CRITICAL -> NotificationCompat.PRIORITY_MAX
            Severity.HIGH -> NotificationCompat.PRIORITY_HIGH
            Severity.MEDIUM, Severity.LOW -> NotificationCompat.PRIORITY_DEFAULT
        }
        val title = when (hazard.severity) {
            Severity.CRITICAL -> "CRITICAL: ${hazard.type.displayName} Detected"
            Severity.HIGH -> "HIGH: ${hazard.type.displayName} Detected"
            Severity.MEDIUM -> "${hazard.type.displayName} Detected"
            Severity.LOW -> "Low-priority: ${hazard.type.displayName}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Immediate attention may be required. Tap to view details.")
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        // Use hazard ID hash as notification ID so updates replace, not stack
        NotificationManagerCompat.from(context).notify(hazard.id.hashCode(), notification)
    }

    /**
     * Posts an escalation reminder for a hazard that remains unresolved past its interval.
     *
     * Body text is severity-specific: CRITICAL uses urgent language; LOW uses a gentle reminder.
     * Uses the same notification ID as the original detection alert — replaces it in the tray.
     *
     * @param hazard The still-unresolved [Hazard] being escalated.
     * @param ageMinutes How many minutes have elapsed since the hazard was first detected.
     */
    fun sendEscalationNotification(hazard: Hazard, ageMinutes: Long) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val text = when (hazard.severity) {
            Severity.CRITICAL -> "UNRESOLVED: ${hazard.type.displayName} still active. Immediate action required."
            Severity.HIGH -> "Unresolved hazard: ${hazard.type.displayName} flagged ${ageMinutes}m ago. Please address."
            Severity.MEDIUM -> "Reminder: ${hazard.type.displayName} has been open ${ageMinutes}m. Resolve when possible."
            Severity.LOW -> "Low-priority item ${hazard.type.displayName} flagged ${ageMinutes}m ago."
        }
        val priority = if (hazard.severity == Severity.CRITICAL) NotificationCompat.PRIORITY_MAX
            else NotificationCompat.PRIORITY_DEFAULT

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Unresolved: ${hazard.type.displayName}")
            .setContentText(text)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(hazard.id.hashCode(), notification)
    }
}
