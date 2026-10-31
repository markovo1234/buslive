package com.buslive.app.data.repo

import android.util.Log
import com.buslive.app.data.api.GtfsRouteDto
import com.buslive.app.data.api.StrideApi
import com.buslive.app.data.db.RouteDao
import com.buslive.app.data.db.RouteEntity
import com.buslive.app.data.db.StopDao
import com.buslive.app.data.db.StopEntity
import com.buslive.app.data.prefs.SettingsRepository
import com.buslive.app.domain.model.BBox
import com.buslive.app.domain.model.GeoPoint
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.Stop
import com.buslive.app.domain.util.Geo
import com.buslive.app.domain.util.TimeUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops + lines cached in Room. Refreshed daily by [com.buslive.app.service.StaticSyncWorker].
 * Works fully offline once synced.
 */
@Singleton
class StaticDataRepository @Inject constructor(
    private val api: StrideApi,
    private val stopDao: StopDao,
    private val routeDao: RouteDao,
    private val settings: SettingsRepository,
) {
    private val syncMutex = Mutex()
    private val linesMutex = Mutex()
    @Volatile private var linesCache: Map<Long, Line>? = null

    suspend fun hasData(): Boolean = stopDao.count() > 0 && routeDao.count() > 0

    /** True when cache is missing or from an older GTFS date. */
    suspend fun needsSync(): Boolean {
        if (!hasData()) return true
        val s = settings.current()
        return s.lastSyncDate != TimeUtil.today().toString() &&
            System.currentTimeMillis() - s.lastSyncAt > 3 * 60 * 60 * 1000L
    }

    /**
     * Downloads today's stops and routes (falls back to yesterday if today's GTFS is not loaded yet).
     * Pages through the API at 10k rows per call (server max is 15k).
     */
    suspend fun sync(force: Boolean = false) = syncMutex.withLock {
        if (!force && !needsSync()) return@withLock
        val date = pickGtfsDate()
        val ds = date.toString()

        val stops = ArrayList<StopEntity>(27_000)
        var offset = 0
        val page = 10_000
        while (true) {
            val rows = api.stops(dateFrom = ds, dateTo = ds, limit = page, offset = offset)
            rows.forEach { r ->
                if (r.lat != null && r.lon != null && r.lat != 0.0) {
                    stops += StopEntity(
                        code = r.code, gtfsId = r.id, name = r.name.orEmpty().trim(),
                        city = r.city.orEmpty().trim(), lat = r.lat, lon = r.lon,
                    )
                }
            }
            if (rows.size < page) break
            offset += page
            if (offset > 200_000) break // safety
        }
        val dedupedStops = stops.distinctBy { it.code }

        val routes = ArrayList<RouteEntity>(6_000)
        offset = 0
        while (true) {
            val rows = api.routes(dateFrom = ds, dateTo = ds, limit = page, offset = offset)
            rows.mapTo(routes) { it.toEntity() }
            if (rows.size < page) break
            offset += page
        }
        if (dedupedStops.isEmpty() || routes.isEmpty()) error("GTFS data empty for $ds")

        stopDao.replaceAll(dedupedStops)
        routeDao.replaceAll(routes)
        linesMutex.withLock { linesCache = null }
        settings.setSynced(ds, System.currentTimeMillis())
        Log.i(TAG, "Synced ${dedupedStops.size} stops, ${routes.size} routes for $ds")
    }

    private suspend fun pickGtfsDate(): LocalDate {
        val today = TimeUtil.today()
        val count = runCatching { api.stopsCount(today.toString(), today.toString()) }.getOrDefault(0)
        return if (count > 0) today else today.minusDays(1)
    }

    // ---- Stops ----

    suspend fun stop(code: Int): Stop? = stopDao.byCode(code)?.toDomain()

    suspend fun stops(codes: List<Int>): List<Stop> = stopDao.byCodes(codes).map { it.toDomain() }

    suspend fun stopsInBounds(b: BBox, limit: Int = 3000): List<Stop> =
        stopDao.inBounds(b.south, b.west, b.north, b.east, limit).map { it.toDomain() }

    /** Nearest stops by straight-line distance, searching outward until enough are found. */
    suspend fun nearestStops(p: GeoPoint, count: Int, maxRadiusM: Double = 3000.0): List<Pair<Stop, Double>> {
        var radius = 400.0
        while (true) {
            val found = stopsInBounds(BBox.around(p, radius), limit = 2000)
                .map { it to Geo.distanceM(p, it.point) }
                .filter { it.second <= radius }
                .sortedBy { it.second }
            if (found.size >= count || radius >= maxRadiusM) return found.take(count)
            radius *= 2
        }
    }

    suspend fun searchStops(q: String, limit: Int = 20): List<Stop> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val byCode = if (query.all { it.isDigit() }) stopDao.searchByCode(query, limit) else emptyList()
        val byName = if (query.length >= 2) stopDao.searchByName(query, limit) else emptyList()
        return (byCode + byName).distinctBy { it.code }.take(limit).map { it.toDomain() }
    }

    // ---- Lines ----

    /** In-memory map lineRef → Line (a few thousand rows, loaded once per sync). */
    suspend fun linesByRef(): Map<Long, Line> {
        linesCache?.let { return it }
        return linesMutex.withLock {
            linesCache ?: routeDao.all().associate { it.lineRef to it.toDomain() }.also { linesCache = it }
        }
    }

    suspend fun line(lineRef: Long): Line? = linesByRef()[lineRef]

    suspend fun searchLines(q: String, limit: Int = 30): List<Line> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        return routeDao.searchByNumber(query, limit).map { it.toDomain() }
    }

    /** Other directions / alternatives of the same public line (same operator + MOT line id). */
    suspend fun siblings(line: Line): List<Line> =
        routeDao.siblings(line.mkt, line.operatorRef).map { it.toDomain() }.ifEmpty { listOf(line) }

    /** Route of a line for another date (route ids change per day). */
    suspend fun lineForDate(lineRef: Long, date: LocalDate): Line? {
        val cached = line(lineRef)
        if (cached != null && cached.date == date) return cached
        val ds = date.toString()
        return api.routes(dateFrom = ds, dateTo = ds, lineRefs = lineRef.toString(), limit = 5)
            .firstOrNull()?.toEntity()?.toDomain()
    }

    companion object {
        private const val TAG = "StaticData"
    }
}

private fun GtfsRouteDto.toEntity() = RouteEntity(
    lineRef = lineRef,
    gtfsRouteId = id,
    date = date,
    operatorRef = operatorRef,
    shortName = shortName.orEmpty(),
    longName = longName.orEmpty(),
    mkt = mkt.orEmpty(),
    direction = direction.orEmpty(),
    alternative = alternative.orEmpty(),
    agencyName = agencyName.orEmpty(),
    routeType = routeType.orEmpty(),
)

fun StopEntity.toDomain() = Stop(code = code, name = name, city = city, lat = lat, lon = lon)

fun RouteEntity.toDomain() = Line(
    lineRef = lineRef,
    routeId = gtfsRouteId,
    date = runCatching { LocalDate.parse(date) }.getOrDefault(TimeUtil.today()),
    operatorRef = operatorRef,
    shortName = shortName,
    longName = longName,
    mkt = mkt,
    direction = direction,
    alternative = alternative,
    agencyName = agencyName,
    routeType = routeType,
)
