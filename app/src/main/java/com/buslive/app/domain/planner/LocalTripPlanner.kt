package com.buslive.app.domain.planner

import com.buslive.app.data.repo.ScheduleRepository
import com.buslive.app.data.repo.StaticDataRepository
import com.buslive.app.domain.model.Arrival
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.RideStop
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.util.Geo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1 planner on top of the Stride API (no server of our own):
 *  - direct: a ride that calls at a stop near A and later at a stop near B (stop_sequence order = right direction)
 *  - one transfer: ride 1 from near A, ride 2 to near B, meeting at the same or a close stop
 * Requests stay small: ≤ 8 + 8 stop boards, ≤ ~20 single-ride stop lists, max 4 in parallel.
 */
@Singleton
class LocalTripPlanner @Inject constructor(
    private val static: StaticDataRepository,
    private val schedule: ScheduleRepository,
) : TripPlanner {

    private data class Board(val stop: Stop, val walkM: Double, val arrival: Arrival)

    /** First a normal search; if no bus is found, a second, wider one (more walking, longer wait). */
    override suspend fun plan(from: GeoPoint, to: GeoPoint, departAt: Instant): List<TripOption> {
        val first = catching { planOnce(from, to, departAt, WALK_RADIUS_M, ORIGIN_WINDOW, 8) }
        val found = first.getOrNull().orEmpty()
        if (found.any { it.busLegs.isNotEmpty() }) return found
        val wide = catching { planOnce(from, to, departAt, WIDE_WALK_RADIUS_M, WIDE_ORIGIN_WINDOW, 14) }
        val wider = wide.getOrNull().orEmpty()
        return when {
            wider.any { it.busLegs.isNotEmpty() } -> wider
            found.isNotEmpty() -> found
            wider.isNotEmpty() -> wider
            else -> throw first.exceptionOrNull() ?: wide.exceptionOrNull() ?: NoServiceException()
        }
    }

    private suspend fun planOnce(
        from: GeoPoint, to: GeoPoint, departAt: Instant, radiusM: Double, originWindow: Duration, stopCount: Int,
    ): List<TripOption> = coroutineScope {
        val options = mutableListOf<TripOption>()
        val straight = Geo.distanceM(from, to)
        if (straight <= WALK_ONLY_MAX_M) options += walkOnly(from, to, departAt)
        if (straight < 250) return@coroutineScope options

        val originStops = static.nearestStops(from, stopCount, radiusM)
        val destStops = static.nearestStops(to, stopCount, radiusM)
        if (originStops.isEmpty() || destStops.isEmpty()) return@coroutineScope options

        val originBoards = originStops.map { (stop, d) ->
            async {
                val earliest = departAt.plusSeconds(Geo.walkSeconds(d))
                runCatching { schedule.arrivals(stop.code, from = earliest, to = departAt.plus(originWindow)) }
                    .getOrDefault(emptyList())
                    .map { Board(stop, d, it) }
            }
        }
        val destBoards = destStops.map { (stop, d) ->
            async {
                runCatching {
                    schedule.arrivals(stop.code, from = departAt, to = departAt.plus(DEST_WINDOW), includeDropOffOnly = true)
                }.getOrDefault(emptyList()).filter { it.dropOffAllowed }.map { Board(stop, d, it) }
            }
        }
        val origins = originBoards.awaitAll().flatten()
        val dests = destBoards.awaitAll().flatten()
        if (origins.isEmpty() || dests.isEmpty()) {
            if (options.isEmpty()) throw NoServiceException()
            return@coroutineScope options
        }

        // ---------------- direct
        val destsByRide = dests.groupBy { it.arrival.rideId }
        val direct = HashMap<Long, TripOption>()
        for (o in origins) {
            val candidates = destsByRide[o.arrival.rideId] ?: continue
            for (d in candidates) {
                if (d.arrival.stopSequence <= o.arrival.stopSequence) continue
                val opt = buildDirect(from, to, o, d)
                val prev = direct[o.arrival.rideId]
                if (prev == null || better(opt, prev)) direct[o.arrival.rideId] = opt
            }
        }
        val directList = direct.values
            .groupBy { it.busLegs.first().lineRef }
            .flatMap { (_, list) -> list.sortedBy { it.arrival }.take(2) }
            .sortedBy { it.arrival }
            .take(6)
        options += directList

        // ---------------- one transfer (only when direct choice is thin)
        if (directList.size < 3) {
            options += transfers(from, to, origins, dests, directList.mapNotNull { it.busLegs.first().lineRef }.toSet())
        }

        options
            .distinctBy { o -> o.legs.joinToString("|") { l -> if (l is TripLeg.Bus) "${l.rideId}" else "w" } }
            .sortedWith(compareBy<TripOption> { it.arrival }.thenBy { it.walkSeconds })
            .take(8)
    }

    private suspend fun transfers(
        from: GeoPoint,
        to: GeoPoint,
        origins: List<Board>,
        dests: List<Board>,
        directLines: Set<Long>,
    ): List<TripOption> = coroutineScope {
        // First leg: earliest boarding per line, not a line that already goes direct.
        val firstRides = origins
            .filter { it.arrival.lineRef !in directLines }
            .groupBy { it.arrival.lineRef }
            .map { (_, list) -> list.minWith(compareBy<Board> { it.arrival.planned }.thenBy { it.walkM }) }
            .sortedBy { it.arrival.planned }
            .take(MAX_FIRST_RIDES)
        // Second leg: per line, a few rides arriving near B.
        val secondRides = dests
            .filter { it.arrival.lineRef !in directLines }
            .groupBy { it.arrival.lineRef }
            .flatMap { (_, list) -> list.sortedBy { it.arrival.planned }.distinctBy { it.arrival.rideId }.take(2) }
            .sortedBy { it.arrival.planned }
            .take(MAX_SECOND_RIDES)
        if (firstRides.isEmpty() || secondRides.isEmpty()) return@coroutineScope emptyList()

        val rideIds = (firstRides.map { it.arrival.rideId } + secondRides.map { it.arrival.rideId }).distinct()
        val stopsByRide = rideIds.map { id -> async { id to runCatching { schedule.rideStops(id) }.getOrDefault(emptyList()) } }
            .awaitAll().toMap()

        val results = mutableListOf<TripOption>()
        for (a in firstRides) {
            val aStops = stopsByRide[a.arrival.rideId].orEmpty()
            val downstream = aStops.filter { it.sequence > a.arrival.stopSequence }
            if (downstream.isEmpty()) continue
            for (b in secondRides) {
                if (b.arrival.lineRef == a.arrival.lineRef) continue
                val bStops = stopsByRide[b.arrival.rideId].orEmpty()
                val upstream = bStops.filter { it.sequence < b.arrival.stopSequence }
                if (upstream.isEmpty()) continue
                var best: Triple<RideStop, RideStop, Double>? = null
                for (x in downstream) {
                    for (y in upstream) {
                        val walk = if (x.stopCode == y.stopCode) 0.0 else Geo.distanceM(x.point, y.point)
                        if (walk > TRANSFER_RADIUS_M) continue
                        val ready = x.arrival.plusSeconds(Geo.walkSeconds(walk) + TRANSFER_BUFFER_S)
                        if (ready > y.departure) continue
                        // Prefer the earliest feasible transfer (shortest first ride), then shortest walk.
                        if (best == null || x.sequence < best.first.sequence ||
                            (x.sequence == best.first.sequence && walk < best.third)
                        ) best = Triple(x, y, walk)
                    }
                }
                val (x, y, walk) = best ?: continue
                results += buildTransfer(from, to, a, x, y, walk, b, aStops, bStops)
            }
        }
        results
            .groupBy { o -> o.busLegs.joinToString("+") { "${it.lineRef}" } }
            .map { (_, list) -> list.minBy { it.arrival } }
            .sortedBy { it.arrival }
            .take(4)
    }

    // ---------------------------------------------------------------- builders

    private fun buildDirect(from: GeoPoint, to: GeoPoint, o: Board, d: Board): TripOption {
        val walk1 = Geo.walkSeconds(o.walkM)
        val board = o.arrival.planned
        val alight = d.arrival.planned
        val legs = mutableListOf<TripLeg>()
        if (o.walkM > 15) legs += walkLeg(from, o.stop.point, "", o.stop.name, o.walkM, board.minusSeconds(walk1 + BOARD_BUFFER_S))
        legs += busLeg(o.arrival, o.stop, d.stop, board, alight, d.arrival.stopSequence - o.arrival.stopSequence, emptyList())
        if (d.walkM > 15) legs += walkLeg(d.stop.point, to, d.stop.name, "", d.walkM, alight)
        return TripOption("d-${o.arrival.rideId}-${o.stop.code}-${d.stop.code}", legs, TripOption.Source.LOCAL)
    }

    private fun buildTransfer(
        from: GeoPoint, to: GeoPoint, a: Board, x: RideStop, y: RideStop, walkM: Double, b: Board,
        aStops: List<RideStop>, bStops: List<RideStop>,
    ): TripOption {
        val legs = mutableListOf<TripLeg>()
        val board = a.arrival.planned
        if (a.walkM > 15) legs += walkLeg(from, a.stop.point, "", a.stop.name, a.walkM, board.minusSeconds(Geo.walkSeconds(a.walkM) + BOARD_BUFFER_S))
        val aSlice = aStops.filter { it.sequence in a.arrival.stopSequence..x.sequence }
        val xStop = Stop(x.stopCode, x.name, x.city, x.lat, x.lon)
        legs += busLeg(a.arrival, a.stop, xStop, board, x.arrival, x.sequence - a.arrival.stopSequence, aSlice)
        if (walkM > 15) legs += walkLeg(x.point, y.point, x.name, y.name, walkM, x.arrival)
        val bSlice = bStops.filter { it.sequence in y.sequence..b.arrival.stopSequence }
        val yStop = Stop(y.stopCode, y.name, y.city, y.lat, y.lon)
        legs += busLeg(b.arrival, yStop, b.stop, y.departure, b.arrival.planned, b.arrival.stopSequence - y.sequence, bSlice)
        if (b.walkM > 15) legs += walkLeg(b.stop.point, to, b.stop.name, "", b.walkM, b.arrival.planned)
        return TripOption("t-${a.arrival.rideId}-${b.arrival.rideId}-${x.stopCode}", legs, TripOption.Source.LOCAL)
    }

    private fun busLeg(
        a: Arrival, fromStop: Stop, toStop: Stop, start: Instant, end: Instant, stops: Int, slice: List<RideStop>,
    ) = TripLeg.Bus(
        lineRef = a.lineRef,
        shortName = a.shortName,
        operatorRef = a.operatorRef,
        agencyName = a.agencyName,
        headsign = a.destination,
        rideId = a.rideId,
        rideStart = if (a.rideStartKnown) a.rideStart else null,
        tripNumber = a.tripNumber,
        from = StopRef(fromStop.code, fromStop.name, fromStop.lat, fromStop.lon),
        to = StopRef(toStop.code, toStop.name, toStop.lat, toStop.lon),
        start = start,
        end = end,
        stopsCount = stops,
        stops = slice,
    )

    private fun walkLeg(from: GeoPoint, to: GeoPoint, fromName: String, toName: String, meters: Double, start: Instant) =
        TripLeg.Walk(from, to, fromName, toName, meters.toInt(), start, start.plusSeconds(Geo.walkSeconds(meters)))

    private fun walkOnly(from: GeoPoint, to: GeoPoint, departAt: Instant): TripOption {
        val d = Geo.distanceM(from, to)
        return TripOption("walk", listOf(walkLeg(from, to, "", "", d, departAt)), TripOption.Source.LOCAL)
    }

    private fun better(a: TripOption, b: TripOption): Boolean =
        a.arrival < b.arrival || (a.arrival == b.arrival && a.walkSeconds < b.walkSeconds)

    class NoServiceException : Exception("no service")

    private inline fun <T> catching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val WALK_RADIUS_M = 750.0
        const val WIDE_WALK_RADIUS_M = 1_300.0
        val WIDE_ORIGIN_WINDOW: Duration = Duration.ofMinutes(150)
        const val WALK_ONLY_MAX_M = 1_500.0
        const val TRANSFER_RADIUS_M = 200.0
        const val TRANSFER_BUFFER_S = 120L
        const val BOARD_BUFFER_S = 60L
        const val MAX_FIRST_RIDES = 8
        const val MAX_SECOND_RIDES = 10
        val ORIGIN_WINDOW: Duration = Duration.ofMinutes(75)
        val DEST_WINDOW: Duration = Duration.ofHours(4)
    }
}
