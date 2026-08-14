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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class CommittedSortColumn {
    DATE_ADDED, TYPE, STATUS, SYNC, HOST, PROVIDER, CHANNELS, VODS, DAYS_LEFT, EXPIRES
}

@Composable
fun CommittedTab() {
    val records = CommittedManager.records
    var selectedRecord by remember { mutableStateOf<CommittedRecord?>(null) }
    var isReloading by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var isRechecking by remember { mutableStateOf(false) }
    var isQueryingStreams by remember { mutableStateOf(false) }
    var queryProgressText by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf("") }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showPushConfirmDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf(DataStore.githubToken) }

    val coroutineScope = rememberCoroutineScope()

    // Token Configuration Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Access Token", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "To push saved accounts to your GitHub repository, enter a GitHub Personal Access Token (with repo scope). It is securely stored in your device's private sandboxed app storage and used only to communicate directly with GitHub.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("GITHUB_TOKEN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF333344),
                            focusedContainerColor = Color(0xFF12121A),
                            unfocusedContainerColor = Color(0xFF12121A)
                        )
                    )
                    if (DataStore.githubToken.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                CommittedManager.clearGithubToken()
                                tempToken = ""
                                showTokenDialog = false
                                ToastManager.info("GitHub Token cleared")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("Clear Saved Token")
                        }
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save Token",
                    onClick = {
                        val tokenClean = tempToken.trim()
                        CommittedManager.saveGithubToken(tokenClean)
                        showTokenDialog = false
                        ToastManager.success("GitHub Token saved securely!")
                    }
                )
            },
            dismissButton = {
                SecondaryButton(text = "Cancel", onClick = { showTokenDialog = false })
            },
            containerColor = Color(0xFF1E1E2E)
        )
    }

    // Push Confirmation Dialog (Guard against accidental overwrite)
    if (showPushConfirmDialog) {
        val localCount = records.count { it.isLocal }
        AlertDialog(
            onDismissRequest = { showPushConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Push to GitHub", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You are about to push ${records.size} records to GitHub repository:",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "📁 project-strong/committed.json",
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (localCount > 0) {
                        Text(
                            "Includes $localCount newly added/modified local accounts that will be synced to the cloud.",
                            color = Color(0xFFFBBF24),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "Existing cloud records will be cleanly merged and updated.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Push Now",
                    color = Color(0xFF10B981),
                    onClick = {
                        showPushConfirmDialog = false
                        isPushing = true
                        actionMessage = "Pushing to Cloud..."
                        ToastManager.info("Pushing ${records.size} accounts to GitHub...")
                        coroutineScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                CommittedManager.pushToCloud(DataStore.githubToken)
                            }
                            if (success) {
                                actionMessage = "Push successful! All records synced."
                                ToastManager.success("Successfully pushed ${records.size} records to GitHub!")
                            } else {
                                actionMessage = "Push failed. Token may be invalid or expired."
                                ToastManager.error("Push failed. Check GitHub token permissions.")
                            }
                            isPushing = false
                            delay(2500)
                            actionMessage = ""
                        }
                    }
                )
            },
            dismissButton = {
                SecondaryButton(text = "Cancel", onClick = { showPushConfirmDialog = false })
            },
            containerColor = Color(0xFF1E1E2E)
        )
    }

    AnimatedContent(targetState = selectedRecord != null) { isDetail ->
        if (isDetail && selectedRecord != null) {
            CommittedDetailScreen(
                record = selectedRecord!,
                onBack = { selectedRecord = null },
                onDelete = {
                    CommittedManager.delete(selectedRecord!)
                    selectedRecord = null
                }
            )
        } else {
            CommittedMasterGrid(
                records = records,
                isBusy = isReloading || isPushing || isRechecking || isQueryingStreams,
                statusMessage = actionMessage,
                queryProgressText = queryProgressText,
                onSelectRecord = { selectedRecord = it },
                onRecheckStatus = {
                    if (isReloading || isPushing || isRechecking || isQueryingStreams || records.isEmpty()) return@CommittedMasterGrid
                    isRechecking = true
                    actionMessage = "Re-checking live statuses of all ${records.size} saved accounts..."
                    ToastManager.info("Checking live statuses of ${records.size} accounts...")
                    coroutineScope.launch {
                        try {
                            val count = CommittedManager.recheckAllStatus()
                            actionMessage = "Status check complete ($count verified)!"
                            ToastManager.success("Status re-check complete: $count verified!")
                        } catch (e: Exception) {
                            actionMessage = "Status check error."
                            ToastManager.error("Error during status re-check.")
                        }
                        isRechecking = false
                        delay(2500)
                        actionMessage = ""
                    }
                },
                onQueryStreamCounts = {
                    if (isReloading || isPushing || isRechecking || isQueryingStreams || records.isEmpty()) return@CommittedMasterGrid
                    val xtreamRecords = records.filter { it.safeType == "Xtream" && it.safeStatus.contains("Active", ignoreCase = true) }
                    if (xtreamRecords.isEmpty()) {
                        ToastManager.warning("No active Xtream nodes to query channels for.")
                        return@CommittedMasterGrid
                    }
                    isQueryingStreams = true
                    ToastManager.info("Querying Channels & VODs for ${xtreamRecords.size} active nodes...")
                    coroutineScope.launch {
                        val semaphore = Semaphore(5)
                        var done = 0
                        withContext(Dispatchers.IO) {
                            coroutineScope {
                                val deferreds = xtreamRecords.map { rec ->
                                    async {
                                        semaphore.withPermit {
                                            val liveCount = IPTVClient.getLiveStreamCount(rec.safeBaseUrl, rec.safeUser, rec.safePass)
                                            val vodCount = IPTVClient.getVodStreamCount(rec.safeBaseUrl, rec.safeUser, rec.safePass)
                                            withContext(Dispatchers.Main) {
                                                val idx = CommittedManager.records.indexOf(rec)
                                                if (idx != -1) {
                                                    CommittedManager.records[idx] = rec.copy(
                                                        channels = if (liveCount > 0) liveCount.toString() else rec.safeChannels,
                                                        vods = if (vodCount > 0) vodCount.toString() else rec.safeVods
                                                    )
                                                }
                                                done++
                                                queryProgressText = "Queried $done/${xtreamRecords.size} catalogs..."
                                            }
                                        }
                                    }
                                }
                                deferreds.awaitAll()
                            }
                        }
                        isQueryingStreams = false
                        queryProgressText = ""
                        ToastManager.success("Channels & VOD counts updated for $done nodes!")
                    }
                },
                onReload = {
                    if (isReloading || isPushing || isRechecking || isQueryingStreams) return@CommittedMasterGrid
                    isReloading = true
                    actionMessage = "Syncing from Git repository..."
                    ToastManager.info("Syncing saved records from GitHub...")
                    coroutineScope.launch {
                        try {
                            delay(400)
                            val newRecords = withContext(Dispatchers.IO) {
                                CommittedManager.syncFromCloud()
                            }
                            if (newRecords == null) {
                                actionMessage = "Sync failed. Check internet."
                                ToastManager.error("Sync failed. Could not fetch committed.json")
                            } else {
                                actionMessage = "Synced ${records.size} items from cloud."
                                ToastManager.success("Synced ${records.size} records from GitHub!")
                            }
                        } catch (e: Exception) {
                            actionMessage = "Sync failed."
                            ToastManager.error("Sync failed: ${e.localizedMessage}")
                        }
                        isReloading = false
                        delay(2500)
                        actionMessage = ""
                    }
                },
                onPush = {
                    if (isReloading || isPushing || isRechecking || isQueryingStreams) return@CommittedMasterGrid
                    // Guard against empty push
                    if (records.isEmpty()) {
                        ToastManager.warning("Cannot push empty list to cloud. Load or sync records first.")
                        return@CommittedMasterGrid
                    }
                    if (DataStore.githubToken.isEmpty()) {
                        tempToken = ""
                        showTokenDialog = true
                    } else {
                        showPushConfirmDialog = true
                    }
                },
                onOpenTokenSettings = {
                    tempToken = DataStore.githubToken
                    showTokenDialog = true
                }
            )
        }
    }
}

