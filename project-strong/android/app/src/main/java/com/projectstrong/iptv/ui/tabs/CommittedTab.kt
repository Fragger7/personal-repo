package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun CommittedTab() {
    val records = CommittedManager.records
    val coroutineScope = rememberCoroutineScope()
    var isReloading by remember { mutableStateOf(false) }
    var reloadMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Committed Data",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "${records.size} Records",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth()) {
            GlassButton(
                text = if (isReloading) "Reloading..." else "Reload from Cloud",
                onClick = {
                    if (isReloading) return@GlassButton
                    isReloading = true
                    reloadMessage = ""
                    coroutineScope.launch {
                        try {
                            val client = OkHttpClient()
                            val request = Request.Builder().url("https://raw.githubusercontent.com/Fragger7/personal-repo/main/project-strong/committed.json").build()
                            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                            val body = response.body?.string()
                            if (response.isSuccessful && body != null) {
                                val array = JSONArray(body)
                                val newRecords = mutableListOf<com.projectstrong.iptv.data.CommittedRecord>()
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    newRecords.add(com.projectstrong.iptv.data.CommittedRecord(
                                        type = obj.optString("type", "Xtream"),
                                        baseUrl = obj.optString("base_url", ""),
                                        user = obj.optString("username", ""),
                                        pass = obj.optString("password", ""),
                                        mac = obj.optString("mac", ""),
                                        notes = obj.optString("Notes", obj.optString("notes", ""))
                                    ))
                                }
                                newRecords.forEach { CommittedManager.commit(it) }
                                reloadMessage = "Synced successfully!"
                            } else {
                                reloadMessage = "Failed to fetch from cloud."
                            }
                        } catch(e: Exception) {
                            reloadMessage = "Error: ${e.message}"
                            e.printStackTrace()
                        }
                        isReloading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (reloadMessage.isNotEmpty()) {
            Text(text = reloadMessage, color = Color(0xFF10B981), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No committed records found.",
                            color = Color.White.copy(alpha=0.5f)
                        )
                    }
                }
            } else {
                items(records) { record ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = "Record", tint = if (record.type == "Xtream") Color(0xFF3B82F6) else Color(0xFF8B5CF6))
                                    Text(
                                        text = record.baseUrl.removePrefix("http://").removePrefix("https://"),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(onClick = { CommittedManager.delete(record) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha=0.8f))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    com.projectstrong.iptv.ui.components.GlassTextField(value = record.baseUrl, onValueChange = {}, label = "Host URL", minLines = 1)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (record.type == "Xtream") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = record.user, onValueChange = {}, label = "Username", minLines = 1)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = record.pass, onValueChange = {}, label = "Password", minLines = 1)
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        com.projectstrong.iptv.ui.components.GlassTextField(value = record.mac, onValueChange = {}, label = "MAC Address", minLines = 1)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            GlassTextField(
                                value = record.notes,
                                onValueChange = { newText ->
                                    CommittedManager.updateNotes(record, newText)
                                },
                                label = "Custom Notes / Labels",
                                minLines = 2,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        }
    }
}
