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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CommittedTab() {
    val records = CommittedManager.records
    var selectedRecord by remember { mutableStateOf<CommittedRecord?>(null) }
    var isReloading by remember { mutableStateOf(false) }
    var reloadMessage by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()

    AnimatedContent(targetState = selectedRecord != null) { isDetail ->
        if (isDetail && selectedRecord != null) {
            CommittedDetailScreen(
                record = selectedRecord!!,
                onBack = { selectedRecord = null },
                onDelete = { 
                    CommittedManager.delete(selectedRecord!!)
                    selectedRecord = null 
                }
            )
        } else {
            CommittedMasterGrid(
                records = records,
                isReloading = isReloading,
                reloadMessage = reloadMessage,
                onSelectRecord = { selectedRecord = it },
                onReload = {
                    if (isReloading) return@CommittedMasterGrid
                    isReloading = true
                    reloadMessage = "Syncing from Git..."
                    coroutineScope.launch {
                        try {
                            delay(500)
                            val newRecords = withContext(Dispatchers.IO) {
                                CommittedManager.syncFromCloud()
                            }
                            if (newRecords == null) {
                                withContext(Dispatchers.Main) {
                                    reloadMessage = "Sync failed. Check internet."
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    records.clear()
                                    records.addAll(newRecords)
                                    reloadMessage = "Synced ${records.size} items."
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                reloadMessage = "Sync failed."
                            }
                        }
                        isReloading = false
                        delay(2000)
                        reloadMessage = ""
                    }
                }
            )
        }
    }
}

@Composable
fun CommittedMasterGrid(
    records: List<CommittedRecord>,
    isReloading: Boolean,
    reloadMessage: String,
    onSelectRecord: (CommittedRecord) -> Unit,
    onReload: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Committed Data (${records.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (reloadMessage.isNotEmpty()) {
                    Text(reloadMessage, color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall)
                }
            }
            SecondaryButton(
                text = if (isReloading) "Syncing..." else "Reload Cloud",
                onClick = onReload
            )
        }

        if (isReloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF10B981))
        }

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No committed records.", color = Color.Gray)
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
                    GridHeader("Type", 80.dp)
                    GridHeader("Host URL", 250.dp)
                    GridHeader("User / MAC", 160.dp)
                    GridHeader("Notes", 200.dp)
                    GridHeader("Actions", 100.dp)
                }
                
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF333344)))
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecord(record) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(record.safeType, 80.dp)
                            GridCell(record.safeBaseUrl, 250.dp, isBold = true)
                            val authId = if (record.safeType == "Xtream") record.safeUser else record.safeMac
                            GridCell(authId, 160.dp)
                            GridCell(record.safeNotes.ifEmpty { "..." }, 200.dp, color = Color.Gray)
                            
                            IconButton(onClick = { CommittedManager.delete(record) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
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
fun CommittedDetailScreen(record: CommittedRecord, onBack: () -> Unit, onDelete: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var currentNotes by remember(record) { mutableStateOf(record.safeNotes) }

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

        // Host Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("HOST", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.safeBaseUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(record.safeBaseUrl)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (record.safeType == "Xtream") {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("USERNAME", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safeUser, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(record.safeUser)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PASSWORD", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safePass, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(record.safePass)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MAC ADDRESS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.safeMac, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(record.safeMac)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notes Area
        Text("NOTES & ANNOTATIONS", color = Color(0xFFA0A0B0), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = currentNotes,
            onValueChange = { currentNotes = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF333344),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedContainerColor = Color(0xFF12121A),
                focusedContainerColor = Color(0xFF12121A)
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrimaryButton(
                text = "Save Note",
                onClick = {
                    CommittedManager.updateNotes(record, currentNotes)
                    // The record in the list will be updated, this just handles UI logic
                }
            )
        }
    }
}
