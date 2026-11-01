package com.buslive.app.domain

import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.LiveEstimate
import java.time.Instant
import kotlin.math.max

/**
 * Simple, honest live ETA: how far the bus still has to drive along its route (SIRI distance from journey start
 * vs. GTFS shape distance of the stop), divided by a speed that mixes the planned average speed with the bus's
 * last reported speed. Shown in the UI as "LIVE · estimate".
 */
object ArrivalEstimator {

    /** Attach live estimates to arrivals whose ride matches a live bus (same line + same scheduled start). */
    fun attachLive(arrivals: List<Arrival>, buses: List<LiveBus>, now: Instant = Instant.now()): List<Arrival> {
        if (buses.isEmpty()) return arrivals
        val byTrip = buses.filter { it.tripNumber != null }.associateBy { it.tripNumber!! }
        val byRide = buses
            .filter { it.scheduledStart != null }
            .associateBy { it.lineRef to it.scheduledStart!!.epochSecond }
        return arrivals.map { a ->
            val bus = a.tripNumber?.let { byTrip[it] }
                ?: (if (a.rideStartKnown) byRide[a.lineRef to a.rideStart.epochSecond] else null)
                ?: return@map a
            val est = estimate(a, bus, now)
            if (est == null) a else a.copy(live = est)
        }
    }

    fun estimate(a: Arrival, bus: LiveBus, now: Instant = Instant.now()): LiveEstimate? {
        val stopDist = a.shapeDistM ?: return null
        val busDist = bus.distanceFromStartM ?: return null
        val remaining = stopDist - busDist
        if (remaining < -150) return null // bus already passed this stop
        // Ride start unknown in the API: use the bus's own scheduled start, else a typical city-bus speed.
        val rideStart = if (a.rideStartKnown) a.rideStart else bus.scheduledStart
        if (rideStart == null) {
            val reported = bus.speedKmh / 3.6
            val speed = (if (bus.speedKmh in 8..90) 0.6 * CITY_SPEED + 0.4 * reported else CITY_SPEED).coerceIn(2.0, 25.0)
            val eta = bus.recordedAt.plusSeconds((remaining.coerceAtLeast(0) / speed).toLong())
            return LiveEstimate(maxOf(eta, now), bus.vehicleRef, bus.recordedAt, remaining.coerceAtLeast(0))
        }
        val plannedTravelS = a.planned.epochSecond - rideStart.epochSecond
        // Bus has not left the first stop yet (or this is the first stop).
        if (stopDist <= 0 || plannedTravelS <= 0 || busDist < 30) {
            val leave = maxOf(rideStart, bus.recordedAt)
            val eta = leave.plusSeconds(max(0L, plannedTravelS))
            return LiveEstimate(maxOf(eta, now), bus.vehicleRef, bus.recordedAt, remaining.coerceAtLeast(0))
        }
        val plannedSpeed = stopDist.toDouble() / plannedTravelS // m/s, includes dwell at stops
        val reported = bus.speedKmh / 3.6
        val speed = (if (bus.speedKmh in 8..90) 0.7 * plannedSpeed + 0.3 * reported else plannedSpeed)
            .coerceIn(2.0, 25.0)
        val travelS = (remaining.coerceAtLeast(0) / speed).toLong()
        val eta = bus.recordedAt.plusSeconds(travelS)
        return LiveEstimate(maxOf(eta, now), bus.vehicleRef, bus.recordedAt, remaining.coerceAtLeast(0))
    }

    /** Typical average city-bus speed including stops (m/s), used only when the timetable start is unknown. */
    private const val CITY_SPEED = 5.0
}
