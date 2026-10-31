package com.buslive.app.domain.model

import java.time.Instant
import java.time.LocalDate

data class GeoPoint(val lat: Double, val lon: Double)

data class BBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(p: GeoPoint) = p.lat in south..north && p.lon in west..east
    fun expand(factor: Double): BBox {
        val dLat = (north - south) * factor
        val dLon = (east - west) * factor
        return BBox(south - dLat, west - dLon, north + dLat, east + dLon)
    }
    companion object {
        fun around(p: GeoPoint, meters: Double): BBox {
            val dLat = meters / 111_320.0
            val dLon = meters / (111_320.0 * kotlin.math.cos(Math.toRadians(p.lat)))
            return BBox(p.lat - dLat, p.lon - dLon, p.lat + dLat, p.lon + dLon)
        }
    }
}

data class Stop(
    val code: Int,
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double,
) {
    val point get() = GeoPoint(lat, lon)
}

/** One GTFS route = a line number in one direction / alternative, valid for [date]. */
data class Line(
    val lineRef: Long,
    val routeId: Long,
    val date: LocalDate,
    val operatorRef: Int,
    val shortName: String,
    val longName: String,
    val mkt: String,
    val direction: String,
    val alternative: String,
    val agencyName: String,
    val routeType: String,
) {
    val origin: String get() = RouteNames.origin(longName)
    val destination: String get() = RouteNames.destination(longName)
    val isRail: Boolean get() = routeType == "2"
}

/** Helpers for MOT long names: "Origin stop-Origin city<->Dest stop-Dest city-<dir><alt>". */
object RouteNames {
    private val trailingCode = Regex("-[0-9]+[#0-9A-Za-z]*$")

    fun destination(longName: String): String {
        val parts = longName.split("<->")
        val raw = (if (parts.size > 1) parts[1] else longName).replace(trailingCode, "")
        return pretty(raw)
    }

    fun origin(longName: String): String {
        val raw = longName.split("<->").firstOrNull() ?: longName
        return pretty(raw)
    }

    /** "ת. רכבת יבנה מזרח-יבנה" → "ת. רכבת יבנה מזרח, יבנה" */
    private fun pretty(raw: String): String {
        val idx = raw.lastIndexOf('-')
        if (idx <= 0 || idx == raw.length - 1) return raw.trim()
        val stop = raw.substring(0, idx).trim()
        val city = raw.substring(idx + 1).trim()
        return if (stop.contains(city)) stop else "$stop, $city"
    }
}

data class LiveBus(
    val vehicleRef: String,
    val lineRef: Long,
    val operatorRef: Int,
    val lat: Double,
    val lon: Double,
    val bearing: Int,
    val speedKmh: Int,
    val recordedAt: Instant,
    val scheduledStart: Instant?,
    val distanceFromStartM: Int?,
    val siriRideId: Long?,
    val line: Line?,
    /** Label to show when [line] is unknown (e.g. the bus tracked during live directions). */
    val labelOverride: String? = null,
) {
    val point get() = GeoPoint(lat, lon)
    val shortName: String get() = line?.shortName ?: labelOverride ?: "?"
    fun ageSeconds(now: Instant = Instant.now()): Long = (now.epochSecond - recordedAt.epochSecond).coerceAtLeast(0)
}

/** A stop call of a ride (planned). */
data class RideStop(
    val stopCode: Int,
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val sequence: Int,
    val arrival: Instant,
    val departure: Instant,
    val shapeDistM: Int?,
) {
    val point get() = GeoPoint(lat, lon)
}

data class Ride(
    val rideId: Long,
    val routeId: Long,
    val start: Instant,
    val end: Instant?,
)

data class LiveEstimate(
    val eta: Instant,
    val vehicleRef: String,
    val recordedAt: Instant,
    val metersAway: Int,
)

/** One planned call at a stop, with an optional live estimate. */
data class Arrival(
    val lineRef: Long,
    val shortName: String,
    val operatorRef: Int,
    val agencyName: String,
    val destination: String,
    val rideId: Long,
    val routeId: Long,
    val rideStart: Instant,
    val planned: Instant,
    val stopSequence: Int,
    val shapeDistM: Int?,
    val live: LiveEstimate? = null,
    val dropOffAllowed: Boolean = true,
) {
    /** Best guess of arrival time: live estimate if any, else planned. */
    val best: Instant get() = live?.eta ?: planned
}

enum class FavoriteType { STOP, LINE, PLACE }
enum class PlaceKind { HOME, WORK, CUSTOM }

data class Favorite(
    val id: Long,
    val type: FavoriteType,
    val key: String,
    val title: String,
    val subtitle: String?,
    val lat: Double?,
    val lon: Double?,
    val placeKind: PlaceKind?,
)

data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
) {
    val point get() = GeoPoint(lat, lon)
}
