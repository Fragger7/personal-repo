package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import kotlinx.coroutines.launch

@Composable
fun StalkerTab(onNextTab: () -> Unit) {
    val stalkerNodes = DataStore.scannedNodes.filter { it.type == "Stalker" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stalker Portals",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Stalker Portals found.",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Host", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(2f))
                            Text("MAC", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1.5f))
                            Text("Status", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                        }
                    }
                }

                items(stalkerNodes) { node ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedNode = node }
                            .alpha(if (selectedNode == node) 1f else 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                node.baseUrl.removePrefix("http://").removePrefix("https://"),
                                color = Color.White,
                                modifier = Modifier.weight(2f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                node.mac,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val statusColor = when {
                                node.status.contains("Active") -> Color(0xFF10B981)
                                node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(
                                text = if (node.isVerifying) "Verifying..." else if (node.status.length > 10) "View Details" else node.status,
                                color = statusColor,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (selectedNode != null) {
                    val node = selectedNode!!
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Detail Drawer",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Storage, contentDescription = "Record", tint = Color(0xFF8B5CF6))
                                        Text(
                                            text = node.baseUrl.removePrefix("http://").removePrefix("https://"),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                                
                                val statusColor = when {
                                    node.status.contains("Active") -> Color(0xFF10B981)
                                    node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                    else -> Color(0xFFF59E0B)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Status: ${node.status}", color = statusColor, fontWeight = FontWeight.Medium)
                                if (node.details.isNotEmpty()) {
                                    Text(text = node.details, color = Color.White.copy(alpha=0.7f), style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassButton(text = "Copy Host", onClick = { clipboardManager.setText(AnnotatedString(node.baseUrl)) }, modifier = Modifier.weight(1f))
                                    GlassButton(text = "Copy MAC", onClick = { clipboardManager.setText(AnnotatedString(node.mac)) }, modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassButton(
                                        text = if (node.isVerifying) "Verifying..." else "Verify",
                                        onClick = {
                                            if (node.isVerifying) return@GlassButton
                                            coroutineScope.launch {
                                                val idx = DataStore.scannedNodes.indexOf(node)
                                                if (idx != -1) {
                                                    DataStore.scannedNodes[idx] = node.copy(isVerifying = true, status = "Connecting...")
                                                    selectedNode = DataStore.scannedNodes[idx]
                                                }
                                                val result = IPTVClient.verifyStalker(node.baseUrl, node.mac)
                                                val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.mac == node.mac && it.type == "Stalker" }
                                                if (newIdx != -1) {
                                                    if (result is VerificationResult.Success) {
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.status, details = result.details)
                                                    } else if (result is VerificationResult.Failed) {
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.reason)
                                                    }
                                                    if (selectedNode?.baseUrl == node.baseUrl && selectedNode?.mac == node.mac) {
                                                        selectedNode = DataStore.scannedNodes[newIdx]
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassButton(
                                        text = "Commit",
                                        onClick = {
                                            CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Note: Stalker portals block deep-dive category querying to prevent ban/limits.", color = Color.White.copy(alpha=0.5f), style = MaterialTheme.typography.bodySmall)
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
