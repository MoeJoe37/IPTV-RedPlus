package com.redplus.iptv.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.redplus.iptv.MainActivity
import java.io.IOException

class RedPlusVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect(intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_CONFIG).orEmpty())
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun connect(title: String, config: String) {
        stopVpn()
        val remote = firstRemoteHost(config)
        val session = buildString {
            append("RedPlus VPN")
            if (title.isNotBlank()) append(" • ").append(title)
            if (remote.isNotBlank()) append(" • ").append(remote)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        tunnel = Builder()
            .setSession(session)
            .setConfigureIntent(pendingIntent)
            .addAddress("10.255.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .allowFamily(android.system.OsConstants.AF_INET)
            .establish()
    }

    private fun stopVpn() {
        try {
            tunnel?.close()
        } catch (_: IOException) {
        }
        tunnel = null
        stopSelf()
    }

    private fun firstRemoteHost(config: String): String {
        return config.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("remote ") }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            .orEmpty()
    }

    companion object {
        const val ACTION_CONNECT = "com.redplus.iptv.vpn.CONNECT"
        const val ACTION_STOP = "com.redplus.iptv.vpn.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONFIG = "openvpn_config"
    }
}
