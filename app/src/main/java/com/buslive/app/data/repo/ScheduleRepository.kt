package com.buslive.app.data.repo

import android.util.LruCache
import com.buslive.app.data.api.GtfsRideStopDto
import com.buslive.app.data.api.StrideApi
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.model.Ride
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.model.RouteNames
import com.buslive.app.domain.util.TimeUtil
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Planned (GTFS) data: arrivals at stops, rides of a route, stop lists of a ride. Small caches, ≤4 parallel calls. */
@Singleton
class ScheduleRepository @Inject constructor(
    private val api: StrideApi,
    private val static: StaticDataRepository,
) {
    private val limiter = Semaphore(4)
    private val rideStopsCache = LruCache<Long, List<RideStop>>(96)
    private val ridesCache = LruCache<Long, List<Ride>>(96)
    private val arrivalsCache = ConcurrentHashMap<String, Pair<Long, List<Arrival>>>()

    /**
     * Planned calls at a stop in [from]..[to], sorted by time. Only calls where boarding is allowed
     * unless [includeDropOffOnly].
     */
    suspend fun arrivals(
        stopCode: Int,
        from: Instant = Instant.now().minus(Duration.ofMinutes(2)),
        to: Instant = Instant.now().plus(Duration.ofHours(3)),
        includeDropOffOnly: Boolean = false,
        maxAgeMs: Long = 60_000,
    ): List<Arrival> {
        val key = "$stopCode|${from.epochSecond / 60}|${to.epochSecond / 60}|$includeDropOffOnly"
        arrivalsCache[key]?.let { (at, list) ->
            if (System.currentTimeMillis() - at < maxAgeMs) return list
        }
        val rows = limiter.withPermit {
            api.rideStopsAtStop(
                stopCode = stopCode,
                arrivalFrom = TimeUtil.toApi(from),
                arrivalTo = TimeUtil.toApi(to),
                limit = 400,
            )
        }
        val list = rows
            .filter { includeDropOffOnly || it.pickupType != 1 }
            .mapNotNull { it.toArrival() }
            .sortedBy { it.planned }
        if (arrivalsCache.size > 200) arrivalsCache.clear()
        arrivalsCache[key] = System.currentTimeMillis() to list
        return list
    }

    suspend fun rideStops(rideId: Long): List<RideStop> {
        rideStopsCache.get(rideId)?.let { return it }
        val rows = limiter.withPermit { api.rideStopsOfRide(rideId) }
        val list = rows.mapNotNull { it.toRideStop() }.sortedBy { it.sequence }
        if (list.isNotEmpty()) rideStopsCache.put(rideId, list)
        return list
    }

    private val rawRidesCache = LruCache<Long, List<com.buslive.app.data.api.GtfsRideDto>>(96)

    private suspend fun rawRides(routeId: Long): List<com.buslive.app.data.api.GtfsRideDto> {
        rawRidesCache.get(routeId)?.let { return it }
        val raw = limiter.withPermit { api.rides(routeId = routeId, limit = 600) }
        if (raw.isNotEmpty()) rawRidesCache.put(routeId, raw)
        return raw
    }

    suspend fun rides(routeId: Long): List<Ride> {
        ridesCache.get(routeId)?.let { return it }
        val raw = rawRides(routeId)
        // The API sometimes has no start_time for today's rides yet. Then read the departure time of every ride
        // at the route's first stop (2 small calls) instead.
        val starts: Map<Long, Instant> = if (raw.isNotEmpty() && raw.all { it.startTime == null }) {
            runCatching { startsFromFirstStop(routeId, raw.first().id) }.getOrDefault(emptyMap())
        } else emptyMap()
        val list = raw.mapNotNull { r ->
            val start = TimeUtil.parse(r.startTime) ?: starts[r.id] ?: return@mapNotNull null
            Ride(rideId = r.id, routeId = r.routeId ?: routeId, start = start, end = TimeUtil.parse(r.endTime), journeyRef = r.journeyRef)
        }.sortedBy { it.start }
        if (list.isNotEmpty()) ridesCache.put(routeId, list)
        return list
    }

    private suspend fun startsFromFirstStop(routeId: Long, sampleRideId: Long): Map<Long, Instant> {
        val first = rideStops(sampleRideId).firstOrNull() ?: return emptyMap()
        val day = first.arrival.atZone(TimeUtil.ZONE).toLocalDate()
        val from = day.atStartOfDay(TimeUtil.ZONE).toInstant()
        val to = day.plusDays(1).atTime(4, 0).atZone(TimeUtil.ZONE).toInstant()
        val rows = limiter.withPermit {
            api.rideStopsAtStopOfRoute(
                stopCode = first.stopCode, routeId = routeId,
                arrivalFrom = TimeUtil.toApi(from), arrivalTo = TimeUtil.toApi(to),
            )
        }
        return rows.filter { it.stopSequence <= 1 || it.stopSequence == first.sequence }
            .mapNotNull { r -> TimeUtil.parse(r.departureTime ?: r.arrivalTime)?.let { r.rideId to it } }
            .toMap()
    }

    /** Planned ride of a live bus: same line, same scheduled start. */
    suspend fun matchRide(bus: LiveBus): Ride? {
        // Best: same trip number (works even when the API has no start times).
        bus.tripNumber?.let { trip ->
            val today = TimeUtil.today()
            for (date in listOf(today, today.minusDays(1))) {
                val line = lineFor(bus.lineRef, date) ?: continue
                val r = rawRides(line.routeId).firstOrNull { it.journeyRef?.substringBefore('_') == trip } ?: continue
                // SIRI's scheduled start is the planned start, so we don't need the (sometimes empty) GTFS one.
                val start = TimeUtil.parse(r.startTime) ?: bus.scheduledStart ?: continue
                return Ride(r.id, r.routeId ?: line.routeId, start, TimeUtil.parse(r.endTime), r.journeyRef)
            }
        }
        val start = bus.scheduledStart ?: return null
        val startDate = start.atZone(TimeUtil.ZONE).toLocalDate()
        val candidates = buildList {
            add(startDate)
            // Rides after midnight can belong to the previous service day.
            if (start.atZone(TimeUtil.ZONE).toLocalTime() < LocalTime.of(4, 0)) add(startDate.minusDays(1))
        }
        for (date in candidates) {
            val line = lineFor(bus.lineRef, date) ?: continue
            val ride = rides(line.routeId).firstOrNull { it.start == start }
            if (ride != null) return ride
        }
        return null
    }

    /** Route for a given date (route ids are per day). Future dates do not exist in the API. */
    suspend fun lineFor(lineRef: Long, date: LocalDate): Line? = static.lineForDate(lineRef, date)

    /**
     * Departures of a line on [date]. GTFS only exists for today and the past, so for a future date we use
     * the same weekday of an earlier week and report which date was used.
     */
    suspend fun departures(lineRef: Long, date: LocalDate): Departures {
        val today = TimeUtil.today()
        var source = date
        while (source.isAfter(today)) source = source.minusWeeks(1)
        val line = lineFor(lineRef, source) ?: return Departures(date, source, emptyList(), null)
        return Departures(date, source, rides(line.routeId), line)
    }

    data class Departures(
        val requestedDate: LocalDate,
        val sourceDate: LocalDate,
        val rides: List<Ride>,
        val line: Line?,
    ) {
        val isEstimatedFromPast get() = requestedDate != sourceDate
    }
}

