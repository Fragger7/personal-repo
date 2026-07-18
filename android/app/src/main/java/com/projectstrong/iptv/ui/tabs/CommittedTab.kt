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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun CommittedTab() {
    val records = CommittedManager.records

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
                text = "Reload from Cloud",
                onClick = { /* Android implementation doesn't currently pull from remote */ },
                modifier = Modifier.fillMaxWidth()
            )
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
                                    Text(text = "Host URL", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text(text = record.baseUrl, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (record.type == "Xtream") {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Username", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                        Text(text = record.user, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Password", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                        Text(text = record.pass, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "MAC Address", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                        Text(text = record.mac, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
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
