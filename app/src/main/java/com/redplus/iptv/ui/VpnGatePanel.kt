package com.redplus.iptv.ui

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redplus.iptv.data.AppContainer
import com.redplus.iptv.data.remote.VpnGateServer
import com.redplus.iptv.ui.theme.PremiumMuted
import com.redplus.iptv.ui.theme.PremiumRed
import com.redplus.iptv.vpn.RedPlusOpenVpnBridge
import kotlinx.coroutines.launch

@Composable
fun VpnGatePanel(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<VpnGateServer>>(emptyList()) }
    var selected by remember { mutableStateOf<VpnGateServer?>(null) }
    var pendingConnect by remember { mutableStateOf<VpnGateServer?>(null) }
    var pendingLoad by remember { mutableStateOf(false) }

    fun autoPick(list: List<VpnGateServer>): VpnGateServer? = list
        .filter { it.hasOpenVpnConfig }
        .minWithOrNull(
            compareBy<VpnGateServer> { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE }
                .thenByDescending { it.speedBps }
                .thenBy { it.sessions }
        )

    fun loadServers() {
        scope.launch {
            loading = true
            message = null
            runCatching { container.vpnGateClient.fetchServers(60) }
                .onSuccess { list ->
                    servers = list
                    selected = autoPick(list)
                    message = if (list.isEmpty()) "No servers found." else "Loaded ${list.size} servers."
                }
                .onFailure { message = it.message ?: "Could not load VPN servers." }
            loading = false
        }
    }

    fun startConnect(server: VpnGateServer) {
        runCatching { startRedPlusVpn(context, server) }
            .onSuccess { message = "Connecting: ${server.title}" }
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
                OutlinedButton(onClick = { stopRedPlusVpn(); message = "VPN stopped." }) { Text("Stop") }
            }

            selected?.let { server ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            pendingLoad = false
                            pendingConnect = server
                            ensureVpnPermission { pendingConnect = null; startConnect(server) }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Connect selected") }
                    OutlinedButton(onClick = { selected = autoPick(servers); message = selected?.let { "Auto selected: ${it.title}" } ?: "No available VPN server." }) {
                        Text("Auto select")
                    }
                }
                ServerRow(server = server, selected = true, onClick = {})
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                servers.take(15).forEach { server ->
                    ServerRow(server = server, selected = server == selected, onClick = { selected = server; message = "Selected: ${server.title}" })
                }
            }

            message?.let { Text(it, color = PremiumMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ServerRow(server: VpnGateServer, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(1.4.dp, PremiumRed) else BorderStroke(1.dp, Color.White.copy(alpha = .18f))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = if (selected) .08f else .035f), RoundedCornerShape(12.dp))
            .border(border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(if (selected) "✓ ${server.title}" else server.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text("${server.pingMs}ms • ${server.speedMbps} Mbps • ${server.sessions} sessions", color = PremiumMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun startRedPlusVpn(context: Context, server: VpnGateServer) {
    RedPlusOpenVpnBridge.connect(context, server.title, server.decodedOpenVpnConfig(), server.ip)
}

private fun stopRedPlusVpn() {
    RedPlusOpenVpnBridge.stop()
}
