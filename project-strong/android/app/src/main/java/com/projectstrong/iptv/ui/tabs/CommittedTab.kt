package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
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
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

@Composable
fun CommittedTab() {
    var selectedRecord by remember { mutableStateOf<CommittedRecord?>(null) }
    val records = CommittedManager.records
    val coroutineScope = rememberCoroutineScope()
    var isReloading by remember { mutableStateOf(false) }
    var reloadMessage by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // Reset details when selection changes
    LaunchedEffect(selectedRecord) {
        if (selectedRecord != null) {
            val idx = records.indexOf(selectedRecord)
            if (idx >= 0) {
                listState.animateScrollToItem(idx + 1) // +1 for header
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Committed Data",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "${records.size} Saved",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isReloading) {
                Text("Reloading...", color = Color.White.copy(alpha = 0.5f))
            } else {
                Text(reloadMessage, color = Color(0xFF10B981))
            }
            GlassButton(
                text = "Reload from Cloud",
                onClick = {
                    if (isReloading) return@GlassButton
                    isReloading = true
                    reloadMessage = ""
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val client = OkHttpClient()
                                val req = Request.Builder().url("https://raw.githubusercontent.com/Fragger7/personal-repo/main/project-strong/committed.json").build()
                                val res = client.newCall(req).execute()
                                if (res.code == 200) {
                                    val body = res.body?.string() ?: "[]"
                                    val arr = JSONArray(body)
                                    val newRecords = mutableListOf<CommittedRecord>()
                                    for (i in 0 until arr.length()) {
                                        val obj = arr.getJSONObject(i)
                                        newRecords.add(
                                            CommittedRecord(
                                                type = obj.optString("type", "Xtream"),
                                                baseUrl = obj.optString("base_url", ""),
                                                user = obj.optString("username", ""),
                                                pass = obj.optString("password", ""),
                                                mac = obj.optString("mac", ""),
                                                notes = obj.optString("notes", "")
                                            )
                                        )
                                    }
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
                        }
                        isReloading = false
                    }
                }
            )
        }

        // Table / Grid Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No committed records.", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text("Type", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Host", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
                            Text("User / MAC", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp))
                            Text("Notes", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
                            Text("Action", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                        }
                        Divider(color = Color.White.copy(alpha = 0.2f))
                    }
                    
                    itemsIndexed(records) { _, record ->
                        val isSelected = selectedRecord == record
                        Row(
                            modifier = Modifier
                                .background(if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedRecord = record }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(record.type, color = Color.White.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.width(80.dp))
                            Text(record.baseUrl.removePrefix("http://").removePrefix("https://"), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                            val authId = if (record.type == "Xtream") record.user else record.mac
                            Text(authId, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                            Text(record.notes.ifEmpty { "..." }, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(200.dp))
                            IconButton(onClick = { CommittedManager.delete(record) }, modifier = Modifier.width(80.dp).height(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                        Divider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // Deep-Dive Drawer Fixed at Bottom
        AnimatedVisibility(
            visible = selectedRecord != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val record = selectedRecord ?: return@AnimatedVisibility
            var currentNotes by remember(record) { mutableStateOf(record.notes) }
            
            GlassCard(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF3B82F6))
                            Text(
                                text = record.baseUrl.removePrefix("http://").removePrefix("https://"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(onClick = { selectedRecord = null }) {
                            Text("✕", color = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (record.type == "Xtream") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(text = "Copy Host", onClick = { clipboardManager.setText(AnnotatedString(record.baseUrl)) }, modifier = Modifier.weight(1f))
                            GlassButton(text = "Copy User", onClick = { clipboardManager.setText(AnnotatedString(record.user)) }, modifier = Modifier.weight(1f))
                            GlassButton(text = "Copy Pass", onClick = { clipboardManager.setText(AnnotatedString(record.pass)) }, modifier = Modifier.weight(1f))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(text = "Copy Host", onClick = { clipboardManager.setText(AnnotatedString(record.baseUrl)) }, modifier = Modifier.weight(1f))
                            GlassButton(text = "Copy MAC", onClick = { clipboardManager.setText(AnnotatedString(record.mac)) }, modifier = Modifier.weight(1f))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    GlassTextField(
                        value = currentNotes,
                        onValueChange = { currentNotes = it },
                        label = "Notes / Annotations"
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        GlassButton(
                            text = "Save Note",
                            onClick = {
                                val idx = records.indexOf(record)
                                if (idx != -1) {
                                    CommittedManager.updateNotes(record, currentNotes)
                                    selectedRecord = record.copy(notes = currentNotes)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
