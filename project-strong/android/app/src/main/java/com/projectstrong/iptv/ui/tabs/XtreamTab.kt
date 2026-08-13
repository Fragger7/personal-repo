package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun XtreamTab() {
    val xtreamNodes = DataStore.scannedNodes.filter { it.type == "Xtream" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }

    AnimatedContent(targetState = selectedNode != null) { isDetail: Boolean ->
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
    var sortColumn by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(false) }

    val filteredNodes = (if (DataStore.activeOnlyXtream) nodes.filter { it.status.contains("Active", ignoreCase = true) } else nodes)
        .let { list ->
            when (sortColumn) {
                "Live" -> if (sortAscending) list.sortedBy { it.channels.toIntOrNull() ?: -1 } else list.sortedByDescending { it.channels.toIntOrNull() ?: -1 }
                "VODs" -> if (sortAscending) list.sortedBy { it.vods.toIntOrNull() ?: -1 } else list.sortedByDescending { it.vods.toIntOrNull() ?: -1 }
                "Days Left" -> if (sortAscending) list.sortedBy { it.daysLeft.toIntOrNull() ?: -1 } else list.sortedByDescending { it.daysLeft.toIntOrNull() ?: -1 }
                "Active" -> if (sortAscending) list.sortedBy { it.activeConn.toIntOrNull() ?: -1 } else list.sortedByDescending { it.activeConn.toIntOrNull() ?: -1 }
                "Host URL" -> if (sortAscending) list.sortedBy { it.baseUrl } else list.sortedByDescending { it.baseUrl }
                "Status" -> if (sortAscending) list.sortedBy { it.status } else list.sortedByDescending { it.status }
                "Provider" -> if (sortAscending) list.sortedBy { it.provider } else list.sortedByDescending { it.provider }
                "Username" -> if (sortAscending) list.sortedBy { it.user } else list.sortedByDescending { it.user }
                else -> list
            }
        }
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var fetchingRows by remember { mutableStateOf(emptySet<String>()) }
    var isQueryingAll by remember { mutableStateOf(false) }
    var queryStatusText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Xtream Codes (${filteredNodes.size}/${nodes.size})",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                if (nodes.isNotEmpty()) {
                    PrimaryButton(
                        text = if (isQueryingAll) "Stop Query" else "Query All Active",
                        onClick = {
                            if (isQueryingAll) {
                                isQueryingAll = false
                                queryStatusText = "Query stopped."
                                return@PrimaryButton
                            }
                            val activeNodes = nodes.filter { it.status.contains("Active", ignoreCase = true) }
                            if (activeNodes.isEmpty()) {
                                queryStatusText = "No active nodes to query."
                                return@PrimaryButton
                            }
                            isQueryingAll = true
                            DataStore.scanProgress = 0f
                            queryStatusText = "Querying 0/${activeNodes.size} active nodes..."
                            
                            coroutineScope.launch {
                                val total = activeNodes.size
                                var completed = 0
                                val chunkSize = 5
                                val chunks = activeNodes.chunked(chunkSize)

                                for (chunk in chunks) {
                                    if (!isQueryingAll) break

                                    coroutineScope {
                                        chunk.map { node: ParsedCredential ->
                                            async(Dispatchers.IO) {
                                                val key = node.baseUrl + node.user
                                                withContext(Dispatchers.Main) { fetchingRows = fetchingRows + key }

                                                val liveStreams = IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass)
                                                val vodStreams = IPTVClient.getVodStreams(node.baseUrl, node.user, node.pass)
                                                val liveCount = liveStreams?.length() ?: 0
                                                val vodCount = vodStreams?.length() ?: 0

                                                withContext(Dispatchers.Main) {
                                                    val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                                    if (newIdx != -1) {
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(channels = "$liveCount", vods = "$vodCount")
                                                    }
                                                    fetchingRows = fetchingRows - key
                                                    completed++
                                                    DataStore.scanProgress = completed.toFloat() / total.toFloat()
                                                    queryStatusText = "Querying $completed/$total active nodes..."
                                                }
                                            }
                                        }.awaitAll()
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (isQueryingAll) {
                                        // Specific multi-level sort requested:
                                        // 1. Highest Live count, 2. Highest Days Left, 3. Highest VODs count
                                        DataStore.scannedNodes.sortWith(
                                            compareByDescending<ParsedCredential> { it.channels.toIntOrNull() ?: -1 }
                                                .thenByDescending { it.daysLeft.toIntOrNull() ?: -1 }
                                                .thenByDescending { it.vods.toIntOrNull() ?: -1 }
                                        )
                                        sortColumn = "Live"
                                        sortAscending = false
                                        isQueryingAll = false
                                        queryStatusText = "Query Complete! Sorted by Live Channels, Days Left, and VODs."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text("Active Only", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = DataStore.activeOnlyXtream,
                    onCheckedChange = { DataStore.activeOnlyXtream = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF3B82F6), checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.5f))
                )
            }
        }

        if (queryStatusText.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = queryStatusText,
                    color = Color(0xFF38BDF8),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (isQueryingAll) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { DataStore.scanProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        if (filteredNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Xtream accounts found.", color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    Column {
                        // Header Row with Sort Indicators
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF1E1E2E))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            val headerClick = { col: String ->
                                if (sortColumn == col) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortColumn = col
                                    sortAscending = false
                                }
                            }
                            GridHeader("Host URL", 250.dp, onClick = { headerClick("Host URL") }, isSorted = (sortColumn == "Host URL"), isAscending = sortAscending)
                            GridHeader("Status", 120.dp, onClick = { headerClick("Status") }, isSorted = (sortColumn == "Status"), isAscending = sortAscending)
                            GridHeader("Provider", 150.dp, onClick = { headerClick("Provider") }, isSorted = (sortColumn == "Provider"), isAscending = sortAscending)
                            GridHeader("Timezone", 120.dp, null)
                            GridHeader("Username", 120.dp, onClick = { headerClick("Username") }, isSorted = (sortColumn == "Username"), isAscending = sortAscending)
                            GridHeader("Live", 80.dp, onClick = { headerClick("Live") }, isSorted = (sortColumn == "Live"), isAscending = sortAscending)
                            GridHeader("VODs", 80.dp, onClick = { headerClick("VODs") }, isSorted = (sortColumn == "VODs"), isAscending = sortAscending)
                            GridHeader("Days Left", 100.dp, onClick = { headerClick("Days Left") }, isSorted = (sortColumn == "Days Left"), isAscending = sortAscending)
                            GridHeader("Active", 80.dp, onClick = { headerClick("Active") }, isSorted = (sortColumn == "Active"), isAscending = sortAscending)
                            GridHeader("Max", 80.dp, null)
                            GridHeader("Expires", 100.dp, null)
                            GridHeader("Actions", 230.dp, null)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))

                        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                            items(filteredNodes, key = { it.baseUrl + it.user }) { node: ParsedCredential ->
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
                                    GridCell(node.daysLeft, 100.dp)
                                    GridCell(node.activeConn, 80.dp)
                                    GridCell(node.maxConn, 80.dp)
                                    GridCell(node.expires, 100.dp)

                                    Row(modifier = Modifier.width(230.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SecondaryButton(
                                            text = if (fetchingRows.contains(node.baseUrl + node.user)) "..." else "Qry",
                                            onClick = {
                                                val key = node.baseUrl + node.user
                                                if (fetchingRows.contains(key)) return@SecondaryButton
                                                coroutineScope.launch {
                                                    fetchingRows = fetchingRows + key
                                                    val liveStreamsAsync = async(Dispatchers.IO) { IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass) }
                                                    val vodStreamsAsync = async(Dispatchers.IO) { IPTVClient.getVodStreams(node.baseUrl, node.user, node.pass) }
                                                    val liveStreams = liveStreamsAsync.await()
                                                    val vodStreams = vodStreamsAsync.await()
                                                    val liveCount = liveStreams?.length() ?: 0
                                                    val vodCount = vodStreams?.length() ?: 0
                                                    withContext(Dispatchers.Main) {
                                                        val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                                        if (newIdx != -1) {
                                                            DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(channels = "$liveCount", vods = "$vodCount")
                                                        }
                                                        fetchingRows = fetchingRows - key
                                                    }
                                                }
                                            },
                                            modifier = Modifier.height(36.dp).width(50.dp)
                                        )
                                        SecondaryButton(
                                            text = "Copy",
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("${node.baseUrl}/player_api.php?username=${node.user}&password=${node.pass}"))
                                            },
                                            modifier = Modifier.height(36.dp).width(50.dp)
                                        )
                                        PrimaryButton(
                                            text = "Commit",
                                            color = Color(0xFF10B981),
                                            onClick = {
                                                CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                                            },
                                            modifier = Modifier.height(36.dp).width(60.dp)
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
}

