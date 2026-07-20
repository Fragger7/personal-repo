package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard

@Composable
fun XtreamTab(onNextTab: () -> Unit = {}) {
    var selectedNode by remember { mutableStateOf<com.projectstrong.iptv.network.ParsedCredential?>(null) }
    val xtreamNodes = DataStore.scannedNodes.filter { it.type == "Xtream" }
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
                text = "Xtream Codes Playlists",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "${xtreamNodes.size} Nodes",
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
            if (xtreamNodes.isEmpty()) {
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
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Table Header
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Host", color = Color.White.copy(alpha=0.5f), modifier = Modifier.weight(2f))
                            Text("User", color = Color.White.copy(alpha=0.5f), modifier = Modifier.weight(1f))
                            Text("Status", color = Color.White.copy(alpha=0.5f), modifier = Modifier.weight(1f))
                        }
                    }
                }
                
                items(xtreamNodes) { node ->
                    GlassCard(modifier = Modifier.fillMaxWidth().clickable { selectedNode = node }.alpha(if (selectedNode == node) 1f else 0.5f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(node.baseUrl.removePrefix("http://").removePrefix("https://"), color = Color.White, modifier = Modifier.weight(2f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(node.user, color = Color.White.copy(alpha=0.8f), modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            
                            val statusColor = when {
                                node.status.contains("Active") -> Color(0xFF10B981)
                                node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(if (node.isVerifying) "Verifying..." else node.status.take(10), color = statusColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                
                if (selectedNode != null) {
                    val node = selectedNode!!
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Selected Details", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF3B82F6))
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
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = node.baseUrl, onValueChange = {}, label = "Host URL", minLines = 1)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = node.user, onValueChange = {}, label = "Username", minLines = 1)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = node.pass, onValueChange = {}, label = "Password", minLines = 1)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (node.isVerifying) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = Color(0xFF3B82F6),
                                        trackColor = Color.White.copy(alpha=0.1f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
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
                                                    val updatedNode = node.copy(isVerifying = true, status = "Connecting...")
                                                    DataStore.scannedNodes[idx] = updatedNode
                                                    selectedNode = updatedNode
                                                }
                                                
                                                val result = IPTVClient.verifyXtream(node.baseUrl, node.user, node.pass)
                                                
                                                val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                                if (newIdx != -1) {
                                                    val finalNode = when (result) {
                                                        is VerificationResult.Success -> {
                                                            DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.status, details = result.details)
                                                        }
                                                        is VerificationResult.Failed -> {
                                                            DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.reason)
                                                        }
                                                    }
                                                    DataStore.scannedNodes[newIdx] = finalNode
                                                    if (selectedNode?.baseUrl == finalNode.baseUrl && selectedNode?.user == finalNode.user) {
                                                        selectedNode = finalNode
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
                    text = "Continue to Stalker Portals →",
                    onClick = onNextTab
                )
            }
        }
    }
}
