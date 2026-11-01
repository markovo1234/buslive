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
    /**
     * The Stride pipeline sometimes runs several minutes behind. Anchor the time window to the newest loaded
     * snapshot (refreshed at most every 30 s) so we still get the latest known positions.
     */
    @Volatile private var snapshotTime: Instant? = null
    @Volatile private var snapshotCheckedAt = 0L
    @Volatile private var snapshotId: Long? = null

    /** Time of the newest loaded SIRI snapshot = how fresh the whole feed is. */
    val feedTime: Instant? get() = snapshotTime

    private suspend fun refreshSnapshot(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - snapshotCheckedAt < 30_000) return
        runCatching { api.snapshots(limit = 5).firstOrNull { it.etlStatus == "loaded" } }.getOrNull()?.let { s ->
            snapshotId = s.id
            snapshotTime = parseSnapshotTime(s.snapshotId)
        }
        snapshotCheckedAt = System.currentTimeMillis()
    }

    private suspend fun windowStart(now: Instant): Instant {
        refreshSnapshot()
        val ref = snapshotTime?.let { minOf(it, now) } ?: now
        return ref - WINDOW
    }

    suspend fun busesInBounds(b: BBox, limit: Int = 1500): List<LiveBus> {
        val now = Instant.now()
        val rows = api.vehicleLocations(
            recordedFrom = TimeUtil.toApi(windowStart(now)),
            // Upper bound filters out bogus rows dated 2038 that exist in the DB.
            recordedTo = TimeUtil.toApi(now + Duration.ofMinutes(1)),
            latMin = b.south, latMax = b.north, lonMin = b.west, lonMax = b.east,
            limit = limit,
        )
        return toBuses(rows)
    }

    /** Id of the newest fully loaded SIRI snapshot (null if unknown). */
    suspend fun latestSnapshotId(): Long? {
        refreshSnapshot(force = true)
        return snapshotId
    }

    suspend fun busesForLine(lineRef: Long): List<LiveBus> {
        val now = Instant.now()
        val rows = api.vehicleLocations(
            recordedFrom = TimeUtil.toApi(windowStart(now)),
            recordedTo = TimeUtil.toApi(now + Duration.ofMinutes(1)),
            lineRef = lineRef,
            limit = 500,
        )
        return toBuses(rows)
    }

    private suspend fun toBuses(rows: List<VehicleLocationDto>): List<LiveBus> {
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
                    tripNumber = r.journeyRef?.substringAfterLast('-')?.takeIf { it.isNotBlank() },
                )
            }
            .groupBy { it.vehicleRef }
            .mapNotNull { (_, points) -> points.maxByOrNull { it.recordedAt } }
            .toList()
            .let { list ->
                // Drop buses that stopped reporting, relative to the newest data (the feed itself may lag).
                val newest = list.maxOfOrNull { it.recordedAt } ?: return@let list
                list.filter { newest.epochSecond - it.recordedAt.epochSecond <= DROP_AFTER_S }
            }
    }

    private fun parseSnapshotTime(id: String?): Instant? = runCatching {
        val p = id!!.split('/').map { it.toInt() }
        java.time.LocalDateTime.of(p[0], p[1], p[2], p[3], p[4]).toInstant(java.time.ZoneOffset.UTC).plusSeconds(60)
    }.getOrNull()

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(6)
        /** Fade markers older than this (seconds). */
        const val FADE_AFTER_S = 180L
        /** Drop markers older than this (seconds). */
        const val DROP_AFTER_S = 420L
    }
}