private fun GtfsRideStopDto.toArrival(): Arrival? {
    val planned = TimeUtil.parse(arrivalTime) ?: return null
    return Arrival(
        lineRef = lineRef ?: return null,
        shortName = routeShortName.orEmpty(),
        operatorRef = operatorRef ?: 0,
        agencyName = agencyName.orEmpty(),
        destination = RouteNames.destination(routeLongName.orEmpty()),
        rideId = rideId,
        routeId = routeId ?: 0,
        rideStart = TimeUtil.parse(rideStartTime) ?: planned,
        planned = planned,
        rideStartKnown = rideStartTime != null,
        tripNumber = rideJourneyRef?.substringBefore('_')?.takeIf { it.isNotBlank() },
        stopSequence = stopSequence,
        shapeDistM = shapeDistTraveled ?: if (stopSequence == 1) 0 else null,
        dropOffAllowed = dropOffType != 1,
    )
}

private fun GtfsRideStopDto.toRideStop(): RideStop? {
    val arr = TimeUtil.parse(arrivalTime) ?: return null
    return RideStop(
        stopCode = stopCode ?: return null,
        name = stopName.orEmpty(),
        city = stopCity.orEmpty(),
        lat = stopLat ?: return null,
        lon = stopLon ?: return null,
        sequence = stopSequence,
        arrival = arr,
        departure = TimeUtil.parse(departureTime) ?: arr,
        shapeDistM = shapeDistTraveled ?: if (stopSequence == 1) 0 else null,
    )
}
