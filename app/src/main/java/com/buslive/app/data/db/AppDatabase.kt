package com.buslive.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        FavoriteEntity::class,
        RecentSearchEntity::class,
        RecentTripEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao
}
