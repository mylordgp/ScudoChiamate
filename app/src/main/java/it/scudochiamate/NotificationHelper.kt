package it.scudochiamate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Gestisce le notifiche di chiamate bloccate.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showBlockedCallNotification(number: String, reason: BlockReason) {
        val settings = SettingsManager(context)
        if (!settings.isNotifyOnBlockEnabled()) return

        val title = context.getString(R.string.notif_blocked_title)
        val reasonStr = when (reason) {
            BlockReason.FOREIGN_PREFIX -> context.getString(R.string.reason_foreign)
            BlockReason.KNOWN_SPAM    -> context.getString(R.string.reason_spam)
            BlockReason.TIME_BLOCK    -> context.getString(R.string.reason_time_block)
            BlockReason.SYSTEM_IMPORT -> context.getString(R.string.reason_system)
        }
        val body = context.getString(R.string.notif_blocked_body, number, reasonStr)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notif)
    }

    companion object {
        const val CHANNEL_ID = "blocked_calls_channel"
    }
}
