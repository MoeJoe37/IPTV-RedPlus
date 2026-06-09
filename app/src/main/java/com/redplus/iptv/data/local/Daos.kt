package com.redplus.iptv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Upsert suspend fun upsert(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE accountKey = :accountKey AND contentType = :type AND itemId = :itemId") suspend fun delete(accountKey: String, type: String, itemId: String)
    @Query("SELECT COUNT(*) FROM favorites WHERE accountKey = :accountKey AND contentType = :type AND itemId = :itemId") fun isFavorite(accountKey: String, type: String, itemId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM favorites WHERE accountKey = :accountKey AND contentType = :type AND itemId = :itemId") suspend fun isFavoriteNow(accountKey: String, type: String, itemId: String): Int
    @Query("SELECT * FROM favorites WHERE accountKey = :accountKey ORDER BY addedAt DESC") fun observeAll(accountKey: String): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites WHERE accountKey = :accountKey AND contentType = :type ORDER BY addedAt DESC") fun observeByType(accountKey: String, type: String): Flow<List<FavoriteEntity>>
    @Query("DELETE FROM favorites WHERE accountKey = :accountKey") suspend fun clear(accountKey: String)
}

@Dao
interface WatchHistoryDao {
    @Upsert suspend fun upsert(history: WatchHistoryEntity)
    @Query("SELECT * FROM watch_history WHERE accountKey = :accountKey ORDER BY updatedAt DESC LIMIT :limit") fun observeRecent(accountKey: String, limit: Int = 100): Flow<List<WatchHistoryEntity>>
    @Query("SELECT * FROM watch_history WHERE accountKey = :accountKey AND contentType IN ('MOVIE','SERIES','EPISODE') AND watched = 0 ORDER BY updatedAt DESC LIMIT :limit") fun observeContinueWatching(accountKey: String, limit: Int = 20): Flow<List<WatchHistoryEntity>>
    @Query("SELECT * FROM watch_history WHERE accountKey = :accountKey AND contentType = :type AND itemId = :itemId LIMIT 1") suspend fun get(accountKey: String, type: String, itemId: String): WatchHistoryEntity?
    @Query("DELETE FROM watch_history WHERE accountKey = :accountKey") suspend fun clear(accountKey: String)
}

@Dao
interface RecentSearchDao {
    @Upsert suspend fun upsert(search: RecentSearchEntity)
    @Query("SELECT * FROM recent_searches WHERE accountKey = :accountKey ORDER BY updatedAt DESC LIMIT 15") fun observe(accountKey: String): Flow<List<RecentSearchEntity>>
    @Query("DELETE FROM recent_searches WHERE accountKey = :accountKey") suspend fun clear(accountKey: String)
}

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(cache: CacheEntity)
    @Query("SELECT * FROM api_cache WHERE cacheKey = :key LIMIT 1") suspend fun get(key: String): CacheEntity?
    @Query("DELETE FROM api_cache") suspend fun clear()
}
