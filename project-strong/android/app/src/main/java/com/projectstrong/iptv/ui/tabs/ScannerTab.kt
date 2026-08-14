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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.PrimaryButton
import com.projectstrong.iptv.ui.components.SecondaryButton
import com.projectstrong.iptv.ui.components.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun ScannerTab(onNextTab: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<Job?>(null) }

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

    fun startOrRestartScan() {
        val parsed = Parser.parseCredentials(DataStore.scannerInput)
        DataStore.scannedNodes.clear()
        DataStore.scannedNodes.addAll(parsed)

        if (parsed.isEmpty()) {
            DataStore.scanCountText = "No valid credentials found in input."
            ToastManager.warning("No credentials detected in text!")
            return
        }

        DataStore.isScanning = true
        DataStore.isScanPaused = false
        DataStore.scanProgress = 0f
        DataStore.scanCountText = "Found ${parsed.size} credentials. Starting non-blocking verification..."
        ToastManager.info("Scanning ${parsed.size} credentials...")

        scanJob?.cancel()
        scanJob = DataStore.scanScope.launch(Dispatchers.Default) {
            val total = parsed.size
            val currentIndex = AtomicInteger(0)
            val completedCount = AtomicInteger(0)
            val updateQueue = ConcurrentLinkedQueue<Pair<Int, ParsedCredential>>()

            val workerCount = 16.coerceAtMost(total.coerceAtLeast(1))
            val workers = List(workerCount) {
                launch(Dispatchers.IO) {
                    while (DataStore.isScanning) {
                        // Handle Pause state
                        while (DataStore.isScanPaused && DataStore.isScanning) {
                            delay(150)
                        }
                        if (!DataStore.isScanning) break

                        val idx = currentIndex.getAndIncrement()
                        if (idx >= total) break

                        val node = parsed[idx]
                        val result = if (node.type == "Xtream") {
                            IPTVClient.verifyXtream(node.baseUrl, node.user, node.pass)
                        } else {
                            IPTVClient.verifyStalker(node.baseUrl, node.mac)
                        }

                        val updatedNode = if (result is VerificationResult.Success) {
                            node.copy(
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
                            node.copy(isVerifying = false, status = result.reason)
                        } else {
                            node.copy(isVerifying = false)
                        }

                        updateQueue.add(Pair(idx, updatedNode))
                        completedCount.incrementAndGet()
                    }
                }
            }

            // Throttled UI updater loop to eliminate Choreographer starvation / ANR
            while (DataStore.isScanning && completedCount.get() < total) {
                delay(120) // Batch updates every 120ms
                val batch = mutableListOf<Pair<Int, ParsedCredential>>()
                while (true) {
                    val item = updateQueue.poll() ?: break
                    batch.add(item)
                    if (batch.size >= 100) break
                }

                val currentDone = completedCount.get()
                withContext(Dispatchers.Main) {
                    for ((idx, updatedNode) in batch) {
                        if (idx < DataStore.scannedNodes.size) {
                            DataStore.scannedNodes[idx] = updatedNode
                        }
                    }
                    DataStore.scanProgress = currentDone.toFloat() / total.toFloat()
                    if (DataStore.isScanPaused) {
                        DataStore.scanCountText = "⏸️ Paused at $currentDone / $total connections."
                    } else {
                        DataStore.scanCountText = "Processed $currentDone / $total connections (${(DataStore.scanProgress * 100).toInt()}%)..."
                    }
                }
            }

            // Await workers
            workers.forEach { it.join() }

            // Final drain
            val finalBatch = mutableListOf<Pair<Int, ParsedCredential>>()
            while (true) {
                val item = updateQueue.poll() ?: break
                finalBatch.add(item)
            }

            withContext(Dispatchers.Main) {
                for ((idx, updatedNode) in finalBatch) {
                    if (idx < DataStore.scannedNodes.size) {
                        DataStore.scannedNodes[idx] = updatedNode
                    }
                }
                if (DataStore.isScanning) {
                    DataStore.isScanning = false
                    DataStore.isScanPaused = false
                    DataStore.scanProgress = 1f
                    val activeCount = DataStore.scannedNodes.count { it.status.contains("Active", ignoreCase = true) }
                    DataStore.scanCountText = "Scan Complete! Found $activeCount active nodes out of $total total."
                    ToastManager.success("Scan complete: $activeCount active nodes found!")
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

        // Dynamic State Action Buttons
        if (!DataStore.isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    text = "Analyze Playlist Nodes",
                    onClick = { startOrRestartScan() },
                    modifier = Modifier.weight(1.8f)
                )
                SecondaryButton(
                    text = "Paste",
                    onClick = {
                        clipboardManager.getText()?.text?.let {
                            DataStore.scannerInput = it
                            ToastManager.info("Pasted text from clipboard")
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
                        ToastManager.info("Cleared scanner input")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Active or Paused Scan State
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!DataStore.isScanPaused) {
                    PrimaryButton(
                        text = "⏸️ Pause Scan",
                        color = Color(0xFFF59E0B),
                        onClick = {
                            DataStore.isScanPaused = true
                            ToastManager.warning("Scanning paused")
                        },
                        modifier = Modifier.weight(1.2f)
                    )
                } else {
                    PrimaryButton(
                        text = "▶️ Resume Scan",
                        color = Color(0xFF10B981),
                        onClick = {
                            DataStore.isScanPaused = false
                            ToastManager.success("Scanning resumed")
                        },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                PrimaryButton(
                    text = "⏹️ Stop Scan",
                    color = Color(0xFFEF4444),
                    onClick = {
                        DataStore.isScanning = false
                        DataStore.isScanPaused = false
                        scanJob?.cancel()
                        DataStore.scanCountText = "Scan stopped by user."
                        ToastManager.error("Scan stopped")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (DataStore.scanCountText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DataStore.scanCountText,
                    color = if (DataStore.isScanPaused) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (DataStore.isScanning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { DataStore.scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (DataStore.isScanPaused) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                    trackColor = Color(0xFF1E293B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            text = "Continue to Xtream Nodes →",
            onClick = { onNextTab?.invoke() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
