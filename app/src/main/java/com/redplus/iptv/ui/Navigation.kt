package com.redplus.iptv.ui

import com.redplus.iptv.data.model.ContentItem

object Routes {
    const val Dashboard = "dashboard"
    const val Live = "live"
    const val Events = "events"
    const val Movies = "movies"
    const val MovieDetails = "movieDetails"
    const val Series = "series"
    const val SeriesDetails = "seriesDetails"
    const val Favorites = "favorites"
    const val History = "history"
    const val Search = "search"
    const val Settings = "settings"
    const val Player = "player"
    const val Epg = "epg"
}

object SharedSelection {
    var item: ContentItem? = null
}
