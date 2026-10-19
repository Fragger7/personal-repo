package com.projectstrong.iptv.ui.tabs

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.VaultItem
import com.projectstrong.iptv.data.VaultManager
import com.projectstrong.iptv.ui.components.SmartLazyListScroller
import com.projectstrong.iptv.ui.components.StreamPreviewDialog
import com.projectstrong.iptv.ui.components.ToastManager
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTab(
    onLock: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var previewChannel by remember { mutableStateOf<VaultItem?>(null) }
    var renameTarget by remember { mutableStateOf<VaultItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<VaultItem?>(null) }

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

    // Filter items based on search query
    val filteredItems = remember(VaultManager.items.toList(), searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            VaultManager.items.toList()
        } else {
            VaultManager.items.filter { item ->
                item.name.lowercase().contains(query) ||
                item.originalName.lowercase().contains(query) ||
                item.categoryName.lowercase().contains(query) ||
                item.parentPlaylist.lowercase().contains(query) ||
                item.streamId.contains(query)
            }
        }
    }

    // Rename Modal Dialog
    if (renameTarget != null) {
        val item = renameTarget!!
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = AppSurface,
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
                Button(
                    onClick = {
                        VaultManager.renameChannel(item, renameInput)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteTarget != null) {
        val item = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = AppSurface,
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppError)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    // Main Column Container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Action & Status Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Secure Channel Vault",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppPrimary.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${filteredItems.size} Saved",
                                    color = AppPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            text = "Zero-Knowledge AES-256 encrypted channels",
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Manual Cloud Commit / Sync Button
                    IconButton(
                        onClick = {
                            val pwd = VaultManager.currentSessionPassword
                            if (pwd != null) {
                                coroutineScope.launch {
                                    val success = VaultManager.commitToCloud(pwd)
                                    if (success) {
                                        ToastManager.success("Vault synced to GitHub")
                                    } else {
                                        ToastManager.warning("Cloud sync failed (check token)")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppSurfaceVariant)
                    ) {
                        if (VaultManager.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AppPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Sync Cloud",
                                tint = AppPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Lock Vault Button (Closes Vault and returns to previous tab)
                    Button(
                        onClick = {
                            VaultManager.lock()
                            onLock()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppError.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppError.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = AppError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lock Vault",
                            color = AppError,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Search Bar Card
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search channel name, original name, category, host...", color = AppTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppPrimary,
                unfocusedBorderColor = AppSurfaceBorder,
                focusedTextColor = AppTextPrimary,
                unfocusedTextColor = AppTextPrimary,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            )
        )

        // Enterprise Data Table Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = AppTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No channels match your search" else "No channels in Vault yet",
                            color = AppTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search query" else "Open the Catalog Explorer on any connection and tap the 🔒 icon next to any channel to save it here.",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val horizontalScrollState = rememberScrollState()

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Table Headers
                        Row(
                            modifier = Modifier
                                .background(AppSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GridHeader("Date Added", 130.dp)
                            GridHeader("Channel Name", 200.dp)
                            GridHeader("Original Name", 180.dp)
                            GridHeader("Stream ID", 90.dp)
                            GridHeader("Parent Playlist / Host", 210.dp)
                            GridHeader("Category", 140.dp)
                            GridHeader("Sync Status", 110.dp)
                            GridHeader("Actions", 170.dp)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(AppSurfaceBorder)
                        )

                        // Data Rows
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = listState
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Date Added
                                    GridCell(item.dateAdded.ifEmpty { "-" }, 130.dp, color = AppTextSecondary)

                                    // 2. Channel Name (Display Name + Quick edit indicator)
                                    Row(
                                        modifier = Modifier.width(200.dp).padding(end = 8.dp),
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

                                    // 3. Original Name
                                    GridCell(item.originalName.ifEmpty { "-" }, 180.dp, color = AppTextSecondary)

                                    // 4. Stream ID
                                    GridCell(item.streamId, 90.dp, color = AppPrimary, isBold = true)

                                    // 5. Parent Playlist / Host
                                    GridCell(item.parentPlaylist.ifEmpty { item.baseUrl }, 210.dp, color = AppTextPrimary)

                                    // 6. Category
                                    Box(modifier = Modifier.width(140.dp).padding(end = 8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppPrimary.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = item.categoryName.ifEmpty { "Live" },
                                                color = AppPrimary,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // 7. Sync Status
                                    Box(modifier = Modifier.width(110.dp).padding(end = 8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (item.isSynced) AppSuccessContainer else AppSurfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (item.isSynced) "🟢 Synced" else "🟡 Pending",
                                                color = if (item.isSynced) AppSuccess else AppTextMuted,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // 8. Actions
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
                                            modifier = Modifier.size(32.dp)
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
                                            modifier = Modifier.size(32.dp)
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
                                            modifier = Modifier.size(32.dp)
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

                    SmartLazyListScroller(
                        listState = listState,
                        itemCount = filteredItems.size,
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

@Composable
private fun GridHeader(
    title: String,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = AppTextSecondary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun GridCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = AppTextPrimary,
    isBold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
