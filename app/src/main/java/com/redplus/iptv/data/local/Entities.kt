package com.redplus.iptv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites", primaryKeys = ["accountKey", "contentType", "itemId"])
data class FavoriteEntity(val accountKey: String, val contentType: String, val itemId: String, val title: String, val image: String?, val categoryId: String?, val streamExtension: String?, val meta: String?, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "watch_history", primaryKeys = ["accountKey", "contentType", "itemId"])
data class WatchHistoryEntity(val accountKey: String, val contentType: String, val itemId: String, val title: String, val image: String?, val categoryId: String?, val streamExtension: String?, val positionMs: Long, val durationMs: Long, val watched: Boolean, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "recent_searches", primaryKeys = ["accountKey", "query"])
data class RecentSearchEntity(val accountKey: String, val query: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "api_cache")
data class CacheEntity(@PrimaryKey val cacheKey: String, val json: String, val updatedAt: Long = System.currentTimeMillis())
