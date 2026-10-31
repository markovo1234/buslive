package com.buslive.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.buslive.app.R
import com.buslive.app.data.repo.LiveRepository
import com.buslive.app.data.repo.LocationRepository
import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.UserLocation
import com.buslive.app.domain.ArrivalEstimator
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.LiveEstimate
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.planner.StopRef
import com.buslive.app.domain.planner.TripLeg
import com.buslive.app.domain.planner.TripOption
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor

enum class GuidancePhase { WALK, WAIT, ON_BOARD, ARRIVED }

/** What the user should do right now. Drives the guidance screen and the ongoing notification. */
data class GuidanceState(
    val trip: TripOption,
    val quickAlert: Boolean,
    val legIndex: Int = 0,
    val phase: GuidancePhase = GuidancePhase.WALK,
    val user: GeoPoint? = null,
    val distanceToTargetM: Int? = null,
    val busEstimate: LiveEstimate? = null,
    val busPosition: GeoPoint? = null,
    val stopsLeft: Int? = null,
    val etaDestination: Instant? = null,
    val readyAlerted: Boolean = false,
    val getOffAlerted: Boolean = false,
    val lastGpsAt: Instant? = null,
) {
    val leg: TripLeg get() = trip.legs[legIndex.coerceIn(0, trip.legs.lastIndex)]
    val finalDestinationName: String
        get() = when (val last = trip.legs.last()) {
            is TripLeg.Walk -> last.toName
            is TripLeg.Bus -> last.to.name
        }
}

/**
 * Live directions engine. Runs inside [GuidanceService] so it keeps going with the screen off.
 * GPS rate adapts to distance: slow when far, fast near the stop you need.
 */
