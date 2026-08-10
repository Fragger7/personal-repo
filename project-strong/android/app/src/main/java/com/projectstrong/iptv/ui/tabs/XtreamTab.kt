package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import kotlinx.coroutines.Dispatchers

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun XtreamTab() {
    val xtreamNodes = DataStore.scannedNodes.filter { it.type == "Xtream" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }

    AnimatedContent(targetState = selectedNode != null) { isDetail ->
        if (isDetail && selectedNode != null) {
            XtreamDetailScreen(
                node = selectedNode!!,
                onBack = { selectedNode = null }
            )
        } else {
            XtreamMasterGrid(
                nodes = xtreamNodes,
                onSelectNode = { selectedNode = it }
            )
        }
    }
}

@Composable
fun XtreamMasterGrid(nodes: List<ParsedCredential>, onSelectNode: (ParsedCredential) -> Unit) {
    var showActiveOnly by remember { mutableStateOf(false) }
    val filteredNodes = if (showActiveOnly) nodes.filter { it.status.contains("Active", ignoreCase = true) } else nodes
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var fetchingRows by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Xtream Codes Nodes (${nodes.size})",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active Only", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showActiveOnly,
                    onCheckedChange = { showActiveOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF3B82F6), checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.5f))
                )
            }
        }
        
        if (filteredNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Xtream accounts found.", color = Color.Gray)
            }
            return
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1E1E2E))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        GridHeader("Host URL", 250.dp)
                        GridHeader("Status", 120.dp)
                        GridHeader("Provider", 150.dp)
                        GridHeader("Timezone", 120.dp)
                        GridHeader("Username", 120.dp)
                        GridHeader("Live", 80.dp)
                        GridHeader("VODs", 80.dp)
                        GridHeader("Active", 80.dp)
                        GridHeader("Max", 80.dp)
                        GridHeader("Expires", 100.dp)
                        GridHeader("Actions", 280.dp)
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(filteredNodes) { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectNode(node) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GridCell(node.baseUrl, 250.dp, isBold = true)
                                StatusBadge(node.status, 120.dp)
                                GridCell(node.provider, 150.dp)
                                GridCell(node.serverTimezone, 120.dp)
                                GridCell(node.user, 120.dp)
                                GridCell(node.channels, 80.dp)
                                GridCell(node.vods, 80.dp)
                                GridCell(node.activeConn, 80.dp)
                                GridCell(node.maxConn, 80.dp)
                                GridCell(node.expires, 100.dp)
                                
                                Row(modifier = Modifier.width(280.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isFetching = fetchingRows.contains(node.baseUrl + node.user)
                                    SecondaryButton(
                                        text = if (isFetching) "..." else "Query",
                                        onClick = {
                                            if (isFetching) return@SecondaryButton
                                            val key = node.baseUrl + node.user
                                            fetchingRows = fetchingRows + key
                                            coroutineScope.launch {
                                                val liveStreams = IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass)
                                                val vodStreams = IPTVClient.getVodStreams(node.baseUrl, node.user, node.pass)
                                                
                                                val liveCount = liveStreams?.length() ?: 0
                                                val vodCount = vodStreams?.length() ?: 0
                                                
                                                val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                                if (newIdx != -1) {
                                                    DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(channels = "$liveCount", vods = "$vodCount")
                                                }
                                                fetchingRows = fetchingRows - key
                                            }
                                        },
                                        modifier = Modifier.height(36.dp).weight(1f)
                                    )
                                    SecondaryButton(
                                        text = "Copy",
                                        onClick = {
                                            val url = "${node.baseUrl}/get.php?username=${node.user}&password=${node.pass}&type=m3u_plus&output=ts"
                                            clipboardManager.setText(AnnotatedString(url))
                                        },
                                        modifier = Modifier.height(36.dp).weight(1f)
                                    )
                                    PrimaryButton(
                                        text = "Commit",
                                        onClick = {
                                            CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                                        },
                                        modifier = Modifier.height(36.dp).weight(1f)
                                    )
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                        }
                    }
                }
            }
            
            // Floating scroll buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top")
                }
                FloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(if (filteredNodes.isNotEmpty()) filteredNodes.size - 1 else 0) } },
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
                }
            }
        }
    }
}