@Composable
fun CommittedMasterGrid(
    records: List<CommittedRecord>,
    isBusy: Boolean,
    statusMessage: String,
    queryProgressText: String,
    onSelectRecord: (CommittedRecord) -> Unit,
    onRecheckStatus: () -> Unit,
    onQueryStreamCounts: () -> Unit,
    onReload: () -> Unit,
    onPush: () -> Unit,
    onOpenTokenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Sorting state (default: DATE_ADDED descending)
    var sortColumn by remember { mutableStateOf(CommittedSortColumn.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(false) }

    fun toggleSort(column: CommittedSortColumn) {
        if (sortColumn == column) {
            sortAscending = !sortAscending
        } else {
            sortColumn = column
            sortAscending = false
        }
    }

    val sortedRecords = remember(records.toList(), sortColumn, sortAscending) {
        val list = records.toList()
        when (sortColumn) {
            CommittedSortColumn.DATE_ADDED -> {
                if (sortAscending) list.sortedBy { it.safeDateAdded } else list.sortedByDescending { it.safeDateAdded }
            }
            CommittedSortColumn.TYPE -> {
                if (sortAscending) list.sortedBy { it.safeType } else list.sortedByDescending { it.safeType }
            }
            CommittedSortColumn.STATUS -> {
                if (sortAscending) list.sortedBy { it.safeStatus } else list.sortedByDescending { it.safeStatus }
            }
            CommittedSortColumn.SYNC -> {
                if (sortAscending) list.sortedBy { it.isLocal } else list.sortedByDescending { it.isLocal }
            }
            CommittedSortColumn.HOST -> {
                if (sortAscending) list.sortedBy { it.safeBaseUrl } else list.sortedByDescending { it.safeBaseUrl }
            }
            CommittedSortColumn.PROVIDER -> {
                if (sortAscending) list.sortedBy { it.safeProvider } else list.sortedByDescending { it.safeProvider }
            }
            CommittedSortColumn.CHANNELS -> {
                if (sortAscending) list.sortedBy { it.safeChannels.toIntOrNull() ?: -1 } else list.sortedByDescending { it.safeChannels.toIntOrNull() ?: -1 }
            }
            CommittedSortColumn.VODS -> {
                if (sortAscending) list.sortedBy { it.safeVods.toIntOrNull() ?: -1 } else list.sortedByDescending { it.safeVods.toIntOrNull() ?: -1 }
            }
            CommittedSortColumn.DAYS_LEFT -> {
                if (sortAscending) list.sortedBy { it.safeDaysLeft.toIntOrNull() ?: -1 } else list.sortedByDescending { it.safeDaysLeft.toIntOrNull() ?: -1 }
            }
            CommittedSortColumn.EXPIRES -> {
                if (sortAscending) list.sortedBy { it.safeExpires } else list.sortedByDescending { it.safeExpires }
            }
        }
    }

    val localCount = records.count { it.isLocal }

    Column(modifier = Modifier.fillMaxSize()) {
        // Control Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Committed Data (${records.size})",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (localCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$localCount Unpushed",
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (statusMessage.isNotEmpty()) {
                    Text(statusMessage, color = Color(0xFF34D399), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                if (queryProgressText.isNotEmpty()) {
                    Text(queryProgressText, color = Color(0xFF38BDF8), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Query Channels & VODs
                SecondaryButton(
                    text = "📺 Query Catalogs",
                    onClick = onQueryStreamCounts
                )
                // Re-check live status
                SecondaryButton(
                    text = "⚡ Re-Check",
                    onClick = onRecheckStatus
                )
                // Reload from Git
                SecondaryButton(
                    text = "Reload Cloud",
                    onClick = onReload
                )
                // Push to Git (Disabled if records is empty)
                PrimaryButton(
                    text = if (localCount > 0) "Push ($localCount)" else "Push to Cloud",
                    color = if (records.isEmpty()) Color.Gray else Color(0xFF10B981),
                    onClick = onPush
                )
                // Token Key Button
                IconButton(onClick = onOpenTokenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = "Token Settings",
                        tint = if (DataStore.githubToken.isNotEmpty()) Color(0xFF60A5FA) else Color.Gray
                    )
                }
            }
        }

        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFF10B981),
                trackColor = Color(0xFF1E293B)
            )
        }

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No committed records saved.", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Save verified connections from the Xtream or Stalker tabs.", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                }
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
                    // Full 16-Column Header Row matching Python Dataframe exactly
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1E1E2E))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GridHeader("Date Added", 140.dp, onClick = { toggleSort(CommittedSortColumn.DATE_ADDED) }, isSorted = sortColumn == CommittedSortColumn.DATE_ADDED, isAscending = sortAscending)
                        GridHeader("Type", 80.dp, onClick = { toggleSort(CommittedSortColumn.TYPE) }, isSorted = sortColumn == CommittedSortColumn.TYPE, isAscending = sortAscending)
                        GridHeader("Status", 110.dp, onClick = { toggleSort(CommittedSortColumn.STATUS) }, isSorted = sortColumn == CommittedSortColumn.STATUS, isAscending = sortAscending)
                        GridHeader("Sync", 110.dp, onClick = { toggleSort(CommittedSortColumn.SYNC) }, isSorted = sortColumn == CommittedSortColumn.SYNC, isAscending = sortAscending)
                        GridHeader("Server / Host", 230.dp, onClick = { toggleSort(CommittedSortColumn.HOST) }, isSorted = sortColumn == CommittedSortColumn.HOST, isAscending = sortAscending)
                        GridHeader("Provider", 140.dp, onClick = { toggleSort(CommittedSortColumn.PROVIDER) }, isSorted = sortColumn == CommittedSortColumn.PROVIDER, isAscending = sortAscending)
                        GridHeader("Username", 140.dp)
                        GridHeader("Password", 140.dp)
                        GridHeader("MAC Address", 150.dp)
                        GridHeader("Channels", 90.dp, onClick = { toggleSort(CommittedSortColumn.CHANNELS) }, isSorted = sortColumn == CommittedSortColumn.CHANNELS, isAscending = sortAscending)
                        GridHeader("VODs", 90.dp, onClick = { toggleSort(CommittedSortColumn.VODS) }, isSorted = sortColumn == CommittedSortColumn.VODS, isAscending = sortAscending)
                        GridHeader("Days Left", 90.dp, onClick = { toggleSort(CommittedSortColumn.DAYS_LEFT) }, isSorted = sortColumn == CommittedSortColumn.DAYS_LEFT, isAscending = sortAscending)
                        GridHeader("Expires", 110.dp, onClick = { toggleSort(CommittedSortColumn.EXPIRES) }, isSorted = sortColumn == CommittedSortColumn.EXPIRES, isAscending = sortAscending)
                        GridHeader("Conns", 80.dp)
                        GridHeader("Timezone", 130.dp)
                        GridHeader("Notes", 200.dp)
                        GridHeader("Actions", 90.dp)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))

                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(sortedRecords, key = { it.safeBaseUrl + it.safeUser + it.safeMac }) { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRecord(record) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Date Added
                                GridCell(record.safeDateAdded.ifEmpty { "-" }, 140.dp, color = Color(0xFFA0A0B0))
                                // 2. Type
                                StatusBadge(record.safeType, 80.dp)
                                // 3. Status
                                StatusBadge(record.safeStatus, 110.dp)
                                // 4. Sync
                                SyncBadge(record.isLocal, 110.dp)
                                // 5. Server / Host URL
                                GridCell(record.safeBaseUrl, 230.dp, isBold = true)
                                // 6. Provider
                                GridCell(record.safeProvider, 140.dp, color = Color(0xFF93C5FD))
                                // 7. Username
                                GridCell(if (record.safeType == "Xtream") record.safeUser.ifEmpty { "-" } else "-", 140.dp)
                                // 8. Password
                                GridCell(if (record.safeType == "Xtream") record.safePass.ifEmpty { "-" } else "-", 140.dp, color = Color.Gray)
                                // 9. MAC Address
                                GridCell(if (record.safeType == "Stalker") record.safeMac.ifEmpty { "-" } else "-", 150.dp)
                                // 10. Channels
                                GridCell(record.safeChannels.ifEmpty { "-" }, 90.dp)
                                // 11. VODs
                                GridCell(record.safeVods.ifEmpty { "-" }, 90.dp)
                                // 12. Days Left
                                GridCell(record.safeDaysLeft.ifEmpty { "-" }, 90.dp, isBold = true, color = if (record.safeDaysLeft.toIntOrNull() ?: 0 > 30) Color(0xFF34D399) else Color(0xFFFBBF24))
                                // 13. Expires
                                GridCell(record.safeExpires.ifEmpty { "-" }, 110.dp, color = Color(0xFFA0A0B0))
                                // 14. Active/Max Conns
                                val connsStr = if (record.safeActiveConn.isNotEmpty() || record.safeMaxConn.isNotEmpty()) {
                                    "${record.safeActiveConn.ifEmpty { "0" }}/${record.safeMaxConn.ifEmpty { "1" }}"
                                } else "-"
                                GridCell(connsStr, 80.dp)
                                // 15. Timezone
                                GridCell(record.safeTimezone.ifEmpty { "-" }, 130.dp, color = Color.Gray)
                                // 16. Notes
                                GridCell(record.safeNotes.ifEmpty { "..." }, 200.dp, color = Color.LightGray)

                                // Actions (Copy & Delete)
                                Row(modifier = Modifier.width(90.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val copyText = if (record.safeType == "Xtream") {
                                                "Host: ${record.safeBaseUrl}\nUsername: ${record.safeUser}\nPassword: ${record.safePass}"
                                            } else {
                                                "Host: ${record.safeBaseUrl}\nMAC: ${record.safeMac}"
                                            }
                                            clipboardManager.setText(AnnotatedString(copyText))
                                            ToastManager.success("Copied credentials to clipboard!")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { CommittedManager.delete(record) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
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
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Top")
                }
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            if (records.isNotEmpty()) listState.animateScrollToItem(records.size - 1)
                        }
                    },
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bottom")
                }
            }
        }
    }
}

