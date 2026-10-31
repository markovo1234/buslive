package com.buslive.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.buslive.app.R

object Notifications {
    const val CHANNEL_GUIDANCE = "guidance"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_REMINDERS = "reminders"

    const val ID_GUIDANCE = 1001
    const val ID_ALERT = 1002
    const val ID_REMINDER_BASE = 2000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val guidance = NotificationChannel(
            CHANNEL_GUIDANCE, context.getString(R.string.channel_guidance), NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_guidance_desc)
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
        }
        nm.createNotificationChannels(listOf(guidance, alerts, reminders))
    }
}
