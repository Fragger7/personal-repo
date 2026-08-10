package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll

@Composable
fun ScannerTab(onNextTab: () -> Unit = {}) {
    val clipboardManager = LocalClipboardManager.current
    
    LaunchedEffect(Unit) {
        if (DataStore.ipInfo.isEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url("http://ip-api.com/json/").build()
                    val response = client.newCall(request).execute()
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val isp = json.optString("isp", "Unknown ISP")
                    val org = json.optString("org", "")
                    DataStore.ipInfo = "Connected via $isp"
                    val combined = "$isp $org".lowercase()
                    val cloudProviders = listOf("amazon", "aws", "google", "azure", "cloudflare", "digitalocean")
                    DataStore.showVpnWarning = !combined.contains("vpn") && !combined.contains("proxy") && !combined.contains("mullvad") && !combined.contains("nord")
                } catch (e: Exception) {
                    DataStore.ipInfo = "VPN / Connection Unknown"
                }
            }
        }
    }

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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Paste messy, unstructured text blocks containing Xtream Codes or Stalker Portals credentials. The parser will extract all readable accounts.",
                color = Color(0xFFA0A0B0),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = DataStore.scannerInput,
                onValueChange = { DataStore.scannerInput = it },
                label = { Text("Paste Unstructured Credentials Block") },
                minLines = 8,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
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
            
            if (DataStore.showVpnWarning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(DataStore.ipInfo, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                            Text("WARNING: You may not be using a VPN. Public ISPs often block IPTV portals. You can proceed, but results may fail.", color = Color(0xFFF59E0B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text(text = "🛡️ ${DataStore.ipInfo}", color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
            }
            
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
                        DataStore.scanCountText = "Found ${parsed.size} credentials. Starting handshake..."
                        
                        DataStore.scanScope.launch {
                            val total = parsed.size
                            var completed = 0
                            val chunkSize = 15
                            val chunks = parsed.chunked(chunkSize)
                            
                            for (chunk in chunks) {
                                if (!DataStore.isScanning) break
                                
                                kotlinx.coroutines.coroutineScope {
                                    chunk.map { node ->
                                        kotlinx.coroutines.async(Dispatchers.IO) {
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
                                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.status, details = result.details, expires = result.expires, daysLeft = result.daysLeft, activeConn = result.activeConn, maxConn = result.maxConn, serverTimezone = result.serverTimezone, serverTime = result.serverTime)
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
                                DataStore.scanCountText = "Scan Complete: ${parsed.size} credentials ready."
                            }
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    color = if (DataStore.isScanning) Color(0xFFEF4444) else Color(0xFF3B82F6)
                )
                SecondaryButton(
                    text = "Paste",
                    onClick = { 
                        clipboardManager.getText()?.text?.let { DataStore.scannerInput += it } 
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Clear",
                    onClick = { DataStore.scannerInput = ""; DataStore.scannedNodes.clear() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            AnimatedVisibility(
                visible = DataStore.scannedNodes.isNotEmpty() || DataStore.isScanning,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(
                        text = DataStore.scanCountText,
                        color = Color(0xFF3B82F6),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (DataStore.isScanning || DataStore.scanProgress > 0f) {
                        LinearProgressIndicator(
                            progress = DataStore.scanProgress,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF333344)
                        )
                    }
                }
            }
        }
        
        if (DataStore.scannedNodes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "Continue to Xtream & Stalker →",
                    onClick = onNextTab,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
