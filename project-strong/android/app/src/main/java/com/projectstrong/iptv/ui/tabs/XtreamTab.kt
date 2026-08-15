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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun XtreamTab(onNextTab: (() -> Unit)? = null) {
    // Implement chunked/dynamic loading: only show nodes that have finished verifying
    // This prevents rendering thousands of "Connecting..." items and massively improves performance.
    val xtreamNodes = DataStore.scannedNodes.filter { it.type == "Xtream" && (!it.isVerifying && it.status.isNotEmpty()) }
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
                onSelectNode = { selectedNode = it },
                onNextTab = onNextTab
            )
        }
    }
}

@Composable
fun XtreamMasterGrid(nodes: List<ParsedCredential>, onSelectNode: (ParsedCredential) -> Unit, onNextTab: (() -> Unit)? = null) {
    var sortColumn by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(false) }
    var committingNode by remember { mutableStateOf<ParsedCredential?>(null) }

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
    var catalogJob by remember { mutableStateOf<Job?>(null) }

    fun startOrResumeCatalogQuery() {
        val activeNodes = nodes.filter { it.status.contains("Active", ignoreCase = true) }
        if (activeNodes.isEmpty()) {
            DataStore.catalogQueryStatusText = "No active nodes available to query."
            ToastManager.warning("No active nodes available to query!")
            return
        }

        if (DataStore.isCatalogQueryPaused && DataStore.isQueryingCatalogs) {
            DataStore.isCatalogQueryPaused = false
            ToastManager.success("Catalog query resumed")
            return
        }

        DataStore.isQueryingCatalogs = true
        DataStore.isCatalogQueryPaused = false
        DataStore.catalogQueryProgress = 0f
        DataStore.catalogQueryStatusText = "Parallel catalog querying ${activeNodes.size} active nodes..."
        ToastManager.info("Querying catalogs for ${activeNodes.size} nodes...")

        catalogJob?.cancel()
        catalogJob = DataStore.scanScope.launch(Dispatchers.Default) {
            val total = activeNodes.size
            val currentIndex = AtomicInteger(0)
            val completedCount = AtomicInteger(0)
            val updateQueue = ConcurrentLinkedQueue<Triple<String, String, Pair<Int, Int>>>()

            val workerCount = 12.coerceAtMost(total.coerceAtLeast(1))
            val workers = List(workerCount) {
                launch(Dispatchers.IO) {
                    while (DataStore.isQueryingCatalogs) {
                        while (DataStore.isCatalogQueryPaused && DataStore.isQueryingCatalogs) {
                            delay(150)
                        }
                        if (!DataStore.isQueryingCatalogs) break

                        val idx = currentIndex.getAndIncrement()
                        if (idx >= total) break

                        val node = activeNodes[idx]
                        val liveAsync = async { IPTVClient.getLiveStreamCount(node.baseUrl, node.user, node.pass) }
                        val vodAsync = async { IPTVClient.getVodStreamCount(node.baseUrl, node.user, node.pass) }
                        val liveCount = liveAsync.await()
                        val vodCount = vodAsync.await()

                        updateQueue.add(Triple(node.baseUrl, node.user, Pair(liveCount, vodCount)))
                        completedCount.incrementAndGet()
                    }
                }
            }

            // Throttled UI batch loop
            while (DataStore.isQueryingCatalogs && completedCount.get() < total) {
                delay(500)
                val batch = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
                while (true) {
                    val item = updateQueue.poll() ?: break
                    batch.add(item)
                    if (batch.size >= 250) break
                }

                if (batch.isEmpty()) continue

                val currentDone = completedCount.get()
                withContext(Dispatchers.Main) {
                    for ((bUrl, uName, counts) in batch) {
                        val nodeIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == bUrl && it.user == uName && it.type == "Xtream" }
                        if (nodeIdx != -1) {
                            DataStore.scannedNodes[nodeIdx] = DataStore.scannedNodes[nodeIdx].copy(
                                channels = "${counts.first}",
                                vods = "${counts.second}"
                            )
                        }
                    }
                    DataStore.catalogQueryProgress = currentDone.toFloat() / total.toFloat()
                    if (DataStore.isCatalogQueryPaused) {
                        DataStore.catalogQueryStatusText = "⏸️ Paused query at $currentDone / $total nodes."
                    } else {
                        DataStore.catalogQueryStatusText = "Queried $currentDone / $total active nodes (${(DataStore.catalogQueryProgress * 100).toInt()}%)..."
                    }
                }
            }

            workers.forEach { it.join() }

            val finalBatch = mutableListOf<Triple<String, String, Pair<Int, Int>>>()
            while (true) {
                val item = updateQueue.poll() ?: break
                finalBatch.add(item)
            }

            withContext(Dispatchers.Main) {
                for ((bUrl, uName, counts) in finalBatch) {
                    val nodeIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == bUrl && it.user == uName && it.type == "Xtream" }
                    if (nodeIdx != -1) {
                        DataStore.scannedNodes[nodeIdx] = DataStore.scannedNodes[nodeIdx].copy(
                            channels = "${counts.first}",
                            vods = "${counts.second}"
                        )
                    }
                }

                if (DataStore.isQueryingCatalogs) {
                    DataStore.scannedNodes.sortWith(
                        compareByDescending<ParsedCredential> { it.channels.toIntOrNull() ?: -1 }
                            .thenByDescending { it.daysLeft.toIntOrNull() ?: -1 }
                            .thenByDescending { it.vods.toIntOrNull() ?: -1 }
                    )
                    sortColumn = "Live"
                    sortAscending = false
                    DataStore.isQueryingCatalogs = false
                    DataStore.isCatalogQueryPaused = false
                    DataStore.catalogQueryProgress = 1f
                    DataStore.catalogQueryStatusText = "Query Complete! Auto-sorted by Live Channels, Days Left, and VODs."
                    ToastManager.success("Completed catalog query for $total nodes!")
                }
            }
        }
    }

    if (committingNode != null) {
        val node = committingNode!!
        CommitAccountDialog(
            type = "Xtream",
            baseUrl = node.baseUrl,
            user = node.user,
            pass = node.pass,
            status = node.status,
            expires = node.expires,
            daysLeft = node.daysLeft,
            channels = node.channels,
            vods = node.vods,
            activeConn = node.activeConn,
            maxConn = node.maxConn,
            provider = node.provider,
            serverTimezone = node.serverTimezone,
            onDismiss = { committingNode = null },
            onCommitted = { committingNode = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val activeCount = nodes.count { it.status.contains("Active", ignoreCase = true) }
                
                // Tier 1: Title and Active Count Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xtream Codes",
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Discovered ${nodes.size} connections • Showing ${filteredNodes.size} records",
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (activeCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppSuccess.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppSuccess.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "⚡ $activeCount Active",
                                color = Color(0xFF34D399),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tier 2: Sub-toolbar containing Active Only switch filter & Query actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern Active Filter Switch
                    FilterToggleSwitch(
                        checked = DataStore.activeOnlyXtream,
                        onCheckedChange = { DataStore.activeOnlyXtream = it },
                        activeCount = activeCount,
                        totalCount = nodes.size
                    )

                    // Query Catalogs / Progress Controls
                    if (nodes.isNotEmpty()) {
                        if (!DataStore.isQueryingCatalogs) {
                            PrimaryButton(
                                text = "Query Catalogs ($activeCount Active)",
                                onClick = { startOrResumeCatalogQuery() },
                                modifier = Modifier.weight(1f).height(38.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!DataStore.isCatalogQueryPaused) {
                                    PrimaryButton(
                                        text = "⏸️ Pause",
                                        color = AppWarning,
                                        onClick = {
                                            DataStore.isCatalogQueryPaused = true
                                            ToastManager.warning("Catalog query paused")
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    )
                                } else {
                                    PrimaryButton(
                                        text = "▶️ Resume",
                                        color = AppSuccess,
                                        onClick = {
                                            DataStore.isCatalogQueryPaused = false
                                            ToastManager.success("Catalog query resumed")
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    )
                                }
                                PrimaryButton(
                                    text = "⏹️ Stop",
                                    color = AppError,
                                    onClick = {
                                        DataStore.isQueryingCatalogs = false
                                        DataStore.isCatalogQueryPaused = false
                                        catalogJob?.cancel()
                                        DataStore.catalogQueryStatusText = "Catalog query stopped by user."
                                        ToastManager.error("Catalog query stopped")
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp)
                                )
                            }
                        }
                    }
                }

                if (DataStore.catalogQueryStatusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = DataStore.catalogQueryStatusText,
                        color = if (DataStore.isCatalogQueryPaused) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (DataStore.isQueryingCatalogs) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { DataStore.catalogQueryProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (DataStore.isCatalogQueryPaused) AppWarning else AppSuccess,
                            trackColor = AppSurfaceVariant
                        )
                    }
                }
            }
        }

        if (filteredNodes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Xtream accounts found.", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
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
                                    .background(AppSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
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

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceBorder))

                            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                                items(filteredNodes) { node: ParsedCredential ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectNode(node) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
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

                                        Row(
                                            modifier = Modifier.width(230.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Single Node Live/VOD Query
                                            val isNodeFetching = fetchingRows.contains(node.baseUrl + node.user)
                                            GridActionIconButton(
                                                icon = Icons.Default.Refresh,
                                                tooltip = "Query Channels & VOD Counts",
                                                color = Color(0xFF38BDF8),
                                                isLoading = isNodeFetching,
                                                onClick = {
                                                    val key = node.baseUrl + node.user
                                                    if (fetchingRows.contains(key)) return@GridActionIconButton
                                                    coroutineScope.launch {
                                                        fetchingRows = fetchingRows + key
                                                        val liveStreamsAsync = async(Dispatchers.IO) { IPTVClient.getLiveStreamCount(node.baseUrl, node.user, node.pass) }
                                                        val vodStreamsAsync = async(Dispatchers.IO) { IPTVClient.getVodStreamCount(node.baseUrl, node.user, node.pass) }
                                                        val liveCount = liveStreamsAsync.await()
                                                        val vodCount = vodStreamsAsync.await()
                                                        withContext(Dispatchers.Main) {
                                                            val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.type == "Xtream" }
                                                            if (newIdx != -1) {
                                                                DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(channels = "$liveCount", vods = "$vodCount")
                                                            }
                                                            fetchingRows = fetchingRows - key
                                                        }
                                                    }
                                                }
                                            )

                                            // 2. Copy Xtream API Player URL
                                            GridActionIconButton(
                                                icon = Icons.Default.ContentCopy,
                                                tooltip = "Copy API Endpoint",
                                                color = Color(0xFF60A5FA),
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString("${node.baseUrl}/player_api.php?username=${node.user}&password=${node.pass}"))
                                                    ToastManager.success("Copied Xtream API URL to clipboard!")
                                                }
                                            )

                                            // 3. Copy M3U Playlist Download Link (preserved per user request)
                                            GridActionIconButton(
                                                icon = Icons.Default.FileDownload,
                                                tooltip = "Copy M3U Playlist Link",
                                                color = Color(0xFFA78BFA),
                                                onClick = {
                                                    val m3uUrl = "${node.baseUrl}/get.php?username=${node.user}&password=${node.pass}&type=m3u_plus&output=ts"
                                                    clipboardManager.setText(AnnotatedString(m3uUrl))
                                                    ToastManager.success("Copied M3U Playlist URL to clipboard!")
                                                }
                                            )

                                            // 4. Commit to Permanent Database
                                            GridActionIconButton(
                                                icon = Icons.Default.BookmarkAdd,
                                                tooltip = "Commit Account",
                                                color = AppSuccess,
                                                onClick = { committingNode = node }
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceBorder.copy(alpha = 0.5f)))
                                }
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    PrimaryButton(
                                        text = "Continue to Stalker Portals →",
                                        onClick = { onNextTab?.invoke() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
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
                            containerColor = AppPrimary,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top")
                        }
                        FloatingActionButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(if (filteredNodes.isNotEmpty()) filteredNodes.size - 1 else 0) } },
                            containerColor = AppPrimary,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XtreamDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var showCatalogExplorer by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }

    if (showCommitDialog) {
        CommitAccountDialog(
            type = "Xtream",
            baseUrl = node.baseUrl,
            user = node.user,
            pass = node.pass,
            status = node.status,
            expires = node.expires,
            daysLeft = node.daysLeft,
            channels = node.channels,
            vods = node.vods,
            activeConn = node.activeConn,
            maxConn = node.maxConn,
            provider = node.provider,
            serverTimezone = node.serverTimezone,
            onDismiss = { showCommitDialog = false },
            onCommitted = { showCommitDialog = false }
        )
    }

    if (showCatalogExplorer) {
        FullScreenCatalogExplorer(
            baseUrl = node.baseUrl,
            user = node.user,
            pass = node.pass,
            title = node.baseUrl,
            onDismiss = { showCatalogExplorer = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Xtream Connection Details",
                color = AppTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Discrete Login Credentials Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("HOST URL", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.baseUrl, color = AppTextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(node.baseUrl))
                                ToastManager.success("Copied Host URL to clipboard!")
                            }, modifier = Modifier.size(28.dp).padding(start = 6.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    StatusBadge(node.status, 120.dp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("USERNAME", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.user, color = AppTextPrimary, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(node.user))
                                ToastManager.success("Copied Username to clipboard!")
                            }, modifier = Modifier.size(28.dp).padding(start = 6.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PASSWORD", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.pass, color = AppTextPrimary, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(node.pass))
                                ToastManager.success("Copied Password to clipboard!")
                            }, modifier = Modifier.size(28.dp).padding(start = 6.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("M3U PLAYLIST URL", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    val m3uUrl = "${node.baseUrl.trimEnd('/')}/get.php?username=${node.user}&password=${node.pass}&type=m3u_plus&output=ts"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m3uUrl, color = AppTextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(m3uUrl))
                            ToastManager.success("Copied M3U Playlist URL to clipboard!")
                        }, modifier = Modifier.size(28.dp).padding(start = 6.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EXPIRES", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${node.expires} (${node.daysLeft} days)", color = AppTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ACTIVE CONNS", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${node.activeConn} / ${node.maxConn}", color = AppTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Full Screen Catalog Button & Commit Action
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showCatalogExplorer = true },
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔍 Explore Full Catalog & Channels (Full Screen)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            PrimaryButton(
                text = "Commit Account to Saved Records",
                color = AppSuccess,
                onClick = { showCommitDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }
}
