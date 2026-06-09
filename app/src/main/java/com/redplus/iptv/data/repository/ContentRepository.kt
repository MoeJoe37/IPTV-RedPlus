package com.redplus.iptv.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.redplus.iptv.data.local.CacheDao
import com.redplus.iptv.data.local.CacheEntity
import com.redplus.iptv.data.model.CategoryItem
import com.redplus.iptv.data.model.ContentItem
import com.redplus.iptv.data.model.ContentType
import com.redplus.iptv.data.model.Session
import com.redplus.iptv.data.model.toCategoryItem
import com.redplus.iptv.data.model.toContentItem
import com.redplus.iptv.data.remote.EpgResponse
import com.redplus.iptv.data.remote.EpisodeDto
import com.redplus.iptv.data.remote.SeriesInfoResponse
import com.redplus.iptv.data.remote.VodInfoResponse
import com.redplus.iptv.data.remote.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentRepository(private val client: XtreamClient, private val cacheDao: CacheDao, private val gson: Gson) {
    suspend fun liveCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "live_categories", force) { client.liveCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun liveStreams(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "live_streams_${categoryId ?: "all"}", force) { client.liveStreams(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun eventStreams(session: Session, force: Boolean = false): List<ContentItem> {
        val categories = liveCategories(session, force)
        val eventCategoryIds = categories.filter { isEventText(it.name) }.map { it.id }.toSet()
        return liveStreams(session, null, force).filter { it.categoryId in eventCategoryIds || isEventText(it.title) }.map { it.copy(type = ContentType.EVENT) }
    }
    suspend fun vodCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "vod_categories", force) { client.vodCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun vodStreams(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "vod_streams_${categoryId ?: "all"}", force) { client.vodStreams(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun vodInfo(session: Session, vodId: String): VodInfoResponse = cached(session, "vod_info_$vodId", false) { client.vodInfo(session.serverUrl, session.username, session.password, vodId) }
    suspend fun seriesCategories(session: Session, force: Boolean = false): List<CategoryItem> = cached(session, "series_categories", force) { client.seriesCategories(session.serverUrl, session.username, session.password).map { it.toCategoryItem() } }
    suspend fun series(session: Session, categoryId: String? = null, force: Boolean = false): List<ContentItem> = cached(session, "series_${categoryId ?: "all"}", force) { client.series(session.serverUrl, session.username, session.password, categoryId).map { it.toContentItem() } }
    suspend fun seriesInfo(session: Session, seriesId: String): SeriesInfoResponse = cached(session, "series_info_$seriesId", false) { client.seriesInfo(session.serverUrl, session.username, session.password, seriesId) }
    suspend fun epg(session: Session, streamId: String, limit: Int = 4): EpgResponse = cached(session, "epg_${streamId}_$limit", false) { client.shortEpg(session.serverUrl, session.username, session.password, streamId, limit) }

    fun buildStreamUrl(session: Session, item: ContentItem): String = when (item.type) {
        ContentType.LIVE, ContentType.EVENT -> client.buildLiveUrl(session.serverUrl, session.username, session.password, item.id)
        ContentType.MOVIE -> client.buildMovieUrl(session.serverUrl, session.username, session.password, item.id, item.streamExtension)
        ContentType.SERIES, ContentType.EPISODE -> client.buildSeriesUrl(session.serverUrl, session.username, session.password, item.id, item.streamExtension)
    }

    suspend fun episodeItems(seriesInfo: SeriesInfoResponse): List<ContentItem> = withContext(Dispatchers.Default) {
        seriesInfo.episodes.orEmpty().toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE }).flatMap { (season, episodes) -> episodes.map { it.toContentItem(season) } }
    }

    private fun EpisodeDto.toContentItem(season: String): ContentItem = ContentItem(
        id = id.orEmpty(), type = ContentType.EPISODE, title = "S${season.padStart(2, '0')}E${episodeNum ?: 0} • ${title.orEmpty().ifBlank { "Episode" }}",
        image = info?.image, plot = info?.plot, rating = info?.rating, streamExtension = containerExtension ?: "mp4"
    )
    private fun isEventText(value: String): Boolean = listOf("ppv", "event", "events", "live event", "sports event", "matchday", "fight night", "ufc", "boxing").any { it in value.lowercase() }

    private suspend inline fun <reified T> cached(session: Session, keyPart: String, force: Boolean, crossinline fetch: suspend () -> T): T {
        val key = "${session.accountKey}::$keyPart"
        val maxAgeMs = 15 * 60 * 1000L
        val cached = cacheDao.get(key)
        val fresh = cached != null && System.currentTimeMillis() - cached.updatedAt < maxAgeMs
        if (!force && cached != null && fresh) return gson.fromJson(cached.json, object : TypeToken<T>() {}.type)
        return try {
            val remote = fetch()
            cacheDao.put(CacheEntity(key, gson.toJson(remote)))
            remote
        } catch (e: Exception) {
            if (cached != null) gson.fromJson(cached.json, object : TypeToken<T>() {}.type) else throw e
        }
    }
}
