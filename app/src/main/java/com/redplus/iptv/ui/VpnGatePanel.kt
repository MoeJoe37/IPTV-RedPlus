package com.redplus.iptv.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.remote.VpnGateServer
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.ui.theme.PremiumRed
import com.redplus.iptv.vpn.RedPlusVpnService
import kotlinx.coroutines.launch

@Composable
fun VpnGatePanel(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<VpnGateServer>>(emptyList()) }
    var pendingConnect by remember { mutableStateOf<VpnGateServer?>(null) }
    var pendingLoad by remember { mutableStateOf(false) }

    fun loadServers() {
        scope.launch {
            loading = true
            message = null
            runCatching { container.vpnGateClient.fetchServers(30) }
                .onSuccess {
                    servers = it
                    message = if (it.isEmpty()) "No servers found." else "Loaded ${it.size} servers."
                }
                .onFailure { message = it.message ?: "Could not load VPN servers." }
            loading = false
        }
    }

    fun startConnect(server: VpnGateServer) {
        runCatching { startRedPlusVpn(context, server) }
            .onSuccess { message = "VPN starting: ${server.title}" }
            .onFailure { message = it.message ?: "Could not start VPN." }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null) {
            val server = pendingConnect
            pendingConnect = null
            if (server != null) startConnect(server) else if (pendingLoad) loadServers()
        } else {
            pendingConnect = null
            message = "VPN permission was not approved."
        }
        pendingLoad = false
    }

    fun ensureVpnPermission(onGranted: () -> Unit) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent) else onGranted()
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("VPN Gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        pendingLoad = true
                        pendingConnect = null
                        ensureVpnPermission { pendingLoad = false; loadServers() }
                    },
                    enabled = !loading
                ) { Text(if (loading) "Loading..." else "Load servers") }
                Button(onClick = { stopRedPlusVpn(context); message = "VPN stopped." }) { Text("Stop") }
            }

            servers.firstOrNull()?.let { best ->
                Button(
                    onClick = {
                        pendingLoad = false
                        pendingConnect = best
                        ensureVpnPermission { pendingConnect = null; startConnect(best) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Connect fastest: ${best.title}") }
            }

            servers.take(6).forEachIndexed { index, server ->
                Text(
                    "${index + 1}. ${server.title} • ${server.pingMs}ms • ${server.speedMbps} Mbps • ${server.sessions} sessions",
                    color = if (index == 0) PremiumRed else PremiumMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            message?.let { Text(it, color = PremiumMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun startRedPlusVpn(context: Context, server: VpnGateServer) {
    val intent = Intent(context, RedPlusVpnService::class.java).apply {
        action = RedPlusVpnService.ACTION_CONNECT
        putExtra(RedPlusVpnService.EXTRA_TITLE, server.title)
        putExtra(RedPlusVpnService.EXTRA_CONFIG, server.decodedOpenVpnConfig())
    }
    context.startService(intent)
}

private fun stopRedPlusVpn(context: Context) {
    context.startService(Intent(context, RedPlusVpnService::class.java).setAction(RedPlusVpnService.ACTION_STOP))
}
