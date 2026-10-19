package com.projectstrong.iptv.ui.tabs

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.data.VaultItem
import com.projectstrong.iptv.data.VaultManager
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class VaultSortColumn {
    DATE_ADDED, NAME, STATUS, SYNC, ORIGINAL_NAME, STREAM_ID, HOST, CATEGORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTab(
    onLock: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf(emptySet<String>()) }
    var filterPlaylist by remember { mutableStateOf(emptySet<String>()) }
    var filterStatus by remember { mutableStateOf(emptySet<String>()) }
    var filterSync by remember { mutableStateOf(emptySet<String>()) }

    var sortColumn by remember { mutableStateOf(VaultSortColumn.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(false) }

    fun toggleSort(col: VaultSortColumn) {
        if (sortColumn == col) {
            sortAscending = !sortAscending
        } else {
            sortColumn = col
            sortAscending = true
        }
    }

    var previewChannel by remember { mutableStateOf<VaultItem?>(null) }
    var renameTarget by remember { mutableStateOf<VaultItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<VaultItem?>(null) }
    var showPushConfirmDialog by remember { mutableStateOf(false) }

    var isRechecking by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf("") }

    val isBusy = isRechecking || isRefreshing || isPushing || VaultManager.isSyncing

    // Check timeout periodically while VaultTab is visible
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (VaultManager.checkTimeout()) {
                onLock()
                break
            }
        }
    }

    val items = VaultManager.items
    val unpushedCount = items.count { !it.isSynced }

    // Predicates for cascading filter options
    val matchSearch = { item: VaultItem ->
        if (searchQuery.isBlank()) true else {
            val q = searchQuery.trim().lowercase()
            item.name.lowercase().contains(q) ||
            item.originalName.lowercase().contains(q) ||
            item.categoryName.lowercase().contains(q) ||
            item.parentPlaylist.lowercase().contains(q) ||
            item.baseUrl.lowercase().contains(q) ||
            item.streamId.contains(q)
        }
    }

    val matchCategory = { item: VaultItem ->
        if (filterCategory.isEmpty()) true else {
            val hasNone = filterCategory.contains("None")
            val cat = item.categoryName.trim()
            (hasNone && cat.isEmpty()) || filterCategory.contains(cat)
        }
    }

    val matchPlaylist = { item: VaultItem ->
        if (filterPlaylist.isEmpty()) true else {
            val pl = item.parentPlaylist.ifBlank { item.baseUrl }.trim()
            filterPlaylist.contains(pl)
        }
    }

    val matchStatus = { item: VaultItem ->
        if (filterStatus.isEmpty()) true else {
            filterStatus.contains(item.status)
        }
    }

    val matchSync = { item: VaultItem ->
        if (filterSync.isEmpty()) true else {
            val label = if (item.isSynced) "🟢 Synced" else "🟡 Local"
            filterSync.contains(label)
        }
    }

    // Cascading Distinct Options
    val distinctCategories = remember(items.toList(), filterPlaylist, filterStatus, filterSync) {
        val options = mutableMapOf<String, Int>()
        var noneCount = 0
        items.filter { matchPlaylist(it) && matchStatus(it) && matchSync(it) }
            .forEach { item ->
                val cat = item.categoryName.trim()
                if (cat.isEmpty()) noneCount++ else options[cat] = options.getOrDefault(cat, 0) + 1
            }
        Pair(options.toList().sortedBy { it.first }, noneCount)
    }

    val distinctPlaylists = remember(items.toList(), filterCategory, filterStatus, filterSync) {
        val options = mutableMapOf<String, Int>()
        var noneCount = 0
        items.filter { matchCategory(it) && matchStatus(it) && matchSync(it) }
            .forEach { item ->
                val pl = item.parentPlaylist.ifBlank { item.baseUrl }.trim()
                if (pl.isEmpty()) noneCount++ else options[pl] = options.getOrDefault(pl, 0) + 1
            }
        Pair(options.toList().sortedBy { it.first }, noneCount)
    }

    val distinctStatuses = remember(items.toList(), filterCategory, filterPlaylist, filterSync) {
        val options = mutableMapOf<String, Int>()
        var noneCount = 0
        items.filter { matchCategory(it) && matchPlaylist(it) && matchSync(it) }
            .forEach { item ->
                val st = item.status.trim()
                if (st.isEmpty()) noneCount++ else options[st] = options.getOrDefault(st, 0) + 1
            }
        Pair(options.toList().sortedBy { it.first }, noneCount)
    }

    val distinctSync = remember(items.toList(), filterCategory, filterPlaylist, filterStatus) {
        val options = mutableMapOf<String, Int>()
        items.filter { matchCategory(it) && matchPlaylist(it) && matchStatus(it) }
            .forEach { item ->
                val label = if (item.isSynced) "🟢 Synced" else "🟡 Local"
                options[label] = options.getOrDefault(label, 0) + 1
            }
        Pair(options.toList().sortedBy { it.first }, 0)
    }

    // Filtered items
    val filteredItems = remember(items.toList(), searchQuery, filterCategory, filterPlaylist, filterStatus, filterSync) {
        items.filter {
            matchSearch(it) && matchCategory(it) && matchPlaylist(it) && matchStatus(it) && matchSync(it)
        }
    }

    // Sorted items
    val sortedItems = remember(filteredItems, sortColumn, sortAscending) {
        val list = filteredItems.toList()
        when (sortColumn) {
            VaultSortColumn.DATE_ADDED -> {
                if (sortAscending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            }
            VaultSortColumn.NAME -> {
                if (sortAscending) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
            }
            VaultSortColumn.STATUS -> {
                if (sortAscending) list.sortedBy { it.status } else list.sortedByDescending { it.status }
            }
            VaultSortColumn.SYNC -> {
                if (sortAscending) list.sortedBy { it.isSynced } else list.sortedByDescending { it.isSynced }
            }
            VaultSortColumn.ORIGINAL_NAME -> {
                if (sortAscending) list.sortedBy { it.originalName.lowercase() } else list.sortedByDescending { it.originalName.lowercase() }
            }
            VaultSortColumn.STREAM_ID -> {
                if (sortAscending) list.sortedBy { it.streamId.toIntOrNull() ?: -1 } else list.sortedByDescending { it.streamId.toIntOrNull() ?: -1 }
            }
            VaultSortColumn.HOST -> {
                if (sortAscending) list.sortedBy { it.parentPlaylist.ifBlank { it.baseUrl }.lowercase() } else list.sortedByDescending { it.parentPlaylist.ifBlank { it.baseUrl }.lowercase() }
            }
            VaultSortColumn.CATEGORY -> {
                if (sortAscending) list.sortedBy { it.categoryName.lowercase() } else list.sortedByDescending { it.categoryName.lowercase() }
            }
        }
    }

    val onRecheckStatusAction = {
        if (!isBusy) {
            if (items.isEmpty()) {
                ToastManager.warning("No channels in Vault to check.")
            } else {
                isRechecking = true
                actionMessage = "Verifying live stream status across ${items.size} channels..."
                ToastManager.info("Probing channel status...")
                coroutineScope.launch {
                    val count = VaultManager.recheckAllStatus()
                    actionMessage = "Check complete ($count channels verified)."
                    ToastManager.success("Vault channels verified successfully!")
                    isRechecking = false
                    delay(2500)
                    actionMessage = ""
                }
            }
        }
    }

    val onRefreshAction = {
        if (!isBusy) {
            val pwd = VaultManager.currentSessionPassword
            if (pwd == null) {
                ToastManager.error("Session password missing. Please unlock again.")
            } else if (DataStore.githubToken.isEmpty()) {
                ToastManager.warning("Configure GitHub Token in Settings to sync with cloud.")
            } else {
                isRefreshing = true
                actionMessage = "Syncing Vault from GitHub..."
                ToastManager.info("Pulling latest encrypted Vault from cloud...")
                coroutineScope.launch {
                    val res = VaultManager.syncFromCloud(pwd)
                    if (res.isSuccess) {
                        actionMessage = "Sync complete (${res.getOrNull()} channels)."
                        ToastManager.success("Vault synced with cloud!")
                    } else {
                        val err = res.exceptionOrNull()?.message ?: "Unknown error"
                        actionMessage = "Sync failed: $err"
                        ToastManager.error("Sync failed: $err")
                    }
                    isRefreshing = false
                    delay(2500)
                    actionMessage = ""
                }
            }
        }
    }

    val onPushAction = {
        if (!isBusy) {
            if (items.isEmpty()) {
                ToastManager.warning("Cannot push empty Vault to cloud.")
            } else if (DataStore.githubToken.isEmpty()) {
                ToastManager.warning("Configure GitHub Token in Settings to push to cloud.")
            } else {
                showPushConfirmDialog = true
            }
        }
    }

    // Push Confirmation Dialog
    if (showPushConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPushConfirmDialog = false },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AppSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Push Encrypted Vault to GitHub", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You are about to push ${items.size} encrypted channels to GitHub:",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "📁 project-strong/vault.json",
                        color = AppPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (unpushedCount > 0) {
                        Text(
                            "Includes $unpushedCount newly added or renamed channel(s).",
                            color = AppWarning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "🔒 End-to-End Encrypted: Raw stream URLs and credentials are AES-256 encrypted using your vault password before uploading.",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Push Now",
                    color = AppSuccess,
                    onClick = {
                        showPushConfirmDialog = false
                        val pwd = VaultManager.currentSessionPassword
                        if (pwd != null) {
                            isPushing = true
                            actionMessage = "Pushing encrypted Vault to GitHub..."
                            ToastManager.info("Pushing ${items.size} channels to GitHub...")
                            coroutineScope.launch {
                                val success = VaultManager.commitToCloud(pwd)
                                if (success) {
                                    actionMessage = "Push successful! Vault backed up to cloud."
                                    ToastManager.success("Vault successfully synced to GitHub!")
                                } else {
                                    actionMessage = "Push failed. Check GitHub token."
                                    ToastManager.error("Push failed. Check token permissions.")
                                }
                                isPushing = false
                                delay(2500)
                                actionMessage = ""
                            }
                        }
                    }
                )
            },
            dismissButton = {
                SecondaryButton(text = "Cancel", onClick = { showPushConfirmDialog = false })
            }
        )
    }

    // Rename Modal Dialog
    if (renameTarget != null) {
        val item = renameTarget!!
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = AppPrimary)
                    Text("Rename Channel", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Set a custom display name for this channel in your Vault.",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedContainerColor = AppSurfaceVariant,
                            unfocusedContainerColor = AppSurfaceVariant
                        )
                    )
                    Text(
                        "Original Name: ${item.originalName}",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        VaultManager.renameChannel(item, renameInput)
                        renameTarget = null
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { renameTarget = null }
                )
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteTarget != null) {
        val item = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AppError)
                    Text("Remove from Vault?", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Are you sure you want to remove '${item.displayName}' from your secure Vault?",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        VaultManager.deleteChannel(item)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { deleteTarget = null }
                )
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Main Layout Container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action & Status Control Bar (Matching CommittedTab exactly)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isLandscape) 4.dp else 6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 12.dp)) {
                if (isLandscape) {
                    // Landscape Compact Single Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Secure Channel Vault (${items.size})",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (unpushedCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppWarning.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AppWarning.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "⚠️ $unpushedCount Unpushed",
                                        color = Color(0xFFFBBF24),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else if (items.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppSuccess.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "☁️ Synced",
                                        color = Color(0xFF34D399),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SecondaryButton(
                                text = "⚡ Check",
                                onClick = onRecheckStatusAction,
                                modifier = Modifier.height(34.dp)
                            )
                            SecondaryButton(
                                text = "🔄 Sync",
                                onClick = onRefreshAction,
                                modifier = Modifier.height(34.dp)
                            )
                            PrimaryButton(
                                text = if (unpushedCount > 0) "☁️ Push ($unpushedCount)" else "☁️ Push",
                                color = if (items.isEmpty()) AppTextMuted else AppSuccess,
                                onClick = onPushAction,
                                modifier = Modifier.height(34.dp)
                            )
                            Button(
                                onClick = {
                                    VaultManager.lock()
                                    onLock()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppError.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, AppError.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock", tint = AppError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lock", color = AppError, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    // Portrait 2-Tier Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Secure Channel Vault",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Zero-Knowledge Encrypted • Showing ${sortedItems.size} of ${items.size}",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (unpushedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppWarning.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AppWarning.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "⚠️ $unpushedCount Unpushed",
                                    color = Color(0xFFFBBF24),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (items.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppSuccess.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "☁️ Synced",
                                    color = Color(0xFF34D399),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SecondaryButton(
                            text = "⚡ Check",
                            onClick = onRecheckStatusAction,
                            modifier = Modifier.weight(1f).height(38.dp)
                        )
                        SecondaryButton(
                            text = "🔄 Sync",
                            onClick = onRefreshAction,
                            modifier = Modifier.weight(1f).height(38.dp)
                        )
                        PrimaryButton(
                            text = if (unpushedCount > 0) "☁️ Push ($unpushedCount)" else "☁️ Push",
                            color = if (items.isEmpty()) AppTextMuted else AppSuccess,
                            onClick = onPushAction,
                            modifier = Modifier.weight(1.2f).height(38.dp)
                        )
                        Button(
                            onClick = {
                                VaultManager.lock()
                                onLock()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppError.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, AppError.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.weight(0.9f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = AppError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock", color = AppError, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (actionMessage.isNotEmpty()) {
            Text(
                actionMessage,
                color = AppSuccess,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AppSuccess,
                trackColor = AppSurfaceVariant
            )
        }

        // Search Bar Card
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search channel name, original name, category, host...", color = AppTextMuted, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppPrimary,
                unfocusedBorderColor = AppSurfaceBorder,
                focusedTextColor = AppTextPrimary,
                unfocusedTextColor = AppTextPrimary,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            )
        )

        // Filter Dropdowns Row (Matching CommittedTab filter row)
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(20.dp).padding(end = 8.dp)
                )
                FilterDropdown("Category", distinctCategories.first, distinctCategories.second, filterCategory) { filterCategory = it }
                FilterDropdown("Playlist", distinctPlaylists.first, distinctPlaylists.second, filterPlaylist) { filterPlaylist = it }
                FilterDropdown("Status", distinctStatuses.first, distinctStatuses.second, filterStatus) { filterStatus = it }
                FilterDropdown("Sync", distinctSync.first, distinctSync.second, filterSync) { filterSync = it }

                if (filterCategory.isNotEmpty() || filterPlaylist.isNotEmpty() || filterStatus.isNotEmpty() || filterSync.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            filterCategory = emptySet()
                            filterPlaylist = emptySet()
                            filterStatus = emptySet()
                            filterSync = emptySet()
                        }
                    ) {
                        Text("Clear Filters", color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                text = "Showing ${sortedItems.size} of ${items.size}",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Enterprise Data Table Container
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (sortedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterCategory.isNotEmpty() || filterPlaylist.isNotEmpty() || filterStatus.isNotEmpty() || filterSync.isNotEmpty()) {
                                "No channels match your filters"
                            } else {
                                "No channels in Vault yet"
                            },
                            color = AppTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterCategory.isNotEmpty() || filterPlaylist.isNotEmpty() || filterStatus.isNotEmpty() || filterSync.isNotEmpty()) {
                                "Try clearing your filters or search query."
                            } else {
                                "Open the Catalog Explorer on any connection and tap the 🔒 icon next to any channel to save it here."
                            },
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Column(modifier = Modifier.fillMaxHeight()) {
                            // Table Headers with clickable sorting
                            Row(
                                modifier = Modifier
                                    .background(AppSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GridHeader("Date Added", 130.dp, onClick = { toggleSort(VaultSortColumn.DATE_ADDED) }, isSorted = sortColumn == VaultSortColumn.DATE_ADDED, isAscending = sortAscending)
                                GridHeader("Channel Name", 210.dp, onClick = { toggleSort(VaultSortColumn.NAME) }, isSorted = sortColumn == VaultSortColumn.NAME, isAscending = sortAscending)
                                GridHeader("Status", 110.dp, onClick = { toggleSort(VaultSortColumn.STATUS) }, isSorted = sortColumn == VaultSortColumn.STATUS, isAscending = sortAscending)
                                GridHeader("Sync", 110.dp, onClick = { toggleSort(VaultSortColumn.SYNC) }, isSorted = sortColumn == VaultSortColumn.SYNC, isAscending = sortAscending)
                                GridHeader("Original Name", 190.dp, onClick = { toggleSort(VaultSortColumn.ORIGINAL_NAME) }, isSorted = sortColumn == VaultSortColumn.ORIGINAL_NAME, isAscending = sortAscending)
                                GridHeader("Stream ID", 90.dp, onClick = { toggleSort(VaultSortColumn.STREAM_ID) }, isSorted = sortColumn == VaultSortColumn.STREAM_ID, isAscending = sortAscending)
                                GridHeader("Parent Playlist / Host", 220.dp, onClick = { toggleSort(VaultSortColumn.HOST) }, isSorted = sortColumn == VaultSortColumn.HOST, isAscending = sortAscending)
                                GridHeader("Category", 140.dp, onClick = { toggleSort(VaultSortColumn.CATEGORY) }, isSorted = sortColumn == VaultSortColumn.CATEGORY, isAscending = sortAscending)
                                GridHeader("Actions", 170.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(AppSurfaceBorder)
                            )

                            // Table Data Rows
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                state = listState
                            ) {
                                items(sortedItems, key = { it.id }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Date Added
                                        GridCell(item.dateAdded.ifEmpty { "-" }, 130.dp, color = AppTextSecondary)

                                        // 2. Channel Name (Display Name + Quick edit icon)
                                        Row(
                                            modifier = Modifier.width(210.dp).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = item.displayName,
                                                color = AppTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            IconButton(
                                                onClick = {
                                                    renameInput = item.displayName
                                                    renameTarget = item
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    tint = AppTextMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // 3. Status Badge
                                        StatusBadge(item.status, 110.dp)

                                        // 4. Sync Badge
                                        SyncBadge(!item.isSynced, 110.dp)

                                        // 5. Original Name
                                        GridCell(item.originalName.ifEmpty { "-" }, 190.dp, color = AppTextSecondary)

                                        // 6. Stream ID
                                        GridCell(item.streamId, 90.dp, color = AppPrimary, isBold = true)

                                        // 7. Parent Playlist / Host
                                        GridCell(item.parentPlaylist.ifEmpty { item.baseUrl }, 220.dp, color = AppTextPrimary)

                                        // 8. Category
                                        Box(modifier = Modifier.width(140.dp).padding(end = 8.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = AppPrimary.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.25f))
                                            ) {
                                                Text(
                                                    text = item.categoryName.ifEmpty { "Live" },
                                                    color = AppPrimary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 9. Actions
                                        Row(
                                            modifier = Modifier.width(170.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Play Stream
                                            IconButton(
                                                onClick = { previewChannel = item },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(AppSuccessContainer)
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = AppSuccess,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Rename
                                            IconButton(
                                                onClick = {
                                                    renameInput = item.displayName
                                                    renameTarget = item
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(AppSurfaceVariant)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    tint = AppTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Copy Stream URL
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(item.directUrl))
                                                    ToastManager.success("Copied Direct Stream URL")
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(AppSurfaceVariant)
                                            ) {
                                                Icon(
                                                    Icons.Default.Link,
                                                    contentDescription = "Copy URL",
                                                    tint = AppTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Delete
                                            IconButton(
                                                onClick = { deleteTarget = item },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(AppErrorContainer)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = AppError,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(AppSurfaceBorder.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }

                    SmartLazyListScroller(
                        listState = listState,
                        itemCount = sortedItems.size,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }

    // Floating Live Stream Video Player
    previewChannel?.let { item ->
        StreamPreviewDialog(
            streamUrl = item.directUrl,
            streamName = item.displayName,
            streamId = item.streamId,
            categoryName = item.categoryName.ifEmpty { "Vault Channel" },
            onDismiss = { previewChannel = null }
        )
    }
}
