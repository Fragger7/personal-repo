package com.projectstrong.iptv.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*
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
    DATE_ADDED, TYPE, STATUS, SYNC, HOST, PROVIDER, CHANNELS, VODS, DAYS_LEFT, EXPIRES, SOURCE
}

@Composable
fun CommittedTab() {
    val records = CommittedManager.records
    var selectedRecord by remember { mutableStateOf<CommittedRecord?>(null) }
    var isReloading by remember { mutableStateOf(false) }
    var isPushing by remember { mutableStateOf(false) }
    var isRechecking by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf("") }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showPushConfirmDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf(DataStore.githubToken) }

    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = selectedRecord != null) {
        selectedRecord = null
    }

    // Token Configuration Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Access Token", color = AppTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "To push saved accounts to your GitHub repository, enter a GitHub Personal Access Token (with repo scope). It is securely stored in your device's private sandboxed app storage and used only to communicate directly with GitHub.",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("GITHUB_TOKEN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedContainerColor = AppSurfaceVariant,
                            unfocusedContainerColor = AppSurfaceVariant
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
                            colors = ButtonDefaults.textButtonColors(contentColor = AppError)
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
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Push Confirmation Dialog (Guard against accidental overwrite)
    if (showPushConfirmDialog) {
        val localCount = records.count { it.isLocal }
        AlertDialog(
            onDismissRequest = { showPushConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AppSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Push to GitHub", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You are about to push ${records.size} records to GitHub repository:",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "📁 project-strong/committed.json",
                        color = AppPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (localCount > 0) {
                        Text(
                            "Includes $localCount newly added/modified local accounts that will be merged into the cloud dataset.",
                            color = AppWarning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "🛡️ Safe Sync: Existing remote accounts in the repository will be preserved and merged automatically (never overwritten).",
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
                                actionMessage = "Push failed."
                                // Do not overwrite the detailed toast from CommittedManager
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
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete confirmation state
    var recordToDelete by remember { mutableStateOf<CommittedRecord?>(null) }
    var sourceArchiveViewerData by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (sourceArchiveViewerData != null) {
        val (srcLink, archFile) = sourceArchiveViewerData!!
        SourceArchiveViewerDialog(
            sourceLink = srcLink,
            archiveFileName = archFile,
            onDismiss = { sourceArchiveViewerData = null },
            onSendToScanner = { text ->
                sourceArchiveViewerData = null
                DataStore.scannerInput = text
                ToastManager.success("Loaded source snapshot into Multi-Payload Scanner!")
            }
        )
    }

    if (recordToDelete != null) {
        val rec = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AppError)
                    Text("Delete Saved Account", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to remove this record from your Saved Accounts?",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Host: ${rec.safeBaseUrl}", color = AppTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            if (rec.safeType == "Xtream") {
                                Text("User: ${rec.safeUser}", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("MAC: ${rec.safeMac}", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (DataStore.githubToken.isNotEmpty()) {
                        Text(
                            "☁️ Git Sync: This deletion will immediately update and sync with your GitHub repository (committed.json).",
                            color = AppPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            "⚠️ Local Only: GitHub token is not configured. This record will be removed from local storage.",
                            color = AppWarning,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = recordToDelete!!
                        recordToDelete = null
                        CommittedManager.delete(toDel)
                        if (selectedRecord == toDel) {
                            selectedRecord = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { recordToDelete = null }
                ) {
                    Text("Cancel", color = AppTextSecondary)
                }
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val loadRecords = {
        if (!isReloading && !isPushing && !isRechecking) {
            isReloading = true
            actionMessage = "Syncing with cloud..."
            ToastManager.info("Syncing records from GitHub...")
            coroutineScope.launch {
                val results = withContext(Dispatchers.IO) {
                    CommittedManager.syncFromCloud()
                }
                if (results != null) {
                    actionMessage = "Sync successful."
                    ToastManager.success("Cloud sync complete.")
                } else {
                    actionMessage = "Sync failed."
                    ToastManager.error("Cloud sync failed.")
                }
                isReloading = false
                delay(2500)
                actionMessage = ""
            }
        }
    }

    val onPushAction = {
        if (!isReloading && !isPushing && !isRechecking) {
            if (records.isEmpty()) {
                ToastManager.warning("Cannot push empty list to cloud.")
            } else if (DataStore.githubToken.isEmpty()) {
                tempToken = ""
                showTokenDialog = true
            } else {
                showPushConfirmDialog = true
            }
        }
    }

    val onRecheckStatusAction = {
        if (!isReloading && !isPushing && !isRechecking) {
            if (records.isEmpty()) {
                ToastManager.warning("No records to check.")
            } else {
                isRechecking = true
                actionMessage = "Verifying live status of ${records.size} accounts..."
                ToastManager.info("Starting background status check...")
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        CommittedManager.recheckAllStatus()
                    }
                    actionMessage = "Check complete."
                    ToastManager.success("All accounts verified!")
                    isRechecking = false
                    delay(2500)
                    actionMessage = ""
                }
            }
        }
    }
    
    val onOpenTokenSettingsAction = {
        tempToken = DataStore.githubToken
        showTokenDialog = true
    }

    
    AnimatedContent(
        targetState = selectedRecord,
        modifier = Modifier.fillMaxSize(),
        label = "CommittedScreenTransition"
    ) { activeRecord ->
        if (activeRecord != null) {
            CommittedDetailScreen(
                record = activeRecord,
                onBack = { selectedRecord = null },
                onDelete = { recordToDelete = activeRecord },
                onViewSourceSnapshot = { link, file -> sourceArchiveViewerData = Pair(link, file) },
                onPush = onPushAction
            )
        } else {
            CommittedMasterGrid(
                records = records,
                isBusy = isReloading || isPushing || isRechecking,
                statusMessage = actionMessage,
                onSelectRecord = { selectedRecord = it },
                onViewSourceSnapshot = { link, file -> sourceArchiveViewerData = Pair(link, file) },
                onRefresh = { loadRecords() },
                onPush = onPushAction,
                onRecheckStatus = onRecheckStatusAction,
                onDeleteRecord = { recordToDelete = it },
                onOpenTokenSettings = onOpenTokenSettingsAction
            )
        }
    }

    if (recordToDelete != null) {
        val rec = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AppError)
                    Text("Delete Saved Account", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to remove this record from your Saved Accounts?",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Host: ${rec.safeBaseUrl}", color = AppTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            if (rec.safeType == "Xtream") {
                                Text("User: ${rec.safeUser}", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("MAC: ${rec.safeMac}", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (DataStore.githubToken.isNotEmpty()) {
                        Text(
                            "☁️ Git Sync: This deletion will immediately update and sync with your GitHub repository (committed.json).",
                            color = AppPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text(
                            "⚠️ Local Only: GitHub token is not configured. This record will be removed from local storage.",
                            color = AppWarning,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = recordToDelete!!
                        recordToDelete = null
                        CommittedManager.delete(toDel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { recordToDelete = null }
                ) {
                    Text("Cancel", color = AppTextSecondary)
                }
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

}

@Composable
fun CommittedMasterGrid(
    records: List<CommittedRecord>,
    isBusy: Boolean,
    statusMessage: String,
    onSelectRecord: (CommittedRecord) -> Unit,
    onViewSourceSnapshot: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onPush: () -> Unit,
    onRecheckStatus: () -> Unit,
    onDeleteRecord: (CommittedRecord) -> Unit,
    onOpenTokenSettings: () -> Unit
) {
    var sortColumn by remember { mutableStateOf(CommittedSortColumn.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(false) }

    fun toggleSort(col: CommittedSortColumn) {
        if (sortColumn == col) {
            sortAscending = !sortAscending
        } else {
            sortColumn = col
            sortAscending = true
        }
    }
    
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

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
            CommittedSortColumn.SOURCE -> {
                if (sortAscending) list.sortedBy { it.safeSourceLink } else list.sortedByDescending { it.safeSourceLink }
            }
        }
    }

    val localCount = records.count { it.isLocal }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxSize()) {
        // Control Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isLandscape) 6.dp else 12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isLandscape) 8.dp else 14.dp)) {
                if (isLandscape) {
                    // Landscape Compact Single Row: Title + Status Badge on left, Actions on right
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
                                text = "Committed Accounts (${records.size})",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (localCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppWarning.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppWarning.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "⚠️ $localCount Unpushed",
                                        color = Color(0xFFFBBF24),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else if (records.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AppSuccess.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppSuccess.copy(alpha = 0.35f))
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
                                onClick = onRecheckStatus,
                                modifier = Modifier.height(34.dp)
                            )
                            SecondaryButton(
                                text = "🔄 Sync",
                                onClick = onRefresh,
                                modifier = Modifier.height(34.dp)
                            )
                            PrimaryButton(
                                text = if (localCount > 0) "☁️ Push ($localCount)" else "☁️ Push",
                                color = if (records.isEmpty()) AppTextMuted else AppSuccess,
                                onClick = onPush,
                                modifier = Modifier.height(34.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (DataStore.githubToken.isNotEmpty()) AppPrimary.copy(alpha = 0.15f) else AppSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    if (DataStore.githubToken.isNotEmpty()) AppPrimary.copy(alpha = 0.4f) else AppSurfaceBorder
                                ),
                                modifier = Modifier.clickable { onOpenTokenSettings() }
                            ) {
                                Box(
                                    modifier = Modifier.size(34.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Key,
                                        contentDescription = "Token Settings",
                                        tint = if (DataStore.githubToken.isNotEmpty()) Color(0xFF60A5FA) else AppTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Portrait 2-Tier Layout
                    // Tier 1: Title and Cloud/Sync Status Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Committed Accounts",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Permanent Vault • Showing ${records.size} records",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (localCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppWarning.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppWarning.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "⚠️ $localCount Unpushed",
                                    color = Color(0xFFFBBF24),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (records.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppSuccess.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppSuccess.copy(alpha = 0.35f))
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tier 2: Action Buttons Row with equal balance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SecondaryButton(
                            text = "⚡ Check",
                            onClick = onRecheckStatus,
                            modifier = Modifier.weight(1f).height(38.dp)
                        )
                        SecondaryButton(
                            text = "🔄 Sync",
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f).height(38.dp)
                        )
                        PrimaryButton(
                            text = if (localCount > 0) "☁️ Push ($localCount)" else "☁️ Push",
                            color = if (records.isEmpty()) AppTextMuted else AppSuccess,
                            onClick = onPush,
                            modifier = Modifier.weight(1.2f).height(38.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (DataStore.githubToken.isNotEmpty()) AppPrimary.copy(alpha = 0.15f) else AppSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (DataStore.githubToken.isNotEmpty()) AppPrimary.copy(alpha = 0.4f) else AppSurfaceBorder
                            ),
                            modifier = Modifier.clickable { onOpenTokenSettings() }
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = "Token Settings",
                                    tint = if (DataStore.githubToken.isNotEmpty()) Color(0xFF60A5FA) else AppTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (statusMessage.isNotEmpty()) {
            Text(
                statusMessage,
                color = AppSuccess,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AppSuccess,
                trackColor = AppSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (records.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No committed records saved.", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Save verified connections from the Xtream or Stalker tabs.", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            return
        }

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
                    Column(modifier = Modifier.fillMaxHeight()) {
                        // Full 16-Column Header Row matching Python Dataframe exactly
                        Row(
                            modifier = Modifier
                                .background(AppSurfaceVariant)
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
                            GridHeader("Source Link", 180.dp, onClick = { toggleSort(CommittedSortColumn.SOURCE) }, isSorted = sortColumn == CommittedSortColumn.SOURCE, isAscending = sortAscending)
                            GridHeader("Notes", 200.dp)
                            GridHeader("Actions", 200.dp)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceBorder))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = listState
                        ) {
                            items(sortedRecords, key = { it.safeBaseUrl + it.safeUser + it.safeMac }) { record ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectRecord(record) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Date Added
                                    GridCell(record.safeDateAdded.ifEmpty { "-" }, 140.dp, color = AppTextSecondary)
                                    // 2. Type
                                    StatusBadge(record.safeType, 80.dp)
                                    // 3. Status
                                    StatusBadge(record.safeStatus, 110.dp)
                                    // 4. Sync
                                    SyncBadge(record.isLocal, 110.dp)
                                    // 5. Server / Host URL
                                    GridCell(record.safeBaseUrl, 230.dp, isBold = true)
                                    val profile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(record.safeBaseUrl)
                                    val displayBrand = if (profile?.isIdentified == true) profile.cleanBrand else record.safeProvider.ifEmpty { "Unbranded" }
                                    // 6. Provider
                                    GridCell(displayBrand, 140.dp, color = if (profile?.isIdentified == true) Color(0xFFC084FC) else AppPrimary)
                                    // 7. Username
                                    GridCell(if (record.safeType == "Xtream") record.safeUser.ifEmpty { "-" } else "-", 140.dp)
                                    // 8. Password
                                    GridCell(if (record.safeType == "Xtream") record.safePass.ifEmpty { "-" } else "-", 140.dp, color = AppTextMuted)
                                    // 9. MAC Address
                                    GridCell(if (record.safeType == "Stalker") record.safeMac.ifEmpty { "-" } else "-", 150.dp)
                                    // 10. Channels
                                    GridCell(record.safeChannels.ifEmpty { "-" }, 90.dp)
                                    // 11. VODs
                                    GridCell(record.safeVods.ifEmpty { "-" }, 90.dp)
                                    // 12. Days Left
                                    GridCell(record.safeDaysLeft.ifEmpty { "-" }, 90.dp, isBold = true, color = if (record.safeDaysLeft.toIntOrNull() ?: 0 > 30) AppSuccess else AppWarning)
                                    // 13. Expires
                                    GridCell(record.safeExpires.ifEmpty { "-" }, 110.dp, color = AppTextSecondary)
                                    // 14. Active/Max Conns
                                    val connsStr = if (record.safeActiveConn.isNotEmpty() || record.safeMaxConn.isNotEmpty()) {
                                        "${record.safeActiveConn.ifEmpty { "0" }}/${record.safeMaxConn.ifEmpty { "1" }}"
                                    } else "-"
                                    GridCell(connsStr, 80.dp)
                                    // 15. Timezone
                                    GridCell(record.safeTimezone.ifEmpty { "-" }, 130.dp, color = AppTextMuted)
                                    // 16. Source Link
                                    val hasSource = record.safeSourceLink.isNotEmpty() && record.safeSourceLink != "Direct Ingestion"
                                    val displaySource = if (hasSource) record.safeSourceLink else "-"
                                    GridCell(
                                        text = displaySource,
                                        width = 180.dp,
                                        color = if (hasSource) AppPrimary else AppTextMuted,
                                        onClick = if (hasSource || record.safeSourceArchiveFile.isNotEmpty()) {
                                            { onViewSourceSnapshot(record.safeSourceLink, record.safeSourceArchiveFile) }
                                        } else null
                                    )
                                    // 17. Notes
                                    GridCell(record.safeNotes.ifEmpty { "..." }, 200.dp, color = AppTextSecondary)

                                    // Actions (Push if local, Copy, Copy M3U, Source Snapshot, & Delete)
                                    Row(
                                        modifier = Modifier.width(200.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (record.isLocal) {
                                            GridActionIconButton(
                                                icon = Icons.Default.CloudUpload,
                                                tooltip = "Push to Cloud",
                                                color = AppSuccess,
                                                onClick = { onPush() }
                                            )
                                        }

                                        GridActionIconButton(
                                            icon = Icons.Default.ContentCopy,
                                            tooltip = "Copy Credentials",
                                            color = AppPrimary,
                                            onClick = {
                                                val copyText = if (record.safeType == "Xtream") {
                                                    "Host: ${record.safeBaseUrl}\nUsername: ${record.safeUser}\nPassword: ${record.safePass}"
                                                } else {
                                                    "Host: ${record.safeBaseUrl}\nMAC: ${record.safeMac}"
                                                }
                                                clipboardManager.setText(AnnotatedString(copyText))
                                                ToastManager.success("Copied credentials to clipboard!")
                                            }
                                        )

                                        if (record.safeType == "Xtream" && record.safeUser.isNotEmpty() && record.safePass.isNotEmpty()) {
                                            GridActionIconButton(
                                                icon = Icons.Default.FileDownload,
                                                tooltip = "Copy M3U Playlist Link",
                                                color = Color(0xFFA78BFA),
                                                onClick = {
                                                    val m3uUrl = "${record.safeBaseUrl}/get.php?username=${record.safeUser}&password=${record.safePass}&type=m3u_plus&output=${com.projectstrong.iptv.data.SettingsManager.streamOutputFormat}"
                                                    clipboardManager.setText(AnnotatedString(m3uUrl))
                                                    ToastManager.success("Copied M3U Playlist URL to clipboard!")
                                                }
                                            )
                                        }

                                        if (hasSource || record.safeSourceArchiveFile.isNotEmpty()) {
                                            GridActionIconButton(
                                                icon = Icons.Default.Description,
                                                tooltip = "View Forever Source Archive",
                                                color = Color(0xFF38BDF8),
                                                onClick = { onViewSourceSnapshot(record.safeSourceLink, record.safeSourceArchiveFile) }
                                            )
                                        }

                                        GridActionIconButton(
                                            icon = Icons.Default.Delete,
                                            tooltip = "Delete Record",
                                            color = AppError,
                                            onClick = { onDeleteRecord(record) }
                                        )
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceBorder.copy(alpha = 0.5f)))
                            }
                        }
                    }
                }

                // Context-aware floating scroller
                SmartLazyListScroller(
                    listState = listState,
                    itemCount = sortedRecords.size,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}
@Composable
fun CommittedDetailScreen(
    record: CommittedRecord,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onViewSourceSnapshot: (String, String) -> Unit,
    onPush: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var currentNotes by remember(record) { mutableStateOf(record.safeNotes) }
    var showCatalogExplorer by remember { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()

    if (showCatalogExplorer && record.safeType == "Xtream") {
        FullScreenCatalogExplorer(
            baseUrl = record.safeBaseUrl,
            user = record.safeUser,
            pass = record.safePass,
            title = record.safeBaseUrl,
            onDismiss = { showCatalogExplorer = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(detailScrollState)
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.safeType} Record Details",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppError)
            }
        }

        // Provider Intelligence & Forensics Card
        val providerProfile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(record.safeBaseUrl)
        ProviderIntelligenceCard(
            profile = providerProfile,
            baseUrl = record.safeBaseUrl,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Host & Credentials Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Credentials", color = AppTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(record.safeStatus, 100.dp)
                        SyncBadge(record.isLocal, 90.dp)
                        if (record.isLocal) {
                            IconButton(
                                onClick = onPush,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = "Push to Cloud",
                                    tint = AppSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                CopyableCredentialField(
                    label = "Server / Host URL",
                    value = record.safeBaseUrl,
                    toastMessage = "Host URL copied to clipboard!",
                    isMonospaceOrPrimary = true
                )

                if (record.safeType == "Xtream") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CopyableCredentialField(
                            label = "Username",
                            value = record.safeUser,
                            toastMessage = "Username copied to clipboard!",
                            modifier = Modifier.weight(1f)
                        )
                        CopyableCredentialField(
                            label = "Password",
                            value = record.safePass,
                            toastMessage = "Password copied to clipboard!",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (record.safeUser.isNotEmpty()) {
                        val m3uUrl = "${record.safeBaseUrl.trimEnd('/')}/get.php?username=${record.safeUser}&password=${record.safePass}&type=m3u_plus&output=${com.projectstrong.iptv.data.SettingsManager.streamOutputFormat}"
                        CopyableCredentialField(
                            label = "M3U Playlist URL",
                            value = m3uUrl,
                            toastMessage = "M3U Playlist link copied to clipboard!"
                        )
                    }
                } else {
                    CopyableCredentialField(
                        label = "MAC Address",
                        value = record.safeMac,
                        toastMessage = "MAC address copied to clipboard!"
                    )
                }

                val hasSource = record.safeSourceLink.isNotEmpty() && record.safeSourceLink != "Direct Ingestion"
                val hasOrigin = !record.originLink.isNullOrBlank()
                if (record.safeDateAdded.isNotEmpty() || hasSource || hasOrigin || record.safeSourceArchiveFile.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    if (hasSource) {
                        CopyableCredentialField(
                            label = "ORIGINAL SOURCE LINK (PASTEBIN / PAYLOAD)",
                            value = record.safeSourceLink,
                            toastMessage = "Source link copied to clipboard!"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (hasOrigin) {
                        CopyableCredentialField(
                            label = "ORIGIN FORUM THREAD (REDDIT / CONTEXT)",
                            value = record.originLink ?: "",
                            toastMessage = "Origin thread URL copied to clipboard!"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Forever Source Snapshot card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppPrimary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onViewSourceSnapshot(record.safeSourceLink, record.safeSourceArchiveFile)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📄 Forever Source Snapshot",
                                    color = AppTextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val subtext = if (record.safeSourceArchiveFile.isNotEmpty()) {
                                    "Archived file: ${record.safeSourceArchiveFile}"
                                } else if (hasSource) {
                                    "View or fetch snapshot from source URL"
                                } else {
                                    "Inspect cached raw input snapshot"
                                }
                                Text(
                                    text = subtext,
                                    color = AppTextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = "View ↗",
                                color = AppPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (record.safeDateAdded.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("DATE ADDED: ${record.safeDateAdded}", color = AppTextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Full-screen Channel Explorer if Xtream
        if (record.safeType == "Xtream") {
            // Stream Egress & Ghost Line Inspector Card
            var egressState by remember(record) { mutableStateOf(record.safeEgressStatus) }
            var egressDetailsState by remember(record) { mutableStateOf(record.safeEgressDetails) }
            var isProbingEgress by remember { mutableStateOf(false) }
            val detailScope = rememberCoroutineScope()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Stream Egress & Ghost Line Check",
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val badgeColor = when {
                            egressState.contains("Verified", ignoreCase = true) -> AppSuccess
                            egressState.contains("Ghost", ignoreCase = true) || egressState.contains("456") || egressState.contains("884") -> AppError
                            egressState.contains("Inconclusive", ignoreCase = true) -> AppWarning
                            else -> AppTextSecondary
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = egressState,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (egressDetailsState.isNotBlank()) {
                        Text(
                            text = egressDetailsState,
                            color = if (egressState.contains("Ghost") || egressState.contains("456") || egressState.contains("884")) AppError else AppTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            if (!isProbingEgress) {
                                isProbingEgress = true
                                detailScope.launch {
                                    val updated = CommittedManager.probeEgressForRecord(record)
                                    egressState = updated.safeEgressStatus
                                    egressDetailsState = updated.safeEgressDetails
                                    isProbingEgress = false
                                }
                            }
                        },
                        enabled = !isProbingEgress,
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        if (isProbingEgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probing Stream Channels...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Probe Stream Egress", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Button(
                onClick = { showCatalogExplorer = true },
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🔍 Explore Full Catalog & Channels (Full Screen)", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Notes Area
        Text("NOTES & ANNOTATIONS", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = currentNotes,
            onValueChange = { currentNotes = it },
            placeholder = { Text("Add notes for this account...", color = AppTextMuted) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = AppSurfaceBorder,
                focusedBorderColor = AppPrimary,
                unfocusedTextColor = AppTextPrimary,
                focusedTextColor = AppTextPrimary,
                unfocusedContainerColor = AppSurfaceVariant,
                focusedContainerColor = AppSurfaceVariant
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (record.isLocal) {
                PrimaryButton(
                    text = "Push to Cloud",
                    color = AppSuccess,
                    onClick = onPush
                )
            }
            PrimaryButton(
                text = "Save Note",
                onClick = {
                    CommittedManager.updateNotes(record, currentNotes)
                }
            )
        }
    }
}
