package com.redplus.iptv.data.repository

import com.redplus.iptv.data.local.CacheDao
import com.redplus.iptv.data.local.FavoriteDao
import com.redplus.iptv.data.local.FavoriteEntity
import com.redplus.iptv.data.local.RecentSearchDao
import com.redplus.iptv.data.local.RecentSearchEntity
import com.redplus.iptv.data.local.WatchHistoryDao
import com.redplus.iptv.data.local.WatchHistoryEntity
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val favoriteDao: FavoriteDao, private val watchHistoryDao: WatchHistoryDao, private val recentSearchDao: RecentSearchDao, private val cacheDao: CacheDao) {
    fun favorites(accountKey: String): Flow<List<FavoriteEntity>> = favoriteDao.observeAll(accountKey)
    fun favoritesByType(accountKey: String, type: ContentType): Flow<List<FavoriteEntity>> = favoriteDao.observeByType(accountKey, type.name)
    fun recent(accountKey: String): Flow<List<WatchHistoryEntity>> = watchHistoryDao.observeRecent(accountKey)
    fun continueWatching(accountKey: String): Flow<List<WatchHistoryEntity>> = watchHistoryDao.observeContinueWatching(accountKey)
    fun recentSearches(accountKey: String): Flow<List<RecentSearchEntity>> = recentSearchDao.observe(accountKey)
    fun isFavorite(accountKey: String, item: ContentItem): Flow<Boolean> = favoriteDao.isFavorite(accountKey, item.type.name, item.id).map { it > 0 }

    suspend fun toggleFavorite(accountKey: String, item: ContentItem) {
        if (favoriteDao.isFavoriteNow(accountKey, item.type.name, item.id) > 0) favoriteDao.delete(accountKey, item.type.name, item.id) else favoriteDao.upsert(
            FavoriteEntity(accountKey, item.type.name, item.id, item.title, item.image, item.categoryId, item.streamExtension, listOfNotNull(item.year, item.rating, item.genre).joinToString(" • ").ifBlank { null })
        )
    }

    suspend fun saveWatchProgress(accountKey: String, item: ContentItem, positionMs: Long, durationMs: Long) {
        val watched = durationMs > 0 && positionMs > durationMs * 0.9
        watchHistoryDao.upsert(WatchHistoryEntity(accountKey, item.type.name, item.id, item.title, item.image, item.categoryId, item.streamExtension, if (watched) 0 else positionMs, durationMs, watched))
    }
    suspend fun historyFor(accountKey: String, item: ContentItem): WatchHistoryEntity? = watchHistoryDao.get(accountKey, item.type.name, item.id)
    suspend fun saveSearch(accountKey: String, query: String) { if (query.isNotBlank()) recentSearchDao.upsert(RecentSearchEntity(accountKey, query.trim())) }
    suspend fun clearFavorites(accountKey: String) = favoriteDao.clear(accountKey)
    suspend fun clearHistory(accountKey: String) = watchHistoryDao.clear(accountKey)
    suspend fun clearSearches(accountKey: String) = recentSearchDao.clear(accountKey)
    suspend fun clearCache() = cacheDao.clear()
}
