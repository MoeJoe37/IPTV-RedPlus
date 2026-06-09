package com.redplus.iptv.data.remote

import android.util.Base64
import com.google.gson.annotations.SerializedName

data class XtreamLoginResponse(@SerializedName("user_info") val userInfo: UserInfoDto? = null, @SerializedName("server_info") val serverInfo: ServerInfoDto? = null)

data class UserInfoDto(
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("active_cons") val activeCons: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("message") val message: String? = null
)

data class ServerInfoDto(@SerializedName("url") val url: String? = null, @SerializedName("port") val port: String? = null, @SerializedName("https_port") val httpsPort: String? = null, @SerializedName("server_protocol") val protocol: String? = null, @SerializedName("timezone") val timezone: String? = null, @SerializedName("timestamp_now") val timestampNow: Long? = null)

data class CategoryDto(@SerializedName("category_id") val categoryId: String? = null, @SerializedName("category_name") val categoryName: String? = null, @SerializedName("parent_id") val parentId: Int? = null)

data class LiveStreamDto(
    @SerializedName("num") val num: Int? = null, @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null, @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null, @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null, @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("custom_sid") val customSid: String? = null, @SerializedName("tv_archive") val tvArchive: Int? = null,
    @SerializedName("direct_source") val directSource: String? = null, @SerializedName("tv_archive_duration") val tvArchiveDuration: Int? = null
)

data class VodStreamDto(
    @SerializedName("num") val num: Int? = null, @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null, @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null, @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val ratingFiveBased: Double? = null, @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null, @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("year") val year: String? = null
)

data class VodInfoResponse(@SerializedName("info") val info: VodInfoDto? = null, @SerializedName("movie_data") val movieData: VodMovieDataDto? = null)

data class VodInfoDto(@SerializedName("name") val name: String? = null, @SerializedName("movie_image") val movieImage: String? = null, @SerializedName("backdrop_path") val backdropPath: Any? = null, @SerializedName("plot") val plot: String? = null, @SerializedName("releasedate") val releaseDate: String? = null, @SerializedName("duration") val duration: String? = null, @SerializedName("genre") val genre: String? = null, @SerializedName("cast") val cast: String? = null, @SerializedName("rating") val rating: String? = null)

data class VodMovieDataDto(@SerializedName("stream_id") val streamId: Int? = null, @SerializedName("name") val name: String? = null, @SerializedName("added") val added: String? = null, @SerializedName("category_id") val categoryId: String? = null, @SerializedName("container_extension") val containerExtension: String? = null)

data class SeriesDto(@SerializedName("num") val num: Int? = null, @SerializedName("name") val name: String? = null, @SerializedName("series_id") val seriesId: Int? = null, @SerializedName("cover") val cover: String? = null, @SerializedName("plot") val plot: String? = null, @SerializedName("cast") val cast: String? = null, @SerializedName("director") val director: String? = null, @SerializedName("genre") val genre: String? = null, @SerializedName("releaseDate") val releaseDate: String? = null, @SerializedName("last_modified") val lastModified: String? = null, @SerializedName("rating") val rating: String? = null, @SerializedName("category_id") val categoryId: String? = null)

data class SeriesInfoResponse(@SerializedName("seasons") val seasons: List<SeasonDto>? = null, @SerializedName("info") val info: SeriesInfoDto? = null, @SerializedName("episodes") val episodes: Map<String, List<EpisodeDto>>? = null)

data class SeriesInfoDto(@SerializedName("name") val name: String? = null, @SerializedName("cover") val cover: String? = null, @SerializedName("plot") val plot: String? = null, @SerializedName("genre") val genre: String? = null, @SerializedName("rating") val rating: String? = null, @SerializedName("releaseDate") val releaseDate: String? = null, @SerializedName("cast") val cast: String? = null, @SerializedName("backdrop_path") val backdropPath: Any? = null)

data class SeasonDto(@SerializedName("air_date") val airDate: String? = null, @SerializedName("episode_count") val episodeCount: Int? = null, @SerializedName("id") val id: Int? = null, @SerializedName("name") val name: String? = null, @SerializedName("overview") val overview: String? = null, @SerializedName("season_number") val seasonNumber: Int? = null, @SerializedName("cover") val cover: String? = null)

data class EpisodeDto(@SerializedName("id") val id: String? = null, @SerializedName("episode_num") val episodeNum: Int? = null, @SerializedName("title") val title: String? = null, @SerializedName("container_extension") val containerExtension: String? = null, @SerializedName("info") val info: EpisodeInfoDto? = null)

data class EpisodeInfoDto(@SerializedName("plot") val plot: String? = null, @SerializedName("movie_image") val image: String? = null, @SerializedName("duration_secs") val durationSeconds: Int? = null, @SerializedName("duration") val duration: String? = null, @SerializedName("rating") val rating: String? = null)

data class EpgResponse(@SerializedName("epg_listings") val listings: List<EpgListingDto>? = null)

data class EpgListingDto(@SerializedName("id") val id: String? = null, @SerializedName("title") val encodedTitle: String? = null, @SerializedName("description") val encodedDescription: String? = null, @SerializedName("start") val start: String? = null, @SerializedName("end") val end: String? = null, @SerializedName("start_timestamp") val startTimestamp: Long? = null, @SerializedName("stop_timestamp") val stopTimestamp: Long? = null) {
    val title: String get() = decodeBase64(encodedTitle).ifBlank { "Program" }
    val description: String get() = decodeBase64(encodedDescription)
    private fun decodeBase64(value: String?): String = runCatching { if (value.isNullOrBlank()) "" else String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(value.orEmpty())
}
