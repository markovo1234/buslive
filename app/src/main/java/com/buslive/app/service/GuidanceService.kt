package com.buslive.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.buslive.app.MainActivity
import com.buslive.app.R
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground service (type location) hosting [GuidanceManager] so directions work with the screen locked. */
@AndroidEntryPoint
class GuidanceService : LifecycleService() {

    @Inject lateinit var manager: GuidanceManager

    private var engineJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            manager.stop()
            return START_NOT_STICKY
        }
        val state = manager.state.value
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, Notifications.ID_GUIDANCE, buildNotification(state), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }

        if (engineJob?.isActive != true) {
            engineJob = lifecycleScope.launch {
                launch {
                    manager.state.filterNotNull().collect { s ->
                        runCatching { NotificationManagerCompat.from(this@GuidanceService).notify(Notifications.ID_GUIDANCE, buildNotification(s)) }
                    }
                }
                manager.run()
                manager.stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engineJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(s: GuidanceState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_GUIDANCE).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, GuidanceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, text) = describe(s)
        return NotificationCompat.Builder(this, Notifications.CHANNEL_GUIDANCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop_directions), stop)
            .build()
    }

    private fun describe(s: GuidanceState): Pair<String, String> {
        val eta = s.etaDestination ?: s.trip.arrival
        val etaText = getString(R.string.eta_at, TimeUtil.formatTime(eta))
        return when (s.phase) {
            GuidancePhase.ARRIVED -> getString(R.string.alert_arrived_title) to s.finalDestinationName
            GuidancePhase.WALK -> {
                val leg = s.leg as TripLeg.Walk
                val d = s.distanceToTargetM?.toDouble()
                getString(R.string.walk_to, leg.toName) to
                    (d?.let { Geo.formatDistance(it) + " · " + getString(R.string.walk_minutes, Geo.walkMinutes(it)) + " · " } ?: "") + etaText
            }
            GuidancePhase.WAIT -> {
                val leg = s.leg as TripLeg.Bus
                val bus = s.busEstimate?.eta ?: leg.start
                getString(R.string.wait_for_line, leg.shortName, leg.from.name) to
                    getString(R.string.bus_at, TimeUtil.formatTime(bus)) + (if (s.busEstimate != null) " (${getString(R.string.live)})" else "") + " · " + etaText
            }
            GuidancePhase.ON_BOARD -> {
                val leg = s.leg as TripLeg.Bus
                val left = s.stopsLeft
                getString(R.string.ride_line_to, leg.shortName, leg.to.name) to
                    (left?.let { resources.getQuantityString(R.plurals.stops_left, it, it) + " · " } ?: "") + etaText
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.buslive.app.STOP_GUIDANCE"
    }
}
