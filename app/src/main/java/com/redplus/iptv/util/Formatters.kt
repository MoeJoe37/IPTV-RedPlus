package com.redplus.iptv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun xtreamExpiryToText(value: String?): String {
    if (value.isNullOrBlank() || value == "0") return "Not available"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value.toLong() * 1000L))
    }.getOrDefault(value)
}

fun msToTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

fun unixToLocalTime(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) return "--:--"
    return runCatching {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(seconds * 1000L))
    }.getOrDefault("--:--")
}

fun String.containsNormalized(query: String): Boolean = lowercase(Locale.getDefault()).contains(query.trim().lowercase(Locale.getDefault()))
