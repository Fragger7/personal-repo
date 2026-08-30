package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.NetworkMonitor
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConnectionStateDialog(onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = when {
                                DataStore.isVpnActive -> Color(0xFF38BDF8)
                                DataStore.isCloudHosting -> AppWarning
                                else -> AppSuccess
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Connection State",
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        DataStore.isVpnActive -> AppPrimaryContainer
                        DataStore.isCloudHosting -> AppWarningContainer
                        else -> AppSuccessContainer
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            DataStore.isVpnActive -> Color(0xFF38BDF8).copy(alpha = 0.5f)
                            DataStore.isCloudHosting -> AppWarning.copy(alpha = 0.5f)
                            else -> AppSuccess.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        DataStore.isVpnActive -> Color(0xFF38BDF8)
                                        DataStore.isCloudHosting -> Color(0xFFFBBF24)
                                        else -> Color(0xFF34D399)
                                    }
                                )
                        )
                        Column {
                            Text(
                                text = when {
                                    DataStore.isVpnActive -> "Encrypted VPN Tunnel Active"
                                    DataStore.isCloudHosting -> "Cloud Datacenter IP Detected"
                                    else -> "Direct Residential ISP Connection"
                                },
                                color = when {
                                    DataStore.isVpnActive -> Color(0xFF38BDF8)
                                    DataStore.isCloudHosting -> Color(0xFFFBBF24)
                                    else -> Color(0xFF34D399)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    DataStore.isVpnActive -> "Android VPN transport active. Traffic is securely routed through your VPN tunnel."
                                    DataStore.isCloudHosting -> "Outbound traffic originates from a cloud provider range. IPTV firewalls may block queries."
                                    else -> "Residential / cellular IP active. Optimum for verifying IPTV playlists without Cloudflare blocks."
                                },
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Network Details Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurfaceVariant,
                    border = BorderStroke(1.dp, AppSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkDetailRow("VPN Transport", if (DataStore.isVpnActive) "Active (Hardware Verified)" else "Disabled")
                        NetworkDetailRow("External IP", DataStore.detectedIp.ifEmpty { "Querying..." })
                        NetworkDetailRow("ISP Provider", DataStore.detectedIsp.ifEmpty { "Direct Connection" })
                        if (DataStore.detectedOrg.isNotEmpty() && DataStore.detectedOrg != DataStore.detectedIsp) {
                            NetworkDetailRow("Organization", DataStore.detectedOrg)
                        }
                        if (DataStore.detectedCountry.isNotEmpty()) {
                            NetworkDetailRow("Location", DataStore.detectedCountry)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Refresh Button
                PrimaryButton(
                    text = if (DataStore.isCheckingNetwork) "Checking Network..." else "🔄 Refresh Connection (VPN Test)",
                    onClick = {
                        coroutineScope.launch {
                            val success = NetworkMonitor.refreshNetworkState(context)
                            if (success) {
                                val vpnLabel = if (DataStore.isVpnActive) " (VPN Active)" else ""
                                ToastManager.success("Network updated: ${DataStore.detectedIsp}$vpnLabel")
                            } else {
                                ToastManager.warning("Could not reach IP diagnostics service.")
                            }
                        }
                    },
                    enabled = !DataStore.isCheckingNetwork,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
            }
        }
    }
}

@Composable
private fun NetworkDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = AppTextMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            color = AppTextPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
