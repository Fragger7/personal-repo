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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun StalkerTab(onNextTab: (() -> Unit)? = null) {
    val stalkerNodes = DataStore.scannedNodes.filter { it.type == "Stalker" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }

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
            onDismiss = { committingNode = null },
            onCommitted = { committingNode = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stalker Portals (${filteredNodes.size}/${nodes.size})",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Text("Active Only", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = DataStore.activeOnlyStalker,
                    onCheckedChange = { DataStore.activeOnlyStalker = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF3B82F6), checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.5f))
                )
            }
        }

        if (filteredNodes.isNotEmpty()) {
            Text(
                text = "Showing ${filteredNodes.size} records.",
                color = Color(0xFFA0A0B0),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (filteredNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Stalker portals found.", color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    Column {
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
                            GridHeader("MAC Address", 160.dp, onClick = { headerClick("MAC") }, isSorted = (sortColumn == "MAC"), isAscending = sortAscending)
                            GridHeader("Provider", 150.dp, onClick = { headerClick("Provider") }, isSorted = (sortColumn == "Provider"), isAscending = sortAscending)
                            GridHeader("Timezone", 120.dp, null)
                            GridHeader("Expires", 100.dp, null)
                            GridHeader("Days Left", 100.dp, onClick = { headerClick("Days Left") }, isSorted = (sortColumn == "Days Left"), isAscending = sortAscending)
                            GridHeader("Actions", 180.dp, null)
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))

                        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                            items(filteredNodes, key = { it.baseUrl + it.mac }) { node: ParsedCredential ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectNode(node) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GridCell(node.baseUrl, 250.dp, isBold = true)
                                    StatusBadge(node.status, 120.dp)
                                    GridCell(node.mac, 160.dp)
                                    GridCell(node.provider, 150.dp)
                                    GridCell(node.serverTimezone, 120.dp)
                                    GridCell(node.expires, 100.dp)
                                    GridCell(node.daysLeft, 100.dp)

                                    Row(modifier = Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SecondaryButton(
                                            text = "Copy",
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("${node.baseUrl} / ${node.mac}"))
                                                ToastManager.success("Copied Stalker credentials to clipboard!")
                                            },
                                            modifier = Modifier.height(36.dp).weight(1f)
                                        )
                                        PrimaryButton(
                                            text = "Commit",
                                            onClick = {
                                                committingNode = node
                                            },
                                            modifier = Modifier.height(36.dp).weight(1f)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                PrimaryButton(
                                    text = "Continue to Committed Data →",
                                    onClick = { onNextTab?.invoke() },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
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
fun StalkerDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var showCommitDialog by remember { mutableStateOf(false) }

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
            onDismiss = { showCommitDialog = false },
            onCommitted = { showCommitDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Stalker Portal Details",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Host Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("HOST PORTAL", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.baseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(node.baseUrl))
                                ToastManager.success("Copied Host Portal URL to clipboard!")
                            }, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    StatusBadge(node.status, 120.dp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MAC ADDRESS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.mac, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(node.mac))
                                ToastManager.success("Copied MAC Address to clipboard!")
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TIMEZONE", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Text(node.serverTimezone, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Commit Account",
                color = Color(0xFF10B981),
                onClick = { showCommitDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Deep Dive Section Notice
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12121A)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Stalker API Limitations", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Deep-dive channel classification and VOD grid streaming is structurally blocked for Stalker Portals due to MAC-driven authentication payload dynamically expiring. Deep-dive discovery is explicitly restricted from accessing these nodes to avoid triggering the target server's firewall banning mechanisms.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
