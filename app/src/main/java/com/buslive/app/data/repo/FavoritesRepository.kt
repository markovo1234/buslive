package com.buslive.app.data.repo

import com.buslive.app.data.db.FavoriteDao
import com.buslive.app.data.db.FavoriteEntity
import com.buslive.app.data.db.RecentDao
import com.buslive.app.data.db.RecentSearchEntity
import com.buslive.app.data.db.RecentTripEntity
import com.buslive.app.domain.model.Favorite
import com.buslive.app.domain.model.FavoriteType
import com.buslive.app.domain.model.Line
import com.buslive.app.domain.model.Place
import com.buslive.app.domain.model.PlaceKind
import com.buslive.app.domain.model.Stop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val recentDao: RecentDao,
) {
    val favorites: Flow<List<Favorite>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val favoriteStopCodes: Flow<Set<Int>> = favorites.map { list ->
        list.filter { it.type == FavoriteType.STOP }.mapNotNull { it.key.toIntOrNull() }.toSet()
    }

    /** Favorite lines are stored by MOT line id (mkt) + operator so both directions count. */
    val favoriteLineKeys: Flow<Set<String>> = favorites.map { list ->
        list.filter { it.type == FavoriteType.LINE }.map { it.key }.toSet()
    }

    suspend fun toggleStop(stop: Stop, isFavorite: Boolean) {
        if (isFavorite) dao.delete(FavoriteType.STOP.name, stop.code.toString())
        else dao.upsert(
            FavoriteEntity(
                type = FavoriteType.STOP.name, key = stop.code.toString(), title = stop.name,
                subtitle = stop.city, lat = stop.lat, lon = stop.lon,
            )
        )
    }

    suspend fun toggleLine(line: Line, isFavorite: Boolean) {
        val key = lineKey(line)
        if (isFavorite) dao.delete(FavoriteType.LINE.name, key)
        else dao.upsert(
            FavoriteEntity(
                type = FavoriteType.LINE.name, key = key, title = line.shortName,
                subtitle = "${line.lineRef}|${line.agencyName}|${line.destination}",
            )
        )
    }

    suspend fun savePlace(place: Place, kind: PlaceKind, label: String? = null) {
        if (kind != PlaceKind.CUSTOM) dao.deletePlaceKind(kind.name)
        dao.upsert(
            FavoriteEntity(
                type = FavoriteType.PLACE.name, key = "%.5f,%.5f".format(java.util.Locale.ROOT, place.lat, place.lon),
                title = label ?: place.name, subtitle = place.address, lat = place.lat, lon = place.lon,
                placeKind = kind.name,
            )
        )
    }

    suspend fun remove(f: Favorite) = dao.delete(f.type.name, f.key)

    // ---- Recents ----

    val recentSearches = recentDao.observeSearches()
    val recentTrips = recentDao.observeTrips()

    suspend fun addRecentSearch(type: String, key: String, title: String, subtitle: String?, lat: Double?, lon: Double?) =
        recentDao.upsertSearch(RecentSearchEntity(type = type, key = key, title = title, subtitle = subtitle, lat = lat, lon = lon))

    suspend fun clearRecentSearches() = recentDao.clearSearches()

    suspend fun addRecentTrip(fromName: String, from: Place?, to: Place) = recentDao.upsertTrip(
        RecentTripEntity(
            fromName = fromName, fromLat = from?.lat, fromLon = from?.lon,
            toName = to.name, toLat = to.lat, toLon = to.lon,
        )
    )

    companion object {
        fun lineKey(line: Line) = "${line.operatorRef}:${line.mkt.ifEmpty { line.shortName }}"
    }
}

private fun FavoriteEntity.toDomain() = Favorite(
    id = id,
    type = runCatching { FavoriteType.valueOf(type) }.getOrDefault(FavoriteType.STOP),
    key = key,
    title = title,
    subtitle = subtitle,
    lat = lat,
    lon = lon,
    placeKind = placeKind?.let { runCatching { PlaceKind.valueOf(it) }.getOrNull() },
)
