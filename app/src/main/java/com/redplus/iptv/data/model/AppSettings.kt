package com.redplus.iptv.data.model

data class AppSettings(
    val subtitlesEnabled: Boolean = true,
    val preferredSubtitleLanguage: String = "auto",
    val externalSubtitleUrl: String = "",
    val forceLandscapeApp: Boolean = false,
    val streamUrlRedirectionCheck: Boolean = false,
    val liveTvEpg: Boolean = true,
    val useExtensionInStreamUrl: Boolean = true,
    val useM3uFormatInStreamUrl: Boolean = true,
    val useRuntimeMovies: Boolean = true,
    val useRuntimeSeries: Boolean = true,
    val useUtf8Decode: Boolean = true,
    val contentLoadingStrategy: ContentLoadingStrategy = ContentLoadingStrategy.RUNTIME,
    val useExternalLivePlayer: Boolean = false,
    val useExternalVodPlayer: Boolean = false,
    val externalXmlTvUrl: String = "",
    val tvViewMode: Boolean = false,
    val hiddenCategoryKeys: Set<String> = emptySet(),
    val categoryGroupMap: Map<String, String> = emptyMap()
)

enum class ContentLoadingStrategy(val label: String) {
    RUNTIME("Load data on runtime"),
    ALL_AT_ONCE("Load all data at one time")
}
