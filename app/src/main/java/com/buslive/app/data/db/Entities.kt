package com.buslive.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bus stop, deduped by public stop code (the API repeats codes with different ids). */
@Entity(tableName = "stops", indices = [Index("lat"), Index("name")])
data class StopEntity(
    @PrimaryKey val code: Int,
    val gtfsId: Long,
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double,
)

/** One GTFS route (a line in one direction/alternative) for the cached date. */
@Entity(tableName = "routes", indices = [Index("shortName"), Index("mkt")])
data class RouteEntity(
    @PrimaryKey val lineRef: Long,
    val gtfsRouteId: Long,
    val date: String,
    val operatorRef: Int,
    val shortName: String,
    val longName: String,
    val mkt: String,
    val direction: String,
    val alternative: String,
    val agencyName: String,
    val routeType: String,
)

/** A saved stop / line / place. [key] is stop code, line_ref, or "lat,lon" for places. */
@Entity(tableName = "favorites", indices = [Index(value = ["type", "key"], unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** For places: HOME, WORK or CUSTOM. */
    val placeKind: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Recent search pick (stop / line / place). */
@Entity(tableName = "recent_searches", indices = [Index(value = ["type", "key"], unique = true)])
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val usedAt: Long = System.currentTimeMillis(),
)

/** Recent A→B trip searches. */
@Entity(tableName = "recent_trips", indices = [Index(value = ["toLat", "toLon", "fromLat", "fromLon"], unique = true)])
data class RecentTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromName: String,
    val fromLat: Double?,
    val fromLon: Double?,
    val toName: String,
    val toLat: Double,
    val toLon: Double,
    val usedAt: Long = System.currentTimeMillis(),
)
