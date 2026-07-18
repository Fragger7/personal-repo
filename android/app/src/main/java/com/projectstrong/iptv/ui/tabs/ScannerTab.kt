package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun ScannerTab() {
    var input by remember { mutableStateOf("") }
    val output = DataStore.scannedNodes

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Multi-Payload Scanner",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Paste messy, unstructured text blocks containing Xtream Codes or Stalker Portals credentials. The parser will extract all readable accounts.",
                color = Color.White.copy(alpha = 0.7f),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            GlassTextField(
                value = input,
                onValueChange = { input = it },
                label = "Paste Unstructured Credentials Block",
                minLines = 8,
                modifier = Modifier.heightIn(min = 180.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            GlassButton(
                text = "Parse & Scan Data",
                onClick = {
                    val parsed = Parser.parseCredentials(input)
                    DataStore.scannedNodes.clear()
                    DataStore.scannedNodes.addAll(parsed)
                }
            )
        }
        
        item {
            AnimatedVisibility(
                visible = output.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(
                        text = "Discovered Nodes",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Found ${output.size} valid credentials.",
                        color = Color(0xFF3B82F6),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
            
        if (output.isNotEmpty()) {
            items(output) { cred ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cred.type, 
                                color = if (cred.type == "Xtream") Color(0xFF3B82F6) else Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Host", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        Text(text = cred.baseUrl, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        if (cred.type == "Xtream") {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Username", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text(text = cred.user, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Password", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    Text(text = cred.pass, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                }
                            }
                        } else {
                            Text(text = "MAC Address", color = Color.White.copy(alpha=0.5f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            Text(text = cred.mac, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