@Singleton
class GuidanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val location: LocationRepository,
    private val live: LiveRepository,
    private val schedule: ScheduleRepository,
    private val alerts: GuidanceAlerts,
) {
    private val _state = MutableStateFlow<GuidanceState?>(null)
    val state: StateFlow<GuidanceState?> = _state.asStateFlow()

    private data class GpsConfig(val intervalMs: Long, val high: Boolean)
    private val gps = MutableStateFlow(GpsConfig(10_000, true))

    fun startTrip(trip: TripOption) {
        val firstPhase = if (trip.legs.first() is TripLeg.Bus) GuidancePhase.WAIT else GuidancePhase.WALK
        _state.value = GuidanceState(trip = trip, quickAlert = false, phase = firstPhase)
        startService()
    }

    /** "Alert me at stop X" — the user is on (or about to board) a bus; [stops] run from now to the destination. */
    fun startStopAlert(
        shortName: String,
        operatorRef: Int,
        lineRef: Long?,
        headsign: String,
        rideId: Long?,
        rideStart: Instant?,
        stops: List<RideStop>,
        destination: RideStop,
    ): Boolean {
        val idx = stops.indexOfFirst { it.sequence == destination.sequence }
        if (idx < 0) return false
        val slice = stops.subList(0, idx + 1)
        val first = slice.first()
        val leg = TripLeg.Bus(
            lineRef = lineRef, shortName = shortName, operatorRef = operatorRef, agencyName = "",
            headsign = headsign, rideId = rideId, rideStart = rideStart,
            from = StopRef(first.stopCode, first.name, first.lat, first.lon),
            to = StopRef(destination.stopCode, destination.name, destination.lat, destination.lon),
            start = Instant.now(), end = maxOf(destination.arrival, Instant.now()),
            stopsCount = slice.size - 1, stops = slice,
        )
        val trip = TripOption("alert-${destination.stopCode}", listOf(leg), TripOption.Source.LOCAL)
        _state.value = GuidanceState(trip = trip, quickAlert = true, phase = GuidancePhase.ON_BOARD)
        startService()
        return true
    }

    fun stop() {
        _state.value = null
        context.stopService(Intent(context, GuidanceService::class.java))
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, GuidanceService::class.java))
    }

    /** Text for the Android share sheet. */
    fun shareText(): String? {
        val s = _state.value ?: return null
        val bus = (s.leg as? TripLeg.Bus) ?: s.trip.busLegs.firstOrNull()
        val eta = s.etaDestination ?: s.trip.arrival
        return if (bus != null) context.getString(
            R.string.share_trip_text, bus.shortName, bus.to.name, s.finalDestinationName, TimeUtil.formatTime(eta),
        ) else context.getString(R.string.share_trip_walk_text, s.finalDestinationName, TimeUtil.formatTime(eta))
    }

    // ------------------------------------------------------------------ engine (called by the service)

    suspend fun run() = coroutineScope {
        fillMissingStops()
        val gpsJob = launch {
            gps.collectLatest { cfg ->
                location.updates(cfg.intervalMs, cfg.high).collect { onLocation(it) }
            }
        }
        val liveJob = launch {
            while (isActive) {
                runCatching { pollBus() }
                delay(20_000)
            }
        }
        // Wait until cancelled, arrived or stopped.
        _state.first { it == null || it.phase == GuidancePhase.ARRIVED }
        if (_state.value?.phase == GuidancePhase.ARRIVED) delay(15_000)
        gpsJob.cancel()
        liveJob.cancel()
    }

    /** Local planner legs only know first/last stop: load the full stop list of the ride. */
    private suspend fun fillMissingStops() {
        val s = _state.value ?: return
        val legs = s.trip.legs.map { leg ->
            if (leg !is TripLeg.Bus || leg.stops.size >= 2 || leg.rideId == null) return@map leg
            val all = runCatching { schedule.rideStops(leg.rideId) }.getOrDefault(emptyList())
            val a = all.indexOfFirst { it.stopCode == leg.from.code }
            val b = all.indexOfLast { it.stopCode == leg.to.code }
            if (a >= 0 && b > a) leg.copy(stops = all.subList(a, b + 1), path = all.subList(a, b + 1).map { it.point }) else leg
        }
        _state.update { it?.copy(trip = it.trip.copy(legs = legs)) }
    }

    private fun onLocation(u: UserLocation) {
        val s = _state.value ?: return
        val p = u.point
        var next = s.copy(user = p, lastGpsAt = Instant.now())
        when (val leg = s.leg) {
            is TripLeg.Walk -> {
                val d = Geo.distanceM(p, leg.to)
                next = next.copy(distanceToTargetM = d.toInt(), etaDestination = estimateFinalEta(s, p))
                val nextIsBus = s.trip.legs.getOrNull(s.legIndex + 1) is TripLeg.Bus
                // Reached the stop, or already riding (fast and near the stop: GPS never came within the radius).
                if (d < ARRIVE_RADIUS_M || (nextIsBus && d < 200 && u.speedMps > 5f)) next = advance(next)
            }
            is TripLeg.Bus -> next = onBusLeg(next, leg, p, u.speedMps)
        }
        _state.value = next
        gps.value = desiredGps(next)
    }

    private fun onBusLeg(s: GuidanceState, leg: TripLeg.Bus, p: GeoPoint, speed: Float): GuidanceState {
        val pts = leg.stops.map { it.point }.ifEmpty { listOf(leg.from.point, leg.to.point) }
        val toBoarding = Geo.distanceM(p, leg.from.point)
        if (s.phase == GuidancePhase.WAIT) {
            val progress = Geo.progressAlong(pts, p)
            // Boarded when we clearly moved along the route away from the stop, at vehicle speed.
            val boarded = (progress > 0.6 && toBoarding > 150) || (speed > 6f && toBoarding > 80 && progress > 0.2)
            if (!boarded) return s.copy(distanceToTargetM = toBoarding.toInt())
        }
        val progress = Geo.progressAlong(pts, p)
        val lastIdx = pts.lastIndex
        val nextIdx = ceil(progress).toInt().coerceAtMost(lastIdx)
        // A single-stop list means "the next stop is mine": count it as 1 left until we are close.
        val stopsLeft = if (pts.size < 2) 1 else (lastIdx - floor(progress).toInt()).coerceAtLeast(0)
        val toDest = Geo.distanceM(p, leg.to.point)
        var next = s.copy(phase = GuidancePhase.ON_BOARD, stopsLeft = stopsLeft, distanceToTargetM = toDest.toInt())

        // ETA: planned time from the next stop to the destination, plus a little for the current segment.
        if (leg.stops.size == pts.size && leg.stops.isNotEmpty()) {
            val nextStop = leg.stops[nextIdx]
            val remainingPlanned = Duration.between(nextStop.arrival, leg.stops.last().arrival).seconds.coerceAtLeast(0)
            val toNextS = (Geo.distanceM(p, nextStop.point) / 8.0).toLong()
            next = next.copy(etaDestination = Instant.now().plusSeconds(remainingPlanned + toNextS + remainingWalkSeconds(s)))
        }
        val bus = leg
        val nearDest = toDest <= GET_OFF_RADIUS_M && stopsLeft <= 1
        if (!next.readyAlerted && !nearDest && (stopsLeft == 2 || stopsLeft == 1)) {
            alerts.getReady(bus.shortName, bus.to.name, stopsLeft)
            next = next.copy(readyAlerted = true)
        }
        if (!next.getOffAlerted && (nearDest || stopsLeft == 0)) {
            alerts.getOffNow(bus.shortName, bus.to.name)
            next = next.copy(getOffAlerted = true, readyAlerted = true)
        }
        // Off the bus near the stop (or passed it) → next leg.
        if (toDest < ARRIVE_RADIUS_M * 1.5 || (next.getOffAlerted && progress >= lastIdx - 0.05 && speed < 2.5f)) {
            next = advance(next)
        }
        return next
    }

    private fun advance(s: GuidanceState): GuidanceState {
        val nextIndex = s.legIndex + 1
        if (nextIndex > s.trip.legs.lastIndex) {
            alerts.arrived(s.finalDestinationName)
            return s.copy(phase = GuidancePhase.ARRIVED, distanceToTargetM = 0)
        }
        val nextLeg = s.trip.legs[nextIndex]
        return s.copy(
            legIndex = nextIndex,
            phase = if (nextLeg is TripLeg.Bus) GuidancePhase.WAIT else GuidancePhase.WALK,
            stopsLeft = null, busEstimate = null, busPosition = null,
            readyAlerted = false, getOffAlerted = false,
        )
    }

    /** Live position of the bus we wait for (same line + same planned start). */
    private suspend fun pollBus() {
        val s = _state.value ?: return
        val leg = s.leg as? TripLeg.Bus ?: return
        if (s.phase != GuidancePhase.WAIT || leg.lineRef == null) return
        val buses = live.busesForLine(leg.lineRef)
        val match = buses.firstOrNull { it.scheduledStart != null && it.scheduledStart == leg.rideStart }
            ?: return _state.update { it?.copy(busEstimate = null, busPosition = null) }
        val boarding = leg.stops.firstOrNull()
        val est = ArrivalEstimator.estimate(
            Arrival(
                lineRef = leg.lineRef, shortName = leg.shortName, operatorRef = leg.operatorRef, agencyName = "",
                destination = leg.headsign, rideId = leg.rideId ?: 0, routeId = 0,
                rideStart = leg.rideStart ?: leg.start, planned = leg.start,
                stopSequence = boarding?.sequence ?: 0, shapeDistM = boarding?.shapeDistM,
            ),
            match,
        )
        _state.update { it?.copy(busEstimate = est, busPosition = match.point) }
    }

    private fun estimateFinalEta(s: GuidanceState, p: GeoPoint): Instant {
        val leg = s.leg as? TripLeg.Walk ?: return s.trip.arrival
        val walkS = Geo.walkSeconds(Geo.distanceM(p, leg.to))
        return if (s.legIndex == s.trip.legs.lastIndex) Instant.now().plusSeconds(walkS) else s.trip.arrival
    }

    private fun remainingWalkSeconds(s: GuidanceState): Long =
        s.trip.legs.drop(s.legIndex + 1).filterIsInstance<TripLeg.Walk>().sumOf { Duration.between(it.start, it.end).seconds }

    /** Battery: GPS every 30 s when far, 10 s mid-range, 3 s when close to the stop that matters. */
    private fun desiredGps(s: GuidanceState): GpsConfig {
        val d = s.distanceToTargetM ?: return GpsConfig(10_000, true)
        return when {
            s.phase == GuidancePhase.ON_BOARD && (s.stopsLeft ?: 99) <= 3 -> GpsConfig(3_000, true)
            d > 2_000 -> GpsConfig(30_000, false)
            d > 600 -> GpsConfig(10_000, true)
            else -> GpsConfig(3_000, true)
        }
    }

    companion object {
        const val ARRIVE_RADIUS_M = 60.0
        const val GET_OFF_RADIUS_M = 350.0
    }
}
