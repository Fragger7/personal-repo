package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var showOnlyActive by remember { mutableStateOf(false) }

    val displayNodes = if (showOnlyActive) {
        stalkerNodes.filter { it.status.contains("Active", ignoreCase = true) }
    } else {
        stalkerNodes
    }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // Reset details when selection changes
    LaunchedEffect(selectedNode) {
        if (selectedNode != null) {
            val idx = displayNodes.indexOf(selectedNode)
            if (idx >= 0) {
                listState.animateScrollToItem(idx + 1) // +1 for header
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
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
                    text = "${displayNodes.size} Portals",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = showOnlyActive,
                    onCheckedChange = { showOnlyActive = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF3B82F6), checkmarkColor = Color.White)
                )
                Text("Show only Active", color = Color.White)
            }
            GlassButton(
                text = "Continue to Committed →",
                onClick = onNextTab
            )
        }

        // Table / Grid Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Stalker portals found.", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text("Host", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
                            Text("MAC Address", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp))
                            Text("Status", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp))
                            Text("Details", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
                        }
                        Divider(color = Color.White.copy(alpha = 0.2f))
                    }
                    
                    itemsIndexed(displayNodes) { _, node ->
                        val isSelected = selectedNode?.baseUrl == node.baseUrl && selectedNode?.mac == node.mac
                        Row(
                            modifier = Modifier
                                .background(if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedNode = node }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(node.baseUrl.removePrefix("http://").removePrefix("https://"), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                            Text(node.mac, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(160.dp))
                            
                            val statusColor = when {
                                node.status.contains("Active") -> Color(0xFF10B981)
                                node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(node.status, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(160.dp))
                            
                            Text(node.details, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                        }
                        Divider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // Deep-Dive Drawer Fixed at Bottom
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val node = selectedNode ?: return@AnimatedVisibility
            GlassCard(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF3B82F6))
                            Text(
                                text = node.baseUrl.removePrefix("http://").removePrefix("https://"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(onClick = { selectedNode = null }) {
                            Text("✕", color = Color.White)
                        }
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
                                    val idx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.mac == node.mac && it.type == "Stalker" }
                                    if (idx != -1) {
                                        DataStore.scannedNodes[idx] = DataStore.scannedNodes[idx].copy(isVerifying = true, status = "Connecting...")
                                        selectedNode = DataStore.scannedNodes[idx]
                                    }
                                    
                                    val result = IPTVClient.verifyStalker(node.baseUrl, node.mac)
                                    val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.mac == node.mac && it.type == "Stalker" }
                                    if (newIdx != -1) {
                                        if (result is VerificationResult.Success) {
                                            DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                                isVerifying = false, 
                                                status = result.status, 
                                                details = result.details
                                            )
                                        } else if (result is VerificationResult.Failed) {
                                            DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                                isVerifying = false, 
                                                status = result.reason
                                            )
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
                                CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = "", pass = "", mac = node.mac, notes = ""))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Note: Stalker portals do not support deep-dive category exploration due to MAC token strict checks.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
