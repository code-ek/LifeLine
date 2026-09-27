package com.lifeline.app.emergency

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifeline.app.R
import com.lifeline.app.home.AppNavigation
import com.lifeline.app.home.AppTab

/** High-priority notifications for incoming SOS alerts, separate from chat notifications. */
internal class EmergencyNotifier(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyNotifier"
        private const val CHANNEL_ID = "emergency_sos_alerts"
        private const val NOTIFICATION_ID_BASE = 0x5050
        private val VIBRATION = longArrayOf(0, 600, 250, 600, 250, 600)
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Emergency SOS alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "SOS alerts received from nearby devices over the mesh"
            enableVibration(true)
            vibrationPattern = VIBRATION
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showIncoming(alert: SosAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val sos = alert.payload
        val sender = alert.senderNickname ?: alert.senderPeerID?.take(8) ?: "Unknown"
        val body = buildString {
            append(sos.description.ifBlank { "Needs help" })
            sos.location?.let { append(" · location shared") }
        }
        val openIntent = PendingIntent.getActivity(
            context,
            sos.id.hashCode(),
            AppNavigation.intentFor(context, AppTab.SOS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🆘 SOS ${sos.type.label} from $sender")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try {
            manager.notify(NOTIFICATION_ID_BASE + (sos.id.hashCode() and 0xFFF), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        }
    }
}
