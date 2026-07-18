package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard

@Composable
fun StalkerTab(onNextTab: () -> Unit = {}) {
    val stalkerNodes = DataStore.scannedNodes.filter { it.type == "Stalker" }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stalker Portals",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "${stalkerNodes.size} Nodes",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (stalkerNodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Scan nodes in the Scanner tab to populate.",
                            color = Color.White.copy(alpha=0.5f)
                        )
                    }
                }
            } else {
                items(stalkerNodes) { node ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF8B5CF6))
                                Text(
                                    text = node.baseUrl.removePrefix("http://").removePrefix("https://"),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Host URL", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text(text = node.baseUrl, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "MAC Address", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text(text = node.mac, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            val statusColor = when {
                                node.status.contains("Active") -> Color(0xFF10B981)
                                node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(text = "Status: ${node.status}", color = statusColor, fontWeight = FontWeight.Medium)
                            if (node.details.isNotEmpty()) {
                                Text(text = node.details, color = Color.White.copy(alpha=0.7f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(
                                    text = if (node.isVerifying) "Verifying..." else "Verify",
                                    onClick = { 
                                        if (node.isVerifying) return@GlassButton
                                        coroutineScope.launch {
                                            val idx = DataStore.scannedNodes.indexOf(node)
                                            if (idx != -1) {
                                                DataStore.scannedNodes[idx] = node.copy(isVerifying = true, status = "Connecting...")
                                            }
                                            
                                            val result = IPTVClient.verifyStalker(node.baseUrl, node.mac)
                                            
                                            val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.mac == node.mac && it.type == "Stalker" }
                                            if (newIdx != -1) {
                                                when (result) {
                                                    is VerificationResult.Success -> {
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.status, details = result.details)
                                                    }
                                                    is VerificationResult.Failed -> {
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.reason)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GlassButton(
                                    text = "Commit",
                                    onClick = { 
                                        CommittedManager.commit(
                                            CommittedRecord(
                                                type = node.type,
                                                baseUrl = node.baseUrl,
                                                user = node.user,
                                                pass = node.pass,
                                                mac = node.mac,
                                                notes = ""
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                GlassButton(
                    text = "Continue to Committed Data →",
                    onClick = onNextTab
                )
            }
        }
    }
}