@Composable
fun CommittedDetailScreen(record: CommittedRecord, onBack: () -> Unit, onDelete: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var currentNotes by remember(record) { mutableStateOf(record.safeNotes) }
    var showCatalogExplorer by remember { mutableStateOf(false) }

    if (showCatalogExplorer && record.safeType == "Xtream") {
        FullScreenCatalogExplorer(
            baseUrl = record.safeBaseUrl,
            user = record.safeUser,
            pass = record.safePass,
            title = record.safeBaseUrl,
            onDismiss = { showCatalogExplorer = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.safeType} Record Details",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
            }
        }

        // Host & Credentials Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("HOST URL", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(record.safeBaseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(record.safeBaseUrl))
                                    ToastManager.success("Host URL copied to clipboard!")
                                },
                                modifier = Modifier.size(24.dp).padding(start = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(record.safeStatus, 100.dp)
                        SyncBadge(record.isLocal, 90.dp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (record.safeType == "Xtream") {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("USERNAME", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safeUser, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(record.safeUser))
                                        ToastManager.success("Username copied to clipboard!")
                                    },
                                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PASSWORD", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safePass, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(record.safePass))
                                        ToastManager.success("Password copied to clipboard!")
                                    },
                                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MAC ADDRESS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safeMac, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(record.safeMac))
                                        ToastManager.success("MAC address copied to clipboard!")
                                    },
                                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                if (record.safeType == "Xtream" && record.safeUser.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("M3U PLAYLIST URL", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        val m3uUrl = "${record.safeBaseUrl.trimEnd('/')}/get.php?username=${record.safeUser}&password=${record.safePass}&type=m3u_plus&output=ts"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m3uUrl, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(m3uUrl))
                                    ToastManager.success("M3U Playlist link copied to clipboard!")
                                },
                                modifier = Modifier.size(24.dp).padding(start = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (record.safeDateAdded.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("DATE ADDED: ${record.safeDateAdded}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Full-screen Channel Explorer if Xtream
        if (record.safeType == "Xtream") {
            Button(
                onClick = { showCatalogExplorer = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🔍 Explore Full Catalog & Channels (Full Screen)", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Notes Area
        Text("NOTES & ANNOTATIONS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = currentNotes,
            onValueChange = { currentNotes = it },
            placeholder = { Text("Add notes for this account...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF333344),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedContainerColor = Color(0xFF12121A),
                focusedContainerColor = Color(0xFF12121A)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryButton(
                text = "Save Note",
                onClick = {
                    CommittedManager.updateNotes(record, currentNotes)
                }
            )
        }
    }
}
