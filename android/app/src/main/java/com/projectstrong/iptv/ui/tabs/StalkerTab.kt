package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard

@Composable
fun StalkerTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stalker Portals",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "0 Nodes",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF8B5CF6))
                            Text(
                                text = "Sample Provider (Stalker)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "URL: http://example-stalker.com/c/", color = Color.White.copy(alpha=0.7f))
                        Text(text = "MAC: 00:1A:79:AB:CD:EF", color = Color.White.copy(alpha=0.7f))
                        Text(text = "Status: Pending Handshake", color = Color(0xFFF59E0B))
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassButton(
                                text = "Verify",
                                onClick = { },
                                modifier = Modifier.weight(1f)
                            )
                            GlassButton(
                                text = "Commit",
                                onClick = { },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scan nodes in the Scanner tab to populate.",
                        color = Color.White.copy(alpha=0.5f)
                    )
                }
            }
        }
    }
}
