package com.buslive.app.domain.planner

import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.RideStop
import java.time.Duration
import java.time.Instant

data class StopRef(val code: Int?, val name: String, val lat: Double, val lon: Double) {
    val point get() = GeoPoint(lat, lon)
}

sealed interface TripLeg {
    val start: Instant
    val end: Instant
    val path: List<GeoPoint>

    data class Walk(
        val from: GeoPoint,
        val to: GeoPoint,
        val fromName: String,
        val toName: String,
        val distanceM: Int,
        override val start: Instant,
        override val end: Instant,
        override val path: List<GeoPoint> = listOf(from, to),
    ) : TripLeg

    data class Bus(
        val lineRef: Long?,
        val shortName: String,
        val operatorRef: Int,
        val agencyName: String,
        val headsign: String,
        val rideId: Long?,
        val rideStart: Instant?,
        val from: StopRef,
        val to: StopRef,
        override val start: Instant,
        override val end: Instant,
        val stopsCount: Int,
        /** Stops from boarding to alighting (inclusive). May be empty for OTP legs without stop data. */
        val stops: List<RideStop> = emptyList(),
        override val path: List<GeoPoint> = stops.map { it.point }.ifEmpty { listOf(from.point, to.point) },
    ) : TripLeg
}

data class TripOption(
    val id: String,
    val legs: List<TripLeg>,
    val source: Source,
) {
    enum class Source { LOCAL, OTP }

    val departure: Instant get() = legs.first().start
    val arrival: Instant get() = legs.last().end
    val busLegs: List<TripLeg.Bus> get() = legs.filterIsInstance<TripLeg.Bus>()
    val transfers: Int get() = (busLegs.size - 1).coerceAtLeast(0)
    val walkSeconds: Long get() = legs.filterIsInstance<TripLeg.Walk>().sumOf { Duration.between(it.start, it.end).seconds }
    val rideSeconds: Long get() = busLegs.sumOf { Duration.between(it.start, it.end).seconds }
    val totalSeconds: Long get() = Duration.between(departure, arrival).seconds
    /** Time standing at stops = total − walking − riding. */
    val waitSeconds: Long get() = (totalSeconds - walkSeconds - rideSeconds).coerceAtLeast(0)
    val stopsCount: Int get() = busLegs.sumOf { it.stopsCount }
}

/** Routing engine. [LocalTripPlanner] uses the Stride API; [OtpTripPlanner] uses a self-hosted OpenTripPlanner. */
interface TripPlanner {
    suspend fun plan(from: GeoPoint, to: GeoPoint, departAt: Instant): List<TripOption>
}
