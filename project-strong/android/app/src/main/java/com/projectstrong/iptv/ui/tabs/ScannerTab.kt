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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.delay
import com.projectstrong.iptv.network.IPTVClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun ScannerTab(onNextTab: () -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    val output = DataStore.scannedNodes
    var ipInfo by remember { mutableStateOf("Checking connection...") }
    var showVpnWarning by remember { mutableStateOf(false) }
        var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanCountText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url("http://ip-api.com/json/").build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val json = JSONObject(response.body?.string() ?: "{}")
                val isp = json.optString("isp", "Unknown ISP")
                val org = json.optString("org", "")
                ipInfo = "Connected via $isp"
                val combined = "$isp $org".lowercase()
                val cloudProviders = listOf("amazon", "aws", "google", "azure", "cloudflare", "digitalocean")
                showVpnWarning = !combined.contains("vpn") && !combined.contains("proxy") && !combined.contains("mullvad") && !combined.contains("nord")
            } catch (e: Exception) {
                ipInfo = "VPN / Connection Unknown"
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
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            if (showVpnWarning) {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(
                        text = "⚠️ $ipInfo\nWARNING: You may not be using a VPN. Public ISPs often block IPTV portals. You can proceed, but results may fail.",
                        color = Color(0xFFF59E0B),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(text = "🛡️ $ipInfo", color = Color(0xFF10B981), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = if (isScanning) "Scanning..." else "Parse & Scan Data",
                    onClick = {
                        if (isScanning) return@GlassButton
                        val parsed = Parser.parseCredentials(input)
                        DataStore.scannedNodes.clear()
                        DataStore.scannedNodes.addAll(parsed)
                        if (parsed.isEmpty()) {
                            scanCountText = "No credentials found."
                            return@GlassButton
                        }
                        
                        isScanning = true
                        scanProgress = 0f
                        scanCountText = "Found ${parsed.size} credentials. Starting handshake..."
                        
                        coroutineScope.launch {
                            val total = parsed.size
                            for ((i, node) in parsed.withIndex()) {
                                // Batch Verification
                                val idx = DataStore.scannedNodes.indexOf(node)
                                if (idx != -1) {
                                    DataStore.scannedNodes[idx] = node.copy(isVerifying = true, status = "Connecting...")
                                }
                                
                                val result = if (node.type == "Xtream") {
                                    IPTVClient.verifyXtream(node.baseUrl, node.user, node.pass)
                                } else {
                                    IPTVClient.verifyStalker(node.baseUrl, node.mac)
                                }
                                
                                val newIdx = DataStore.scannedNodes.indexOfFirst { it.baseUrl == node.baseUrl && it.user == node.user && it.mac == node.mac && it.type == node.type }
                                if (newIdx != -1) {
                                    if (result is com.projectstrong.iptv.network.VerificationResult.Success) {
                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.status, details = result.details)
                                    } else if (result is com.projectstrong.iptv.network.VerificationResult.Failed) {
                                        DataStore.scannedNodes[newIdx] = DataStore.scannedNodes[newIdx].copy(isVerifying = false, status = result.reason)
                                    }
                                }
                                
                                scanProgress = (i + 1).toFloat() / total.toFloat()
                                scanCountText = "Processed ${i + 1}/$total connections..."
                            }
                            isScanning = false
                            scanCountText = "Scan Complete: ${parsed.size} credentials ready."
                        }
                    },
                    modifier = Modifier.weight(1.5f)
                )
                GlassButton(
                    text = "Paste",
                    onClick = { 
                        clipboardManager.getText()?.text?.let { input += it } 
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Clear",
                    onClick = { input = ""; DataStore.scannedNodes.clear() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        item {
            AnimatedVisibility(
                visible = output.isNotEmpty() || isScanning,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(
                        text = scanCountText,
                        color = Color(0xFF3B82F6),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isScanning || scanProgress > 0f) {
                        LinearProgressIndicator(
                            progress = scanProgress,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFF10B981),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
        
        if (output.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                GlassButton(
                    text = "Continue to Xtream & Stalker →",
                    onClick = onNextTab
                )
            }
        }
    }
}
