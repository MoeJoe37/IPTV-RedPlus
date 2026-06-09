package com.redplus.iptv.data.model

import com.redplus.iptv.data.remote.CategoryDto
import com.redplus.iptv.data.remote.LiveStreamDto
import com.redplus.iptv.data.remote.SeriesDto
import com.redplus.iptv.data.remote.UserInfoDto
import com.redplus.iptv.data.remote.VodStreamDto

data class Session(
    val serverUrl: String,
    val username: String,
    val password: String,
    val status: String = "Unknown",
    val expDate: String? = null,
    val activeConnections: String? = null,
    val maxConnections: String? = null
) { val accountKey: String = "${serverUrl.trimEnd('/')}::$username" }

data class DashboardAccount(val status: String, val expiry: String?, val activeConnections: String?, val maxConnections: String?)

enum class ContentType { LIVE, EVENT, MOVIE, SERIES, EPISODE }

data class ContentItem(
    val id: String,
    val type: ContentType,
    val title: String,
    val image: String? = null,
    val categoryId: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val streamExtension: String? = null,
    val channelNumber: Int? = null
)

data class CategoryItem(val id: String, val name: String)

fun UserInfoDto.toDashboardAccount(): DashboardAccount = DashboardAccount(
    status = status ?: if (auth == 1) "Active" else "Unknown",
    expiry = expDate,
    activeConnections = activeCons,
    maxConnections = maxConnections
)

fun CategoryDto.toCategoryItem(): CategoryItem = CategoryItem(id = categoryId.orEmpty(), name = categoryName.orEmpty().ifBlank { "Uncategorized" })

fun LiveStreamDto.toContentItem(type: ContentType = ContentType.LIVE): ContentItem = ContentItem(
    id = streamId?.toString().orEmpty(), type = type, title = name.orEmpty().ifBlank { "Untitled Channel" },
    image = streamIcon, categoryId = categoryId, channelNumber = num
)

fun VodStreamDto.toContentItem(): ContentItem = ContentItem(
    id = streamId?.toString().orEmpty(), type = ContentType.MOVIE, title = name.orEmpty().ifBlank { "Untitled Movie" },
    image = streamIcon, categoryId = categoryId, year = year, rating = rating, streamExtension = containerExtension ?: "mp4"
)

fun SeriesDto.toContentItem(): ContentItem = ContentItem(
    id = seriesId?.toString().orEmpty(), type = ContentType.SERIES, title = name.orEmpty().ifBlank { "Untitled Series" },
    image = cover, categoryId = categoryId, year = releaseDate?.take(4), rating = rating, genre = genre, plot = plot
)
