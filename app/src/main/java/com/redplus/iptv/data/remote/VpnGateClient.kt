package com.redplus.iptv.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class VpnGateServer(
    val hostName: String,
    val ip: String,
    val countryLong: String,
    val countryShort: String,
    val pingMs: Long,
    val speedBps: Long,
    val sessions: Int,
    val score: Long,
    val openVpnConfigBase64: String
) {
    val title: String get() = "${countryShort.ifBlank { "??" }} • ${countryLong.ifBlank { hostName }}"
    val speedMbps: Long get() = speedBps / 1024L / 1024L
    val hasOpenVpnConfig: Boolean get() = openVpnConfigBase64.isNotBlank()

    fun decodedOpenVpnConfig(): String {
        val decoded = Base64.decode(openVpnConfigBase64.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
        return decoded.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd() + "\n"
    }
}

class VpnGateClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    suspend fun fetchServers(limit: Int = 40): List<VpnGateServer> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.vpngate.net/api/iphone/")
            .header("User-Agent", "RedPlusIPTV/1.5")
            .get()
            .build()
        val csv = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("VPN Gate returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        parse(csv)
            .filter { it.hasOpenVpnConfig }
            .sortedWith(compareBy<VpnGateServer> { if (it.pingMs <= 0) Long.MAX_VALUE else it.pingMs }.thenByDescending { it.speedBps })
            .take(limit)
    }

    private fun parse(csv: String): List<VpnGateServer> {
        val lines = csv.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val headerIndex = lines.indexOfFirst { raw ->
            val normalized = raw.removePrefix("#").trimStart()
            normalized.startsWith("HostName,", ignoreCase = true)
        }
        if (headerIndex < 0) return emptyList()

        val header = parseCsvLine(lines[headerIndex].removePrefix("#").trimStart())
            .map { it.trim().removePrefix("#") }
        fun indexOf(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }

        val host = indexOf("HostName")
        val ip = indexOf("IP")
        val score = indexOf("Score")
        val ping = indexOf("Ping")
        val speed = indexOf("Speed")
        val countryLong = indexOf("CountryLong")
        val countryShort = indexOf("CountryShort")
        val sessions = indexOf("NumVpnSessions")
        val ovpn = indexOf("OpenVPN_ConfigData_Base64")
        if (ovpn < 0 || host < 0 || ip < 0) return emptyList()

        return lines.drop(headerIndex + 1)
            .asSequence()
            .filter { "," in it && !it.startsWith("#") && !it.startsWith("*") }
            .mapNotNull { row ->
                val values = parseCsvLine(row)
                fun value(index: Int): String = if (index >= 0) values.getOrNull(index).orEmpty().trim() else ""
                val base64 = value(ovpn)
                if (base64.isBlank()) return@mapNotNull null
                VpnGateServer(
                    hostName = value(host),
                    ip = value(ip),
                    countryLong = value(countryLong),
                    countryShort = value(countryShort),
                    pingMs = value(ping).toLongOrNull() ?: Long.MAX_VALUE,
                    speedBps = value(speed).toLongOrNull() ?: 0L,
                    sessions = value(sessions).toIntOrNull() ?: 0,
                    score = value(score).toLongOrNull() ?: 0L,
                    openVpnConfigBase64 = base64
                )
            }
            .toList()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
