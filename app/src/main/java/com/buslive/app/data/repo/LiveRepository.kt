package com.buslive.app.data.repo

import com.buslive.app.data.api.StrideApi
import com.buslive.app.data.api.VehicleLocationDto
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.LiveBus
import com.buslive.app.domain.util.TimeUtil
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live bus GPS from SIRI (via Stride). SIRI is sampled once a minute and arrives ~2–4 min late,
 * so we ask for the last [WINDOW] and keep only the newest point per vehicle.
 */
@Singleton
class LiveRepository @Inject constructor(
    private val api: StrideApi,
    private val static: StaticDataRepository,
) {
    suspend fun busesInBounds(b: BBox, limit: Int = 1500): List<LiveBus> {
        val now = Instant.now()
        val rows = api.vehicleLocations(
            recordedFrom = TimeUtil.toApi(now - WINDOW),
            // Upper bound filters out bogus rows dated 2038 that exist in the DB.
            recordedTo = TimeUtil.toApi(now + Duration.ofMinutes(1)),
            latMin = b.south, latMax = b.north, lonMin = b.west, lonMax = b.east,
            limit = limit,
        )
        return toBuses(rows, now)
    }

    suspend fun busesForLine(lineRef: Long): List<LiveBus> {
        val now = Instant.now()
        val rows = api.vehicleLocations(
            recordedFrom = TimeUtil.toApi(now - WINDOW),
            recordedTo = TimeUtil.toApi(now + Duration.ofMinutes(1)),
            lineRef = lineRef,
            limit = 500,
        )
        return toBuses(rows, now)
    }

    private suspend fun toBuses(rows: List<VehicleLocationDto>, now: Instant): List<LiveBus> {
        val lines = static.linesByRef()
        return rows.asSequence()
            .mapNotNull { r ->
                val lat = r.lat ?: return@mapNotNull null
                val lon = r.lon ?: return@mapNotNull null
                val at = TimeUtil.parse(r.recordedAtTime) ?: return@mapNotNull null
                val lineRef = r.lineRef ?: return@mapNotNull null
                if (lat == 0.0 || lon == 0.0) return@mapNotNull null
                LiveBus(
                    vehicleRef = r.vehicleRef ?: "ride-${r.siriRideId}",
                    lineRef = lineRef,
                    operatorRef = r.operatorRef ?: 0,
                    lat = lat,
                    lon = lon,
                    bearing = r.bearing ?: 0,
                    speedKmh = r.velocity ?: 0,
                    recordedAt = at,
                    scheduledStart = TimeUtil.parse(r.scheduledStartTime),
                    distanceFromStartM = r.distanceFromJourneyStart,
                    siriRideId = r.siriRideId,
                    line = lines[lineRef],
                )
            }
            .groupBy { it.vehicleRef }
            .mapNotNull { (_, points) -> points.maxByOrNull { it.recordedAt } }
            .filter { it.ageSeconds(now) <= DROP_AFTER_S }
            .toList()
    }

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(6)
        /** Fade markers older than this (seconds). */
        const val FADE_AFTER_S = 180L
        /** Drop markers older than this (seconds). */
        const val DROP_AFTER_S = 420L
    }
}
