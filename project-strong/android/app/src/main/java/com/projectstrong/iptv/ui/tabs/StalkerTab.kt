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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StalkerTab(onNextTab: (() -> Unit)? = null) {
    // Implement chunked/dynamic loading: only show nodes that have finished verifying
    val stalkerNodes = DataStore.scannedNodes.filter { it.type == "Stalker" && (!it.isVerifying && it.status.isNotEmpty()) }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }

    BackHandler(enabled = selectedNode != null) {
        selectedNode = null
    }

    AnimatedContent(targetState = selectedNode != null) { isDetail: Boolean ->
        if (isDetail && selectedNode != null) {
            StalkerDetailScreen(
                node = selectedNode!!,
                onBack = { selectedNode = null }
            )
        } else {
            StalkerMasterGrid(
                nodes = stalkerNodes,
                onSelectNode = { selectedNode = it },
                onNextTab = onNextTab
            )
        }
    }
}

@Composable
fun StalkerMasterGrid(nodes: List<ParsedCredential>, onSelectNode: (ParsedCredential) -> Unit, onNextTab: (() -> Unit)? = null) {
    var sortColumn by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(false) }
    var committingNode by remember { mutableStateOf<ParsedCredential?>(null) }

    val filteredNodes = (if (DataStore.activeOnlyStalker) nodes.filter { it.status.contains("Active", ignoreCase = true) } else nodes)
        .let { list ->
            when (sortColumn) {
                "Live" -> if (sortAscending) list.sortedBy { it.channels.toIntOrNull() ?: -1 } else list.sortedByDescending { it.channels.toIntOrNull() ?: -1 }
                "VODs" -> if (sortAscending) list.sortedBy { it.vods.toIntOrNull() ?: -1 } else list.sortedByDescending { it.vods.toIntOrNull() ?: -1 }
                "Days Left" -> if (sortAscending) list.sortedBy { it.daysLeft.toIntOrNull() ?: -1 } else list.sortedByDescending { it.daysLeft.toIntOrNull() ?: -1 }
                "Host URL" -> if (sortAscending) list.sortedBy { it.baseUrl } else list.sortedByDescending { it.baseUrl }
                "Status" -> if (sortAscending) list.sortedBy { it.status } else list.sortedByDescending { it.status }
                "Provider" -> if (sortAscending) list.sortedBy { it.provider } else list.sortedByDescending { it.provider }
                "MAC" -> if (sortAscending) list.sortedBy { it.mac } else list.sortedByDescending { it.mac }
                "Source Link" -> if (sortAscending) list.sortedBy { it.sourceLink } else list.sortedByDescending { it.sourceLink }
                else -> list
            }
        }
    val scrollState = rememberScrollState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    if (committingNode != null) {
        val node = committingNode!!
        CommitAccountDialog(
            type = "Stalker",
            baseUrl = node.baseUrl,
            mac = node.mac,
            status = node.status,
            expires = node.expires,
            daysLeft = node.daysLeft,
            provider = node.provider,
            serverTimezone = node.serverTimezone,
            sourceLink = node.sourceLink,
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

                // Tier 1: Title and Active Count Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stalker Portals",
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

                // Tier 2: Filter Toolbar with FilterToggleSwitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterToggleSwitch(
                        checked = DataStore.activeOnlyStalker,
                        onCheckedChange = { DataStore.activeOnlyStalker = it },
                        activeCount = activeCount,
                        totalCount = nodes.size
                    )
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
                    Text("No Stalker portals found.", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
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
                                GridHeader("MAC Address", 160.dp, onClick = { headerClick("MAC") }, isSorted = (sortColumn == "MAC"), isAscending = sortAscending)
                                GridHeader("Provider", 150.dp, onClick = { headerClick("Provider") }, isSorted = (sortColumn == "Provider"), isAscending = sortAscending)
                                GridHeader("Timezone", 120.dp, null)
                                GridHeader("Expires", 100.dp, null)
                                GridHeader("Days Left", 100.dp, onClick = { headerClick("Days Left") }, isSorted = (sortColumn == "Days Left"), isAscending = sortAscending)
                                GridHeader("Source Link", 180.dp, onClick = { headerClick("Source Link") }, isSorted = (sortColumn == "Source Link"), isAscending = sortAscending)
                                GridHeader("Actions", 110.dp, null)
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceBorder))

                            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                                items(filteredNodes) { node: ParsedCredential ->
                                    val profile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(node.baseUrl)
                                    val displayBrand = if (profile?.isIdentified == true) profile.cleanBrand else node.provider.ifEmpty { "Unbranded" }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectNode(node) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        GridCell(node.baseUrl, 250.dp, isBold = true)
                                        StatusBadge(node.status, 120.dp)
                                        GridCell(node.mac, 160.dp)
                                        GridCell(displayBrand, 150.dp, color = if (profile?.isIdentified == true) Color(0xFFC084FC) else AppTextPrimary)
                                        GridCell(node.serverTimezone, 120.dp)
                                        GridCell(node.expires, 100.dp)
                                        GridCell(node.daysLeft, 100.dp)
                                        GridCell(node.sourceLink.ifEmpty { "-" }, 180.dp, color = if (node.sourceLink.startsWith("http")) AppPrimary else AppTextMuted)

                                        Row(
                                            modifier = Modifier.width(110.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            GridActionIconButton(
                                                icon = Icons.Default.ContentCopy,
                                                tooltip = "Copy Stalker Credentials",
                                                color = Color(0xFF60A5FA),
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString("${node.baseUrl} / ${node.mac}"))
                                                    ToastManager.success("Copied Stalker credentials to clipboard!")
                                                }
                                            )
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
                                        text = "Continue to Committed Data →",
                                        onClick = { onNextTab?.invoke() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    // Context-aware floating scroller
                    SmartLazyListScroller(
                        listState = listState,
                        itemCount = filteredNodes.size,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun StalkerDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var showCommitDialog by remember { mutableStateOf(false) }
    val detailScrollState = rememberScrollState()

    if (showCommitDialog) {
        CommitAccountDialog(
            type = "Stalker",
            baseUrl = node.baseUrl,
            mac = node.mac,
            status = node.status,
            expires = node.expires,
            daysLeft = node.daysLeft,
            provider = node.provider,
            serverTimezone = node.serverTimezone,
            sourceLink = node.sourceLink,
            onDismiss = { showCommitDialog = false },
            onCommitted = { showCommitDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(detailScrollState)
            .padding(16.dp)
    ) {
        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Stalker Portal Details",
                color = AppTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Provider Intelligence & Forensics Card
        val providerProfile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(node.baseUrl)
        ProviderIntelligenceCard(
            profile = providerProfile,
            baseUrl = node.baseUrl,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Host Info Card
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
                    Text("Portal Credentials", color = AppTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusBadge(node.status, 120.dp)
                }

                CopyableCredentialField(
                    label = "Host Portal URL",
                    value = node.baseUrl,
                    toastMessage = "Copied Host Portal URL to clipboard!",
                    isMonospaceOrPrimary = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CopyableCredentialField(
                        label = "MAC Address",
                        value = node.mac,
                        toastMessage = "Copied MAC Address to clipboard!",
                        modifier = Modifier.weight(1f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TIMEZONE", color = AppTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppSurfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                                Text(node.serverTimezone.ifEmpty { "Default (UTC)" }, color = AppTextPrimary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Commit Account",
                color = AppSuccess,
                onClick = { showCommitDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }

        // Deep Dive Section Notice
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppWarning.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Stalker API Limitations", color = AppWarning, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Deep-dive channel classification and VOD grid streaming is structurally blocked for Stalker Portals due to MAC-driven authentication payload dynamically expiring. Deep-dive discovery is explicitly restricted from accessing these nodes to avoid triggering the target server's firewall banning mechanisms.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}
