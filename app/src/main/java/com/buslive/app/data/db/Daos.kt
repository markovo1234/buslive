package com.buslive.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("DELETE FROM stops")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(stops: List<StopEntity>) {
        deleteAll()
        stops.chunked(2000).forEach { insertAll(it) }
    }

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun count(): Int

    @Query("SELECT * FROM stops WHERE code = :code")
    suspend fun byCode(code: Int): StopEntity?

    @Query("SELECT * FROM stops WHERE code IN (:codes)")
    suspend fun byCodes(codes: List<Int>): List<StopEntity>

    @Query(
        "SELECT * FROM stops WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east LIMIT :limit"
    )
    suspend fun inBounds(south: Double, west: Double, north: Double, east: Double, limit: Int): List<StopEntity>

    @Query(
        "SELECT * FROM stops WHERE name LIKE '%' || :q || '%' OR city LIKE :q || '%' " +
            "ORDER BY CASE WHEN name LIKE :q || '%' THEN 0 ELSE 1 END, length(name) LIMIT :limit"
    )
    suspend fun searchByName(q: String, limit: Int): List<StopEntity>

    @Query("SELECT * FROM stops WHERE CAST(code AS TEXT) LIKE :prefix || '%' ORDER BY code LIMIT :limit")
    suspend fun searchByCode(prefix: String, limit: Int): List<StopEntity>
}

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(routes: List<RouteEntity>) {
        deleteAll()
        routes.chunked(2000).forEach { insertAll(it) }
    }

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun count(): Int

    @Query("SELECT * FROM routes")
    suspend fun all(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE lineRef = :lineRef")
    suspend fun byLineRef(lineRef: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE lineRef IN (:lineRefs)")
    suspend fun byLineRefs(lineRefs: List<Long>): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE mkt = :mkt AND operatorRef = :operatorRef ORDER BY direction, alternative")
    suspend fun siblings(mkt: String, operatorRef: Int): List<RouteEntity>

    @Query(
        "SELECT * FROM routes WHERE shortName = :q OR shortName LIKE :q || '%' " +
            "ORDER BY CASE WHEN shortName = :q THEN 0 ELSE 1 END, length(shortName), shortName, operatorRef, direction LIMIT :limit"
    )
    suspend fun searchByNumber(q: String, limit: Int): List<RouteEntity>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY CASE placeKind WHEN 'HOME' THEN 0 WHEN 'WORK' THEN 1 ELSE 2 END, createdAt")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE type = :type AND `key` = :key")
    suspend fun delete(type: String, key: String)

    @Query("DELETE FROM favorites WHERE type = 'PLACE' AND placeKind = :kind")
    suspend fun deletePlaceKind(kind: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_searches ORDER BY usedAt DESC LIMIT 12")
    fun observeSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clearSearches()

    @Query("SELECT * FROM recent_trips ORDER BY usedAt DESC LIMIT 8")
    fun observeTrips(): Flow<List<RecentTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(entity: RecentTripEntity)

    @Query("DELETE FROM recent_trips WHERE toLat = :toLat AND toLon = :toLon AND fromLat IS :fromLat AND fromLon IS :fromLon")
    suspend fun deleteTrip(fromLat: Double?, fromLon: Double?, toLat: Double, toLon: Double)

    @Transaction
    suspend fun upsertTrip(entity: RecentTripEntity) {
        deleteTrip(entity.fromLat, entity.fromLon, entity.toLat, entity.toLon)
        insertTrip(entity)
    }
}
