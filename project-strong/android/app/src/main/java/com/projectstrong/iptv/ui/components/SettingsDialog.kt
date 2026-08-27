package com.projectstrong.iptv.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.data.SettingsManager
import com.projectstrong.iptv.network.NetworkMonitor
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Modern Full-Featured Settings & Configuration Modal Dialog
 * Accessed via the prominent Top Bar gear icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(DataStore.githubToken) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var isVerifyingToken by remember { mutableStateOf(false) }

    // Dialog for GitHub Personal Access Token
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            containerColor = AppSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = AppPrimary)
                    Text("GitHub Access Token", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Set your GitHub Personal Access Token (PAT) with repo scope to enable real-time 2-way cloud commits and cross-device synchronization.",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("ghp_xxxxxxxxxxxx", color = AppTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedTextColor = AppTextPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedContainerColor = AppSurfaceVariant,
                            unfocusedContainerColor = AppSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tokenInput.trim()
                        CommittedManager.saveGithubToken(trimmed)
                        showTokenDialog = false
                        ToastManager.success(if (trimmed.isNotEmpty()) "GitHub Token saved securely!" else "GitHub Token cleared.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text("Save Token")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    // Dialog for Confirmation to Purge All Local Data
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = AppSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AppError)
                    Text("Clear All Local Data?", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "This will wipe all locally cached scan results, discovered categories, and uncommitted lines. Committed data stored in Git will remain safe.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleared = SettingsManager.purgeVolatileCache()
                        showPurgeConfirmDialog = false
                        ToastManager.info("Cleared $cleared cached scan records.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppError)
                ) {
                    Text("Yes, Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = AppBackground,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppSurface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = AppPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Settings & Intelligence",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Preferences, Cloud Sync & Diagnostics",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppSurfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider(color = AppSurfaceBorder)

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Section: App Brand & Version
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0D3B3A),
                                        border = BorderStroke(1.dp, Color(0xFF0F766E)),
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.TravelExplore,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Sherlock Streams",
                                            color = AppTextPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "The Digital Stream Detective",
                                            color = Color(0xFF38BDF8),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = AppPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                                ) {
                                    val (vName, vCode) = remember {
                                        try {
                                            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
                                            Pair(pInfo.versionName ?: "1.10", code.toString())
                                        } catch (e: Exception) {
                                            Pair("1.10", "1")
                                        }
                                    }
                                    Text(
                                        text = "v$vName ($vCode)",
                                        color = AppPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                            Text(
                                text = "High-performance forensic intelligence scanner and multi-tiered playlist analytics engine for Xtream Codes and Stalker Portal protocols.",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Section 1: Cloud Storage & Sync Engine
                    SettingsDialogSectionHeader(title = "Cloud Synchronization (Git Vault)", icon = Icons.Default.CloudQueue)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingInfoCard(
                                title = "Bidirectional Git Synchronization",
                                description = "Synchronizes verified accounts across all devices via the GitHub REST API (committed.json). Pull merges remote updates; Push commits verified lines permanently."
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "GitHub Token (PAT)",
                                        color = AppTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (DataStore.githubToken.isNotEmpty()) "•••••••••••••••• (Configured)" else "Not Configured (Local Mode Only)",
                                        color = if (DataStore.githubToken.isNotEmpty()) AppSuccess else AppTextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        tokenInput = DataStore.githubToken
                                        showTokenDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (DataStore.githubToken.isNotEmpty()) AppSurfaceVariant else AppPrimary
                                    ),
                                    border = BorderStroke(1.dp, AppSurfaceBorder),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        if (DataStore.githubToken.isNotEmpty()) Icons.Default.Edit else Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (DataStore.githubToken.isNotEmpty()) "Change" else "Configure")
                                }
                            }

                            Divider(color = AppSurfaceBorder.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isVerifyingToken = true
                                            val list = CommittedManager.syncFromCloud()
                                            isVerifyingToken = false
                                            if (list != null) {
                                                ToastManager.success("Vault synced (${list.size} records total)")
                                            } else {
                                                ToastManager.error("Sync failed. Check network or token.")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppPrimary),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    if (isVerifyingToken) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AppPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pull from Cloud")
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isVerifyingToken = true
                                            val ok = CommittedManager.pushToCloud(DataStore.githubToken)
                                            isVerifyingToken = false
                                            if (ok) {
                                                ToastManager.success("Successfully pushed to GitHub repository!")
                                            } else {
                                                ToastManager.error("Push failed. Verify GitHub token.")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Push to Cloud")
                                }
                            }
                        }
                    }

                    // Section 2: Scanning & Discovery Engine Preferences
                    SettingsDialogSectionHeader(title = "Extraction & Scanner Engine", icon = Icons.Default.Tune)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            var concurrency by remember { mutableFloatStateOf(SettingsManager.maxConcurrency.toFloat()) }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Concurrent Handshake Threads", color = AppTextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${concurrency.toInt()} Threads", color = AppPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                SettingInfoCard(
                                    title = "Concurrency Impact",
                                    description = "Higher threads (15-30) scan thousands of links much faster. Lower threads (4-8) reduce Wi-Fi router packet loss and avoid cellular carrier rate limits."
                                )
                                Slider(
                                    value = concurrency,
                                    onValueChange = {
                                        concurrency = it
                                        val cInt = it.toInt()
                                        SettingsManager.saveConcurrency(cInt)
                                    },
                                    valueRange = 2f..30f,
                                    steps = 13,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AppPrimary,
                                        activeTrackColor = AppPrimary,
                                        inactiveTrackColor = AppSurfaceVariant
                                    )
                                )
                            }

                            Divider(color = AppSurfaceBorder.copy(alpha = 0.5f))

                            var timeout by remember { mutableFloatStateOf(SettingsManager.httpTimeoutSeconds.toFloat()) }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Handshake Timeout Limit", color = AppTextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${timeout.toInt()} Seconds", color = AppPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                SettingInfoCard(
                                    title = "Timeout Latency Behavior",
                                    description = "Maximum time to await server response. Increase for slow overseas portals; lower to 4-5s for lightning-fast skipping of offline nodes."
                                )
                                Slider(
                                    value = timeout,
                                    onValueChange = {
                                        timeout = it
                                        val tInt = it.toInt()
                                        SettingsManager.saveTimeout(tInt)
                                    },
                                    valueRange = 3f..20f,
                                    steps = 16,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AppPrimary,
                                        activeTrackColor = AppPrimary,
                                        inactiveTrackColor = AppSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Section 3: Data Management & Maintenance
                    SettingsDialogSectionHeader(title = "Data Management & Cache", icon = Icons.Default.FolderDelete)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingInfoCard(
                                title = "Safe Cache Maintenance",
                                description = "Clearing the active scan cache resets current session tables to free up RAM. Your permanently committed accounts in Git Vault are never touched."
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Clear Active Scan Results", color = AppTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Flush current session's scanned tables and categories.", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(
                                    onClick = { showPurgeConfirmDialog = true },
                                    border = BorderStroke(1.dp, AppError.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppError),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear Cache")
                                }
                            }
                        }
                    }

                    // Section 4: System & Network Diagnostics
                    SettingsDialogSectionHeader(title = "Diagnostics & System Info", icon = Icons.Default.Dns)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingInfoCard(
                                title = "Network Evasion & Shield",
                                description = "The app bypasses Cloudflare & IPTV firewalls by spoofing standard Smarters Player client headers. Running on native Android ensures requests use residential IPs."
                            )

                            DiagnosticRow(
                                label = "Outbound Network IP",
                                value = if (DataStore.detectedIp.isNotEmpty()) DataStore.detectedIp else "Detecting..."
                            )
                            DiagnosticRow(
                                label = "Internet Service Provider (ISP)",
                                value = if (DataStore.detectedIsp.isNotEmpty()) DataStore.detectedIsp else "Unknown / Checking"
                            )
                            DiagnosticRow(
                                label = "Hardware VPN Gateway",
                                value = if (DataStore.isVpnActive) "🛡️ Active / Connected" else "Direct ISP Routing"
                            )
                            DiagnosticRow(
                                label = "Android Runtime OS",
                                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                            )
                            DiagnosticRow(
                                label = "Device Hardware",
                                value = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsDialogSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            color = AppTextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppTextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = AppTextPrimary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingInfoCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppSurfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, AppSurfaceBorder.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = AppTextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = description,
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

