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
import org.json.JSONArray

@Composable
fun XtreamTab(onNextTab: () -> Unit) {
    val xtreamNodes = DataStore.scannedNodes.filter { it.type == "Xtream" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }
    var showOnlyActive by remember { mutableStateOf(false) }
    
    val displayNodes = if (showOnlyActive) {
        xtreamNodes.filter { it.status.contains("Active", ignoreCase = true) }
    } else {
        xtreamNodes
    }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    
    var isLoadingCategories by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<JSONArray?>(null) }

    // Reset details when selection changes
    LaunchedEffect(selectedNode) {
        categories = null
        isLoadingCategories = false
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
                text = "Xtream Codes API",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "${displayNodes.size} Nodes",
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
                text = "Continue to Stalker →",
                onClick = onNextTab
            )
        }

        // Table / Grid Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayNodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Xtream configurations found.", color = Color.White.copy(alpha = 0.5f))
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
                            Text("User", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                            Text("Pass", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                            Text("Status", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
                            Text("Expires", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                            Text("Conn", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Max", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Channels", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("VODs", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                        }
                        Divider(color = Color.White.copy(alpha = 0.2f))
                    }
                    
                    itemsIndexed(displayNodes) { _, node ->
                        val isSelected = selectedNode?.baseUrl == node.baseUrl && selectedNode?.user == node.user
                        Row(
                            modifier = Modifier
                                .background(if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedNode = node }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(node.baseUrl.removePrefix("http://").removePrefix("https://"), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                            Text(node.user, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                            Text(node.pass, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                            
                            val statusColor = when {
                                node.status.contains("Active") -> Color(0xFF10B981)
                                node.status.contains("Failed") || node.status.contains("Blocked") -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
                            }
                            Text(node.status, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                            
                            Text(node.expires, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(100.dp))
                            Text(node.activeConn, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(80.dp))
                            Text(node.maxConn, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(80.dp))
                            Text(node.channels, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(80.dp))
                            Text(node.vods, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(80.dp))
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
            GlassCard(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
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
                        GlassButton(text = "Copy User", onClick = { clipboardManager.setText(AnnotatedString(node.user)) }, modifier = Modifier.weight(1f))
                        GlassButton(text = "Copy Pass", onClick = { clipboardManager.setText(AnnotatedString(node.pass)) }, modifier = Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = if (node.isVerifying) "Verifying..." else "Verify",
                            onClick = {
                                if (node.isVerifying) return@GlassButton
                                coroutineScope.launch {
                                    val idx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                    if (idx != -1) {
                                        DataStore.scannedNodes[idx] = DataStore.scannedNodes[idx].copy(isVerifying = true, status = "Connecting...")
                                        selectedNode = DataStore.scannedNodes[idx]
                                    }
                                    
                                    val result = IPTVClient.verifyXtream(node.baseUrl, node.user, node.pass)
                                    val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                    if (newIdx != -1) {
                                        if (result is VerificationResult.Success) {
                                            DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                                isVerifying = false, 
                                                status = result.status, 
                                                details = result.details,
                                                expires = result.expires,
                                                activeConn = result.activeConn,
                                                maxConn = result.maxConn,
                                                serverTimezone = result.serverTimezone,
                                                serverTime = result.serverTime
                                            )
                                        } else if (result is VerificationResult.Failed) {
                                            DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                                isVerifying = false, 
                                                status = result.reason
                                            )
                                        }
                                        if (selectedNode?.baseUrl == node.baseUrl && selectedNode?.user == node.user) {
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
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Category Explorer", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        GlassButton(
                            text = if (isLoadingCategories) "Loading..." else "Load Categories & Count",
                            onClick = {
                                if (isLoadingCategories) return@GlassButton
                                isLoadingCategories = true
                                coroutineScope.launch {
                                    categories = IPTVClient.getLiveCategories(node.baseUrl, node.user, node.pass)
                                    
                                    // Set channel/vod counts (Mocking VOD count since endpoint is categories)
                                    val catCount = categories?.length() ?: 0
                                    val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                    if (newIdx != -1) {
                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                            channels = "$catCount cats"
                                        )
                                        if (selectedNode?.baseUrl == node.baseUrl && selectedNode?.user == node.user) {
                                            selectedNode = DataStore.scannedNodes[newIdx]
                                        }
                                    }
                                    isLoadingCategories = false
                                }
                            }
                        )
                    }
                    
                    if (categories != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Categories Loaded: ${categories!!.length()}", color = Color(0xFF3B82F6), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
