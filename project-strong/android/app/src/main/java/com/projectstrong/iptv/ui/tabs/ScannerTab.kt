package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
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
import com.projectstrong.iptv.network.NetworkMonitor
import com.projectstrong.iptv.network.ParsedCredential
import com.projectstrong.iptv.network.Parser
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*
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
            NetworkMonitor.refreshNetworkState()
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
                delay(500) // Adaptive delay: wait 500ms between batches to let the UI breathe
                val batch = mutableListOf<Pair<Int, ParsedCredential>>()
                while (true) {
                    val item = updateQueue.poll() ?: break
                    batch.add(item)
                    if (batch.size >= 500) break
                }

                if (batch.isEmpty()) continue

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
        // IP Network Status Banner
        if (DataStore.ipInfo.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (DataStore.isCloudHosting) AppError.copy(alpha = 0.15f) else AppSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (DataStore.isCloudHosting) AppError.copy(alpha = 0.5f) else AppSurfaceBorder
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (DataStore.isCloudHosting) Icons.Default.Warning else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (DataStore.isCloudHosting) AppError else AppSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            DataStore.ipInfo,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (DataStore.isCloudHosting) {
                            Text(
                                "Cloud hosting IP detected. Some providers may block datacenter ranges.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppError
                            )
                        }
                    }
                }
            }
        }

        // Input Area Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                // Tier 1: Title & Description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Credential & Playlist Ingestion",
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paste combos, M3U playlists, or Stalker links",
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tier 2: Dedicated Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Line & Char counter badge
                    val lineCount = if (DataStore.scannerInput.isEmpty()) 0 else DataStore.scannerInput.lines().size
                    val charCount = DataStore.scannerInput.length
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder)
                    ) {
                        Text(
                            text = "$lineCount lines • $charCount chars",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Paste Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                clipboardManager.getText()?.text?.let {
                                    DataStore.scannerInput = it
                                    ToastManager.info("Pasted text from clipboard")
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF60A5FA))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", color = Color(0xFF60A5FA), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Clear Button (Interactive when input has text)
                        if (DataStore.scannerInput.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppError.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppError.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    DataStore.scannerInput = ""
                                    DataStore.scannedNodes.clear()
                                    DataStore.scanProgress = 0f
                                    DataStore.scanCountText = ""
                                    ToastManager.info("Cleared scanner input")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF87171))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", color = Color(0xFFF87171), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = DataStore.scannerInput,
                    onValueChange = { DataStore.scannerInput = it },
                    placeholder = {
                        Text(
                            "Paste raw unstructured text, Xtream Codes combos (host user pass), M3U playlists, or Stalker MAC links here...",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedContainerColor = AppSurfaceVariant,
                        unfocusedContainerColor = AppSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic State Action Buttons
        if (!DataStore.isScanning) {
            PrimaryButton(
                text = "⚡ Analyze Playlist Nodes",
                onClick = { startOrRestartScan() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        } else {
            // Active or Paused Scan State
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!DataStore.isScanPaused) {
                    PrimaryButton(
                        text = "⏸️ Pause Scan",
                        color = AppWarning,
                        onClick = {
                            DataStore.isScanPaused = true
                            ToastManager.warning("Scanning paused")
                        },
                        modifier = Modifier.weight(1.2f).height(48.dp)
                    )
                } else {
                    PrimaryButton(
                        text = "▶️ Resume Scan",
                        color = AppSuccess,
                        onClick = {
                            DataStore.isScanPaused = false
                            ToastManager.success("Scanning resumed")
                        },
                        modifier = Modifier.weight(1.2f).height(48.dp)
                    )
                }

                PrimaryButton(
                    text = "⏹️ Stop Scan",
                    color = AppError,
                    onClick = {
                        DataStore.isScanning = false
                        DataStore.isScanPaused = false
                        scanJob?.cancel()
                        DataStore.scanCountText = "Scan stopped by user."
                        ToastManager.error("Scan stopped")
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                )
            }
        }

        if (DataStore.scanCountText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = DataStore.scanCountText,
                        color = if (DataStore.isScanPaused) AppWarning else AppPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (DataStore.isScanning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { DataStore.scanProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (DataStore.isScanPaused) AppWarning else AppPrimary,
                            trackColor = AppSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SecondaryButton(
            text = "Continue to Xtream Nodes →",
            onClick = { onNextTab?.invoke() },
            modifier = Modifier.fillMaxWidth().height(44.dp)
        )
    }
}
