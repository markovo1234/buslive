package com.buslive.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buslive.app.MainActivity
import com.buslive.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loud, vibrating alerts during live directions. */
@Singleton
class GuidanceAlerts @Inject constructor(@ApplicationContext private val context: Context) {

    fun getReady(line: String, stop: String, stopsLeft: Int) {
        val text = context.resources.getQuantityString(R.plurals.alert_get_ready, stopsLeft, stopsLeft, stop)
        post(context.getString(R.string.alert_get_ready_title, line), text)
        vibrate(longArrayOf(0, 300, 200, 300))
    }

    fun getOffNow(line: String, stop: String) {
        post(context.getString(R.string.alert_get_off_title), context.getString(R.string.alert_get_off_text, stop, line))
        vibrate(longArrayOf(0, 600, 250, 600, 250, 900))
    }

    fun arrived(destination: String) {
        post(context.getString(R.string.alert_arrived_title), context.getString(R.string.alert_arrived_text, destination))
        vibrate(longArrayOf(0, 200, 150, 200))
    }

    @SuppressLint("MissingPermission")
    private fun post(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_GUIDANCE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.ID_ALERT, n)
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
