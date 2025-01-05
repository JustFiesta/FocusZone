package com.focuszone.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.focuszone.R
import com.focuszone.ui.MainActivity

/** Notification manager for app
 * notify of:
 * - working monitoring
 * - app near limit
 * - blocked site
 * - blocked app
 */
class NotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "FocusZone"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FocusZone Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "FocusZone monitoring service notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showServiceRunningNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FocusZone Active")
            .setContentText("Monitoring your app usage")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showBlockedAppNotification(appName: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("App Blocked")
            .setContentText("$appName has been blocked")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showBlockedSiteNotification(siteName: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Site Blocked")
            .setContentText("$siteName has been blocked")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showTimeWarningNotification(appName: String, timeLeft: Long) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time Warning")
            .setContentText("$appName: ${timeLeft}min remaining")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}