@Composable
fun XtreamDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    var categories by remember { mutableStateOf<JSONArray?>(null) }
    var channelsList by remember { mutableStateOf<JSONArray?>(null) }
    var isLoadingCategories by remember { mutableStateOf(false) }
    var isLoadingChannels by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var isFetchingCounts by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Connection Details",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        AnimatedVisibility(visible = selectedCategory == null) {
            Column {
                // Host Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("HOST", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.baseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(node.baseUrl)) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Host", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            StatusBadge(node.status, 120.dp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("USERNAME", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.user, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(node.user)) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PASSWORD", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(node.pass, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(node.pass)) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("M3U PLAYLIST", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                                IconButton(onClick = { 
                                    val m3uUrl = "${node.baseUrl.trimEnd('/')}/get.php?username=${node.user}&password=${node.pass}&type=m3u_plus&output=ts"
                                    clipboardManager.setText(AnnotatedString(m3uUrl)) 
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy M3U URL", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("EXPIRES", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                                Text(node.expires, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                
                // Actions
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (isFetchingCounts) "Fetching Counts..." else "Query Channels & VOD Counts",
                        onClick = {
                            if (isFetchingCounts) return@PrimaryButton
                            isFetchingCounts = true
                            coroutineScope.launch {
                                val liveStreams = IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass)
                                val vodStreams = IPTVClient.getVodStreams(node.baseUrl, node.user, node.pass)
                                
                                val liveCount = liveStreams?.length() ?: 0
                                val vodCount = vodStreams?.length() ?: 0
                                
                                val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                if (newIdx != -1) {
                                    DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(channels = "$liveCount", vods = "$vodCount")
                                }
                                isFetchingCounts = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = "Commit Account",
                        color = Color(0xFF10B981),
                        onClick = {
                            CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Deep Dive Section
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectedCategory == null) "Categories Catalog" else "Channels in ${selectedCategory!!.optString("category_name")}", 
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            SecondaryButton(
                text = if (isLoadingCategories) "Loading..." else if (selectedCategory != null) "Back to Categories" else "Load Categories",
                onClick = {
                    if (selectedCategory != null) {
                        selectedCategory = null
                        channelsList = null
                    } else {
                        if (isLoadingCategories) return@SecondaryButton
                        isLoadingCategories = true
                        coroutineScope.launch {
                            categories = IPTVClient.getLiveCategories(node.baseUrl, node.user, node.pass)
                            isLoadingCategories = false
                        }
                    }
                }
            )
        }

        // Data List
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF12121A), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
            if (isLoadingChannels || isLoadingCategories) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3B82F6))
            } else if (channelsList != null && selectedCategory != null) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(channelsList!!.length()) { i ->
                        val ch = channelsList!!.optJSONObject(i)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ch?.optString("name", "Unknown") ?: "Unknown", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("ID: ${ch?.optString("stream_id", "")}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                    }
                }
            } else if (categories != null) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(categories!!.length()) { i ->
                        val cat = categories!!.optJSONObject(i)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategory = cat
                                    isLoadingChannels = true
                                    coroutineScope.launch {
                                        channelsList = IPTVClient.getLiveStreams(node.baseUrl, node.user, node.pass, cat?.optString("category_id") ?: "")
                                        isLoadingChannels = false
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat?.optString("category_name", "Unknown") ?: "Unknown", color = Color(0xFF3B82F6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("ID: ${cat?.optString("category_id", "")}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                    }
                }
            } else {
                Text("Click Load Categories to fetch catalog data.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
