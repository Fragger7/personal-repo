package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun StalkerTab() {
    val stalkerNodes = DataStore.scannedNodes.filter { it.type == "Stalker" }
    var selectedNode by remember { mutableStateOf<ParsedCredential?>(null) }

    AnimatedContent(targetState = selectedNode != null) { isDetail ->
        if (isDetail && selectedNode != null) {
            StalkerDetailScreen(
                node = selectedNode!!,
                onBack = { selectedNode = null }
            )
        } else {
            StalkerMasterGrid(
                nodes = stalkerNodes,
                onSelectNode = { selectedNode = it }
            )
        }
    }
}

@Composable
fun StalkerMasterGrid(nodes: List<ParsedCredential>, onSelectNode: (ParsedCredential) -> Unit) {
    var showActiveOnly by remember { mutableStateOf(false) }
    val filteredNodes = if (showActiveOnly) nodes.filter { it.status.contains("Active", ignoreCase = true) } else nodes
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stalker Portals (${nodes.size})",
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
                Text("No Stalker portals found.", color = Color.Gray)
            }
            return
        }

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
                    GridHeader("MAC Address", 160.dp)
                    GridHeader("Provider", 150.dp)
                    GridHeader("Timezone", 120.dp)
                    GridHeader("Actions", 180.dp)
                }
                
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            GridCell(node.mac, 160.dp)
                            GridCell("Unknown", 150.dp)
                            GridCell(node.serverTimezone, 120.dp)
                            
                            Row(modifier = Modifier.width(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton(
                                    text = "Copy",
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString("${node.baseUrl} / ${node.mac}"))
                                    },
                                    modifier = Modifier.height(36.dp)
                                )
                                PrimaryButton(
                                    text = "Commit",
                                    onClick = {
                                        CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                                    },
                                    modifier = Modifier.height(36.dp)
                                )
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222233)))
                    }
                }
            }
        }
    }
}

@Composable
fun StalkerDetailScreen(node: ParsedCredential, onBack: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current

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
                        Text(node.baseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    StatusBadge(node.status, 120.dp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MAC ADDRESS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.mac, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(node.mac)) }, modifier = Modifier.size(24.dp)) {
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
                onClick = {
                    CommittedManager.commit(CommittedRecord(type = node.type, baseUrl = node.baseUrl, user = node.user, pass = node.pass, mac = node.mac, notes = ""))
                },
                modifier = Modifier.weight(1f)
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
