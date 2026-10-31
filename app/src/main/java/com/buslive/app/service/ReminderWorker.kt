package com.buslive.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.buslive.app.MainActivity
import com.buslive.app.R
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.Stop
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** "Notify me 5 min before line X reaches my stop." Re-checks live data before firing. */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    /** Returns false if the bus is already too close to remind. */
    fun schedule(stop: Stop, arrival: Arrival, minutesBefore: Int): Boolean {
        val fireAt = arrival.best.minus(Duration.ofMinutes(minutesBefore.toLong()))
        val delayMs = Duration.between(Instant.now(), fireAt).toMillis()
        if (delayMs < -60_000) return false
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.K_STOP_CODE to stop.code,
                    ReminderWorker.K_STOP_NAME to stop.name,
                    ReminderWorker.K_RIDE to arrival.rideId,
                    ReminderWorker.K_LINE_REF to arrival.lineRef,
                    ReminderWorker.K_LINE to arrival.shortName,
                    ReminderWorker.K_PLANNED to arrival.planned.epochSecond,
                    ReminderWorker.K_BEFORE to minutesBefore,
                    ReminderWorker.K_TRIES to 0,
                )
            )
            .addTag(ReminderWorker.TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("reminder-${stop.code}-${arrival.rideId}", ExistingWorkPolicy.REPLACE, request)
        return true
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val schedule: ScheduleRepository,
    private val live: LiveRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val code = inputData.getInt(K_STOP_CODE, 0)
        val stopName = inputData.getString(K_STOP_NAME).orEmpty()
        val rideId = inputData.getLong(K_RIDE, 0)
        val lineRef = inputData.getLong(K_LINE_REF, 0)
        val line = inputData.getString(K_LINE).orEmpty()
        val before = inputData.getInt(K_BEFORE, 5)
        val tries = inputData.getInt(K_TRIES, 0)
        var eta = Instant.ofEpochSecond(inputData.getLong(K_PLANNED, 0))

        // Use a live estimate when one exists for this ride.
        runCatching {
            val now = Instant.now()
            val arrival = schedule.arrivals(code, from = now.minusSeconds(20 * 60), to = now.plusSeconds(3 * 3600), maxAgeMs = 0)
                .firstOrNull { it.rideId == rideId }
            if (arrival != null) {
                val buses = live.busesForLine(lineRef)
                eta = ArrivalEstimator.attachLive(listOf(arrival), buses, now).first().best
            }
        }

        val minutesLeft = Duration.between(Instant.now(), eta).toMinutes()
        if (minutesLeft > before + 2 && tries < 6) {
            // Bus is late: check again closer to the new time.
            val again = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(Instant.now(), eta).minusMinutes(before.toLong()).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(*inputData.keyValueMap.map { it.key to it.value }.toTypedArray(), K_TRIES to tries + 1))
                .addTag(TAG)
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("reminder-$code-$rideId-r${tries + 1}", ExistingWorkPolicy.REPLACE, again)
            return Result.success()
        }
        notify(line, stopName, minutesLeft.coerceAtLeast(0).toInt(), (code + rideId).toInt())
        return Result.success()
    }

    private fun notify(line: String, stop: String, minutes: Int, id: Int) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (minutes <= 0) ctx.getString(R.string.reminder_now, line, stop)
        else ctx.resources.getQuantityString(R.plurals.reminder_minutes, minutes, line, minutes, stop)
        val n = NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.reminder_title, line))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(Notifications.ID_REMINDER_BASE + (id and 0xFFF), n)
    }

    companion object {
        const val TAG = "arrival-reminder"
        const val K_STOP_CODE = "stop_code"
        const val K_STOP_NAME = "stop_name"
        const val K_RIDE = "ride"
        const val K_LINE_REF = "line_ref"
        const val K_LINE = "line"
        const val K_PLANNED = "planned"
        const val K_BEFORE = "before"
        const val K_TRIES = "tries"
    }
}
