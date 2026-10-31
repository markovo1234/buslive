package com.buslive.app.domain.util

import com.buslive.app.domain.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0
    /** Typical walking speed (m/s). */
    const val WALK_SPEED = 1.25
    /** Streets are not straight lines. */
    const val WALK_DETOUR = 1.3

    fun distanceM(a: GeoPoint, b: GeoPoint): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Walking time (s) for a straight-line distance. */
    fun walkSeconds(straightM: Double): Long = (straightM * WALK_DETOUR / WALK_SPEED).toLong()

    fun walkMinutes(straightM: Double): Int = ((walkSeconds(straightM) + 59) / 60).toInt()

    fun formatDistance(m: Double): String =
        if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(m / 1000)

    /**
     * Index of the polyline vertex the point is closest to, using projection on segments.
     * Returns the fractional position (vertex index + fraction along next segment).
     */
    fun progressAlong(points: List<GeoPoint>, p: GeoPoint): Double {
        if (points.size < 2) return 0.0
        var best = Double.MAX_VALUE
        var bestPos = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            // Local equirectangular projection is fine at city scale.
            val kx = cos(Math.toRadians(p.lat)) * 111_320.0
            val ky = 110_540.0
            val ax = a.lon * kx; val ay = a.lat * ky
            val bx = b.lon * kx; val by = b.lat * ky
            val px = p.lon * kx; val py = p.lat * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val d = (px - cx) * (px - cx) + (py - cy) * (py - cy)
            if (d < best) {
                best = d
                bestPos = i + t
            }
        }
        return bestPos
    }
}
