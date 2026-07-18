package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun ScannerTab() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<List<com.projectstrong.iptv.network.ParsedCredential>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Multi-Payload Scanner",
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )

        GlassTextField(
            value = input,
            onValueChange = { input = it },
            label = "Paste Unstructured Credentials Block",
            minLines = 4
        )

        GlassButton(
            text = "Parse & Scan",
            onClick = {
                output = Parser.parseCredentials(input)
            }
        )
        
        if (output.isNotEmpty()) {
            Text(
                text = "Discovered Nodes (${output.size})",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(output) { cred ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Type: ${cred.type}", color = Color.White)
                            Text(text = "URL: ${cred.baseUrl}", color = Color.White.copy(alpha=0.7f))
                            if (cred.type == "Xtream") {
                                Text(text = "User: ${cred.user}", color = Color.White.copy(alpha=0.7f))
                                Text(text = "Pass: ${cred.pass}", color = Color.White.copy(alpha=0.7f))
                            } else {
                                Text(text = "MAC: ${cred.mac}", color = Color.White.copy(alpha=0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
