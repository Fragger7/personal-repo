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
import com.projectstrong.iptv.data.FavoriteItem
import com.projectstrong.iptv.data.FavoritesManager
import com.projectstrong.iptv.data.PlayerScope
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.state.LocalStreamPreviewState
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FavoriteSortColumn {
    DATE_ADDED, NAME, STATUS, SYNC, ORIGINAL_NAME, CATEGORY, HOST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesTab() {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val streamPreviewState = LocalStreamPreviewState.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf(emptySet<String>()) }
    var filterPlaylist by remember { mutableStateOf(emptySet<String>()) }
    var filterStatus by remember { mutableStateOf(emptySet<String>()) }
    var filterSync by remember { mutableStateOf(emptySet<String>()) }

    var sortColumn by remember { mutableStateOf(FavoriteSortColumn.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(false) }

    fun toggleSort(col: FavoriteSortColumn) {
        if (sortColumn == col) {
            sortAscending = !sortAscending
        } else {
            sortColumn = col
            sortAscending = true
        }
    }

    // Row selection and Multi-select state
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    val checkedItemIds = remember { mutableStateListOf<String>() }

    var renameTarget by remember { mutableStateOf<FavoriteItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<FavoriteItem?>(null) }
    var showPushConfirmDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    var isRechecking by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf("") }

    val isBusy = isRechecking || isRefreshing || isPushing || FavoritesManager.isSyncing

    val items = FavoritesManager.items
    val unpushedCount = items.count { !it.isSynced }

    // Predicates for cascading filter options
    val matchSearch = { item: FavoriteItem ->
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

    val matchCategory = { item: FavoriteItem ->
        if (filterCategory.isEmpty()) true else {
            val hasNone = filterCategory.contains("None")
            val cat = item.categoryName.trim()
            (hasNone && cat.isEmpty()) || filterCategory.contains(cat)
        }
    }

    val matchPlaylist = { item: FavoriteItem ->
        if (filterPlaylist.isEmpty()) true else {
            val pl = item.parentPlaylist.ifBlank { item.baseUrl }.trim()
            filterPlaylist.contains(pl)
        }
    }

    val matchStatus = { item: FavoriteItem ->
        if (filterStatus.isEmpty()) true else {
            filterStatus.contains(item.status)
        }
    }

    val matchSync = { item: FavoriteItem ->
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
            FavoriteSortColumn.DATE_ADDED -> {
                if (sortAscending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            }
            FavoriteSortColumn.NAME -> {
                if (sortAscending) list.sortedBy { it.displayName.lowercase() } else list.sortedByDescending { it.displayName.lowercase() }
            }
            FavoriteSortColumn.STATUS -> {
                if (sortAscending) list.sortedBy { it.status } else list.sortedByDescending { it.status }
            }
            FavoriteSortColumn.SYNC -> {
                if (sortAscending) list.sortedBy { it.isSynced } else list.sortedByDescending { it.isSynced }
            }
            FavoriteSortColumn.ORIGINAL_NAME -> {
                if (sortAscending) list.sortedBy { it.originalName.lowercase() } else list.sortedByDescending { it.originalName.lowercase() }
            }
            FavoriteSortColumn.CATEGORY -> {
                if (sortAscending) list.sortedBy { it.categoryName.lowercase() } else list.sortedByDescending { it.categoryName.lowercase() }
            }
            FavoriteSortColumn.HOST -> {
                if (sortAscending) list.sortedBy { it.parentPlaylist.ifBlank { it.baseUrl }.lowercase() } else list.sortedByDescending { it.parentPlaylist.ifBlank { it.baseUrl }.lowercase() }
            }
        }
    }

    val onRecheckStatusAction = {
        if (!isBusy) {
            if (items.isEmpty()) {
                ToastManager.warning("No channels in Favorites to check.")
            } else {
                isRechecking = true
                actionMessage = "Verifying live stream status across ${items.size} channels..."
                ToastManager.info("Probing favorite channel status...")
                coroutineScope.launch {
                    val count = FavoritesManager.recheckAllStatus()
                    actionMessage = "Check complete ($count channels verified)."
                    ToastManager.success("Favorites channels verified successfully!")
                    isRechecking = false
                    delay(2500)
                    actionMessage = ""
                }
            }
        }
    }

    val onRefreshAction = {
        if (!isBusy) {
            if (DataStore.githubToken.isEmpty()) {
                ToastManager.warning("Configure GitHub Token in Settings to sync with cloud.")
            } else {
                isRefreshing = true
                actionMessage = "Syncing Favorites from GitHub..."
                ToastManager.info("Pulling latest Favorites from cloud...")
                coroutineScope.launch {
                    val res = FavoritesManager.syncFromCloud()
                    if (res.isSuccess) {
                        actionMessage = "Sync complete (${res.getOrNull()} channels)."
                        ToastManager.success("Favorites synced with cloud!")
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
                ToastManager.warning("Cannot push empty Favorites to cloud.")
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
                    Text("Push Favorites to GitHub", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You are about to push ${items.size} favorites channels to GitHub:",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "📁 project-strong/favorites.json",
                        color = AppPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (unpushedCount > 0) {
                        Text(
                            "Includes $unpushedCount newly added or modified channel(s).",
                            color = AppWarning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "🔒 End-to-End Encrypted: Raw stream URLs and credentials are AES-256 encrypted using an internal key before cloud persistence.",
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
                        isPushing = true
                        actionMessage = "Pushing Favorites to GitHub..."
                        ToastManager.info("Pushing ${items.size} favorites to GitHub...")
                        coroutineScope.launch {
                            val success = FavoritesManager.commitToCloud()
                            if (success) {
                                actionMessage = "Push successful! Favorites backed up to cloud."
                                ToastManager.success("Favorites successfully synced to GitHub!")
                            } else {
                                actionMessage = "Push failed. Check GitHub token."
                                ToastManager.error("Push failed. Check token permissions.")
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
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        val count = checkedItemIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AppError)
                    Text("Delete $count Selected Channels?", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Are you sure you want to permanently remove $count channels from your Favorites?",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        FavoritesManager.bulkDeleteChannels(checkedItemIds.toList())
                        checkedItemIds.clear()
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete ($count)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showBulkDeleteDialog = false }
                )
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
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFF59E0B))
                    Text("Rename Favorite Channel", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Set a custom display name for this channel in your Favorites.",
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
                            focusedBorderColor = Color(0xFFF59E0B),
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
                Button(
                    onClick = {
                        FavoritesManager.renameChannel(item, renameInput)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
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
                    Text("Remove from Favorites?", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Are you sure you want to remove '${item.displayName}' from your Favorites?",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        FavoritesManager.deleteChannel(item)
                        checkedItemIds.remove(item.id)
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
        // Top Action & Status Control Bar
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
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Favorite Channels (${items.size})",
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
                        }
                    }
                } else {
                    // Portrait 2-Tier Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    text = "Favorite Channels",
                                    color = AppTextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Instant Access • Showing ${sortedItems.size} of ${items.size}",
                                    color = AppTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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
                            modifier = Modifier.weight(1.3f).height(38.dp)
                        )
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
                color = Color(0xFFF59E0B),
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
                focusedBorderColor = Color(0xFFF59E0B),
                unfocusedBorderColor = AppSurfaceBorder,
                focusedTextColor = AppTextPrimary,
                unfocusedTextColor = AppTextPrimary,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            )
        )

        // Filter Dropdowns Row
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
        }

        // Multi-Select Floating Bulk Action Bar (When 1+ channels are selected)
        if (checkedItemIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppSurfaceVariant,
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${checkedItemIds.size} Selected",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(
                            onClick = { checkedItemIds.clear() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Deselect All", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bulk Status Check
                        SecondaryButton(
                            text = "⚡ Check (${checkedItemIds.size})",
                            onClick = {
                                val targets = items.filter { checkedItemIds.contains(it.id) }
                                if (targets.isNotEmpty()) {
                                    coroutineScope.launch {
                                        isRechecking = true
                                        actionMessage = "Checking status for ${targets.size} favorites..."
                                        ToastManager.info("Probing ${targets.size} channels...")
                                        val count = FavoritesManager.recheckStatusForItems(targets)
                                        isRechecking = false
                                        ToastManager.success("Checked $count favorites")
                                        delay(2000)
                                        actionMessage = ""
                                    }
                                }
                            },
                            modifier = Modifier.height(34.dp)
                        )

                        // Bulk Push to Cloud
                        PrimaryButton(
                            text = "☁️ Push",
                            color = AppSuccess,
                            onClick = onPushAction,
                            modifier = Modifier.height(34.dp)
                        )

                        // Bulk Delete Button
                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppError),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete (${checkedItemIds.size})", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else if (items.isNotEmpty()) {
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
                            Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B).copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterCategory.isNotEmpty() || filterPlaylist.isNotEmpty() || filterStatus.isNotEmpty() || filterSync.isNotEmpty()) {
                                "No channels match your filters"
                            } else {
                                "No favorites added yet"
                            },
                            color = AppTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterCategory.isNotEmpty() || filterPlaylist.isNotEmpty() || filterStatus.isNotEmpty() || filterSync.isNotEmpty()) {
                                "Try clearing your filters or search query."
                            } else {
                                "Open the Catalog Explorer on any connection and tap the ⭐ icon next to any channel to bookmark it here."
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
                            // Table Headers: Checkbox, Date Added, Channel Name, Actions, Status, Sync, Original Name, Category, Parent Playlist / Host
                            Row(
                                modifier = Modifier
                                    .background(AppSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Multi-select Checkbox Header
                                val allChecked = sortedItems.isNotEmpty() && sortedItems.all { checkedItemIds.contains(it.id) }
                                Box(
                                    modifier = Modifier.width(48.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Checkbox(
                                        checked = allChecked,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                checkedItemIds.clear()
                                                checkedItemIds.addAll(sortedItems.map { it.id })
                                            } else {
                                                checkedItemIds.clear()
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFFF59E0B),
                                            uncheckedColor = AppTextMuted,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                }

                                GridHeader("Date Added", 130.dp, onClick = { toggleSort(FavoriteSortColumn.DATE_ADDED) }, isSorted = sortColumn == FavoriteSortColumn.DATE_ADDED, isAscending = sortAscending)
                                GridHeader("Channel Name", 210.dp, onClick = { toggleSort(FavoriteSortColumn.NAME) }, isSorted = sortColumn == FavoriteSortColumn.NAME, isAscending = sortAscending)
                                GridHeader("Actions", 230.dp)
                                GridHeader("Status", 110.dp, onClick = { toggleSort(FavoriteSortColumn.STATUS) }, isSorted = sortColumn == FavoriteSortColumn.STATUS, isAscending = sortAscending)
                                GridHeader("Sync", 110.dp, onClick = { toggleSort(FavoriteSortColumn.SYNC) }, isSorted = sortColumn == FavoriteSortColumn.SYNC, isAscending = sortAscending)
                                GridHeader("Original Name", 190.dp, onClick = { toggleSort(FavoriteSortColumn.ORIGINAL_NAME) }, isSorted = sortColumn == FavoriteSortColumn.ORIGINAL_NAME, isAscending = sortAscending)
                                GridHeader("Category", 140.dp, onClick = { toggleSort(FavoriteSortColumn.CATEGORY) }, isSorted = sortColumn == FavoriteSortColumn.CATEGORY, isAscending = sortAscending)
                                GridHeader("Parent Playlist / Host", 220.dp, onClick = { toggleSort(FavoriteSortColumn.HOST) }, isSorted = sortColumn == FavoriteSortColumn.HOST, isAscending = sortAscending)
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
                                    val isRowChecked = checkedItemIds.contains(item.id)
                                    val isRowHighlighted = item.id == selectedItemId

                                    val rowBg = when {
                                        isRowChecked -> Color(0xFFF59E0B).copy(alpha = 0.22f)
                                        isRowHighlighted -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg)
                                            .clickable {
                                                if (checkedItemIds.isNotEmpty()) {
                                                    if (isRowChecked) checkedItemIds.remove(item.id)
                                                    else checkedItemIds.add(item.id)
                                                } else {
                                                    selectedItemId = if (selectedItemId == item.id) null else item.id
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 0. Row Checkbox
                                        Box(
                                            modifier = Modifier.width(48.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Checkbox(
                                                checked = isRowChecked,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked) checkedItemIds.add(item.id)
                                                    else checkedItemIds.remove(item.id)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFFF59E0B),
                                                    uncheckedColor = AppTextMuted,
                                                    checkmarkColor = Color.Black
                                                )
                                            )
                                        }

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

                                        // 3. Actions Column (Directly to the right of Channel Name)
                                        Row(
                                            modifier = Modifier.width(230.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Play Stream (Uses root-level overlay for true full-screen parity)
                                            GridActionIconButton(
                                                icon = Icons.Default.PlayArrow,
                                                tooltip = "Play Stream",
                                                color = AppSuccess,
                                                onClick = {
                                                    streamPreviewState.show(
                                                        streamUrl = item.directUrl,
                                                        streamName = item.displayName,
                                                        streamId = item.streamId,
                                                        categoryName = item.categoryName.ifEmpty { "Favorite Channel" },
                                                        scope = PlayerScope.FAVORITES
                                                    )
                                                }
                                            )

                                            // 2. Push / Sync to Cloud
                                            GridActionIconButton(
                                                icon = if (item.isSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                                tooltip = if (item.isSynced) "Synced with Cloud" else "Push to Cloud",
                                                color = if (item.isSynced) AppSuccess else Color(0xFFFBBF24),
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isPushing = true
                                                        actionMessage = "Syncing '${item.displayName}' with Cloud..."
                                                        val ok = FavoritesManager.commitToCloud()
                                                        isPushing = false
                                                        if (ok) {
                                                            ToastManager.success("Synced '${item.displayName}' to Cloud")
                                                        } else {
                                                            ToastManager.error("Push failed. Check GitHub Token.")
                                                        }
                                                        delay(2000)
                                                        actionMessage = ""
                                                    }
                                                }
                                            )

                                            // 3. Rename Channel
                                            GridActionIconButton(
                                                icon = Icons.Default.Edit,
                                                tooltip = "Rename Channel",
                                                color = Color(0xFFF59E0B),
                                                onClick = {
                                                    renameInput = item.displayName
                                                    renameTarget = item
                                                }
                                            )

                                            // 4. Copy Direct Stream URL
                                            GridActionIconButton(
                                                icon = Icons.Default.Link,
                                                tooltip = "Copy Direct Stream URL",
                                                color = Color(0xFFA78BFA),
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(item.directUrl))
                                                    ToastManager.success("Copied Direct Stream URL")
                                                }
                                            )

                                            // 5. Delete Channel
                                            GridActionIconButton(
                                                icon = Icons.Default.Delete,
                                                tooltip = "Remove from Favorites",
                                                color = AppError,
                                                onClick = { deleteTarget = item }
                                            )
                                        }

                                        // 4. Status Badge
                                        StatusBadge(item.status, 110.dp)

                                        // 5. Sync Badge
                                        SyncBadge(!item.isSynced, 110.dp)

                                        // 6. Original Name
                                        GridCell(item.originalName.ifEmpty { "-" }, 190.dp, color = AppTextSecondary)

                                        // 7. Category (Immediately after Original Name)
                                        GridCell(item.categoryName.ifEmpty { "-" }, 140.dp, color = AppTextSecondary)

                                        // 8. Parent Playlist / Host
                                        GridCell(item.parentPlaylist.ifEmpty { item.baseUrl }, 220.dp, color = AppTextPrimary)
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
}
