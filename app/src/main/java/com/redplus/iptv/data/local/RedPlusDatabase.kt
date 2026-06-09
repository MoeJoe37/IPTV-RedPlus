package com.redplus.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteEntity::class, WatchHistoryEntity::class, RecentSearchEntity::class, CacheEntity::class], version = 1, exportSchema = false)
abstract class RedPlusDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun cacheDao(): CacheDao
    companion object {
        @Volatile private var INSTANCE: RedPlusDatabase? = null
        fun get(context: Context): RedPlusDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, RedPlusDatabase::class.java, "redplus.db").fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