@Composable
fun XtreamDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var isFetchingCounts by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<JSONArray?>(null) }
    var selectedCategory by remember { mutableStateOf<JSONObject?>(null) }
    var channelsList by remember { mutableStateOf<JSONArray?>(null) }
    var isLoadingCategories by remember { mutableStateOf(false) }
    var isLoadingChannels by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Xtream Connection Details",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Discrete Login Credentials Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("HOST URL", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Text(node.baseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EXPIRES", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Text("${node.expires} (${node.daysLeft} days)", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ACTIVE CONNS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Text("${node.activeConn} / ${node.maxConn}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = if (isFetchingCounts) "Querying..." else "Query Channels & VODs",
                onClick = {
                    if (isFetchingCounts) return@SecondaryButton
                    isFetchingCounts = true
                    coroutineScope.launch {
                        val liveStreamsAsync = async(Dispatchers.IO) { IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass) }
                        val vodStreamsAsync = async(Dispatchers.IO) { IPTVClient.getVodStreams(node.baseUrl, node.user, node.pass) }
                        val liveStreams = liveStreamsAsync.await()
                        val vodStreams = vodStreamsAsync.await()
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

        // Deep Dive Section Header
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectedCategory == null) "Categories Catalog" else "Channels in ${selectedCategory?.optString("category_name") ?: "Unknown"}", 
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            SecondaryButton(
                text = if (isLoadingCategories || isLoadingChannels) "Loading..." else if (selectedCategory != null) "Back to Categories" else "Load Categories",
                onClick = {
                    if (selectedCategory != null) {
                        selectedCategory = null
                        channelsList = null
                        searchQuery = ""
                    } else {
                        if (isLoadingCategories) return@SecondaryButton
                        isLoadingCategories = true
                        searchQuery = ""
                        coroutineScope.launch {
                            val catsAsync = async(Dispatchers.IO) { IPTVClient.getLiveCategories(node.baseUrl, node.user, node.pass) }
                            val allStreamsAsync = async(Dispatchers.IO) { IPTVClient.getAllLiveStreams(node.baseUrl, node.user, node.pass) }
                            val cats = catsAsync.await()
                            val allStreams = allStreamsAsync.await()
                            
                            if (cats != null && allStreams != null) {
                                val categoryCounts = mutableMapOf<String, Int>()
                                for (i in 0 until allStreams.length()) {
                                    val stream = allStreams.optJSONObject(i)
                                    val catId = stream?.optString("category_id", "") ?: ""
                                    if (catId.isNotEmpty()) {
                                        categoryCounts[catId] = categoryCounts.getOrDefault(catId, 0) + 1
                                    }
                                }
                                
                                for (i in 0 until cats.length()) {
                                    val cat = cats.optJSONObject(i)
                                    if (cat != null) {
                                        val catId = cat.optString("category_id", "")
                                        val count = categoryCounts[catId] ?: 0
                                        cat.put("count", count)
                                    }
                                }
                            }
                            categories = cats
                            isLoadingCategories = false
                        }
                    }
                }
            )
        }

        // Real-time Level 2 Search Input
        if (categories != null || channelsList != null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (selectedCategory != null) "Search channels..." else "Search category groups...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF333344),
                    focusedContainerColor = Color(0xFF12121A),
                    unfocusedContainerColor = Color(0xFF12121A)
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        // Data List
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF12121A), RoundedCornerShape(8.dp))) {
            val currentChannels = channelsList
            val currentCategories = categories
            val currentCategory = selectedCategory

            if (isLoadingChannels || isLoadingCategories) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3B82F6))
            } else if (currentCategory != null) {
                if (currentChannels == null || currentChannels.length() == 0) {
                    Text("No channels found.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    val filteredChannels = remember(currentChannels, searchQuery) {
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until currentChannels.length()) {
                            val ch = currentChannels.optJSONObject(i)
                            if (ch != null) {
                                val name = ch.optString("name", "")
                                if (searchQuery.isEmpty() || name.contains(searchQuery, ignoreCase = true)) {
                                    list.add(ch)
                                }
                            }
                        }
                        list
                    }

                    if (filteredChannels.isEmpty()) {
                        Text("No matching channels for '$searchQuery'", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    } else {
                        key(searchQuery) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                items(filteredChannels, key = { it.optString("stream_id", "") + it.optString("name", "") }) { ch ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(ch.optString("name", "Unknown"), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text("ID: ${ch.optString("stream_id", "")}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                                }
                            }
                        }
                    }
                }
            } else if (currentCategories != null) {
                val filteredCategories = remember(currentCategories, searchQuery) {
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until currentCategories.length()) {
                        val cat = currentCategories.optJSONObject(i)
                        if (cat != null) {
                            val name = cat.optString("category_name", "")
                            if (searchQuery.isEmpty() || name.contains(searchQuery, ignoreCase = true)) {
                                list.add(cat)
                            }
                        }
                    }
                    list
                }

                if (filteredCategories.isEmpty()) {
                    Text("No matching category groups for '$searchQuery'", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    key(searchQuery) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(filteredCategories, key = { it.optString("category_id", "") }) { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategory = cat
                                            isLoadingChannels = true
                                            searchQuery = ""
                                            coroutineScope.launch {
                                                channelsList = IPTVClient.getLiveStreams(node.baseUrl, node.user, node.pass, cat.optString("category_id", ""))
                                                isLoadingChannels = false
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val catName = cat.optString("category_name", "Unknown")
                                    val count = cat.optInt("count", 0)
                                    Text("$catName ($count)", color = Color(0xFF3B82F6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("ID: ${cat.optString("category_id", "")}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                            }
                        }
                    }
                }
            } else {
                Text("Click Load Categories to fetch catalog data.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
