package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.PrimaryButton
import com.projectstrong.iptv.ui.components.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Query external IP info if empty
    LaunchedEffect(Unit) {
        if (DataStore.ipInfo.isEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder().url("http://ip-api.com/json/").build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: "{}"
                    val json = org.json.JSONObject(body)
                    val isp = json.optString("isp", "Unknown ISP")
                    val org = json.optString("org", "")
                    DataStore.ipInfo = "Connected via $isp"
                    val clouds = listOf("amazon", "aws", "google", "azure", "cloudflare", "digitalocean", "linode", "hetzner", "ovh")
                    DataStore.isCloudHosting = clouds.any { isp.lowercase().contains(it) || org.lowercase().contains(it) }
                } catch (e: Exception) {
                    DataStore.ipInfo = "DISCONNECTED / UNKNOWN"
                    DataStore.isCloudHosting = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Multi-Payload Scanner",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (DataStore.ipInfo.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (DataStore.isCloudHosting) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(DataStore.ipInfo, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (DataStore.isCloudHosting) {
                        Text(
                            "Warning: Cloud hosting IP detected. Many IPTV providers block public cloud IP ranges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = DataStore.scannerInput,
            onValueChange = { DataStore.scannerInput = it },
            label = { Text("Paste Raw Unstructured Credentials / M3U Links", color = Color(0xFFA0A0B0)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF333344),
                focusedContainerColor = Color(0xFF12121A),
                unfocusedContainerColor = Color(0xFF12121A)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 6
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = if (DataStore.isScanning) "Stop Scan" else "Parse & Scan Data",
                onClick = {
                    if (DataStore.isScanning) {
                        DataStore.isScanning = false
                        DataStore.scanCountText = "Scan Stopped."
                        return@PrimaryButton
                    }
                    
                    val parsed = Parser.parseCredentials(DataStore.scannerInput)
                    DataStore.scannedNodes.clear()
                    DataStore.scannedNodes.addAll(parsed)
                    
                    if (parsed.isEmpty()) {
                        DataStore.scanCountText = "No credentials found."
                        return@PrimaryButton
                    }
                    
                    DataStore.isScanning = true
                    DataStore.scanProgress = 0f
                    DataStore.scanCountText = "Found " + parsed.size + " credentials. Starting handshake..."
                    
                    DataStore.scanScope.launch {
                        val total = parsed.size
                        var completed = 0
                        val chunkSize = 15
                        val chunks = parsed.chunked(chunkSize)
                        
                        for (chunk in chunks) {
                            if (!DataStore.isScanning) break
                            
                            coroutineScope {
                                chunk.map { node: ParsedCredential ->
                                    async(Dispatchers.IO) {
                                        withContext(Dispatchers.Main) {
                                            val idx = DataStore.scannedNodes.indexOf(node)
                                            if (idx != -1) {
                                                DataStore.scannedNodes[idx] = node.copy(isVerifying = true, status = "Connecting...")
                                            }
                                        }
                                        
                                        val result = if (node.type == "Xtream") {
                                            IPTVClient.verifyXtream(node.baseUrl, node.user, node.pass)
                                        } else {
                                            IPTVClient.verifyStalker(node.baseUrl, node.mac)
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.mac == node.mac && it.type == node.type }
                                            if (newIdx != -1) {
                                                if (result is VerificationResult.Success) {
                                                    DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(
                                                        isVerifying = false, 
                                                        status = result.status, 
                                                        details = result.details, 
                                                        expires = result.expires, 
                                                        daysLeft = result.daysLeft, 
                                                        activeConn = result.activeConn, 
                                                        maxConn = result.maxConn, 
                                                        serverTimezone = result.serverTimezone, 
                                                        serverTime = result.serverTime
                                                    )
                                                } else if (result is VerificationResult.Failed) {
                                                    DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.reason)
                                                }
                                            }
                                            completed++
                                            DataStore.scanProgress = completed.toFloat() / total.toFloat()
                                            DataStore.scanCountText = "Processed $completed/$total connections..."
                                        }
                                    }
                                }.awaitAll()
                            }
                        }
                        if (DataStore.isScanning) {
                            DataStore.isScanning = false
                            val activeCount = DataStore.scannedNodes.count { it.status.contains("Active", ignoreCase = true) }
                            DataStore.scanCountText = "Scan Complete! Found $activeCount active nodes out of " + parsed.size + " total."
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )

            SecondaryButton(
                text = "Clear",
                onClick = {
                    DataStore.scannerInput = ""
                    DataStore.scannedNodes.clear()
                    DataStore.scanProgress = 0f
                    DataStore.scanCountText = ""
                }
            )
        }

        if (DataStore.scanCountText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = DataStore.scanCountText,
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (DataStore.isScanning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { DataStore.scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF3B82F6),
                    trackColor = Color(0xFF1E293B)
                )
            }
        }
    }
}
