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
    val showTimeSkipButtons: Boolean = false,
    val forwardSkipSeconds: Int = 5,
    val rewindSkipSeconds: Int = 5,
    val networkRouteMode: NetworkRouteMode = NetworkRouteMode.DIRECT,
    val autoNetworkSwitch: Boolean = false,
    val manualProxyHost: String = "",
    val manualProxyPort: Int = 0,
    val hiddenCategoryKeys: Set<String> = emptySet(),
    val categoryGroupMap: Map<String, String> = emptyMap()
)

enum class ContentLoadingStrategy(val label: String) {
    RUNTIME("Load data on runtime"),
    ALL_AT_ONCE("Load all data at one time")
}

enum class NetworkRouteMode(val label: String) {
    DIRECT("Direct connection"),
    AUTO_DNS("Auto fastest DNS"),
    CLOUDFLARE_1111("Cloudflare 1.1.1.1 DNS"),
    GOOGLE_DNS("Google Public DNS"),
    QUAD9_DNS("Quad9 DNS"),
    ADGUARD_DNS("AdGuard DNS"),
    MANUAL_HTTP_PROXY("Manual HTTP proxy"),
    MANUAL_SOCKS_PROXY("Manual SOCKS proxy")
}
