package com.projectstrong.iptv.ui.tabs

import android.content.Context
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.projectstrong.iptv.data.*
import com.projectstrong.iptv.network.NetworkMonitor
import com.projectstrong.iptv.ui.components.ToastManager
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab() {
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
                            unfocusedTextColor = AppTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanToken = tokenInput.trim()
                        if (cleanToken.isEmpty()) {
                            CommittedManager.clearGithubToken()
                            ToastManager.info("GitHub Token removed.")
                            showTokenDialog = false
                        } else {
                            isVerifyingToken = true
                            scope.launch {
                                CommittedManager.saveGithubToken(cleanToken)
                                val syncRes = CommittedManager.syncFromCloud()
                                isVerifyingToken = false
                                if (syncRes != null) {
                                    ToastManager.success("Token verified & synced ${syncRes.size} cloud records!")
                                    showTokenDialog = false
                                } else {
                                    ToastManager.warning("Token saved, but cloud sync check returned no changes or invalid repo scope.")
                                    showTokenDialog = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    if (isVerifyingToken) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...")
                    } else {
                        Text("Save & Validate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    // Dialog for Purging Volatile Cache
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = AppSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AppWarning)
                    Text("Purge Scan Caches?", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "This will clear all ${DataStore.scannedNodes.size} scanned active/inactive nodes and reset discovery states. Your permanently Saved/Committed accounts will NOT be affected.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val purged = SettingsManager.purgeVolatileCache()
                        ToastManager.info("Purged $purged cached discovery nodes.")
                        showPurgeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppWarning)
                ) {
                    Text("Purge Cache", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Hero Branding Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.projectstrong.iptv.R.drawable.ic_sherlock_brand),
                                    contentDescription = "Sherlock Detective",
                                    modifier = Modifier.size(36.dp)
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

                // Instant Auto-Save Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppSurfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "⚡ Instant Auto-Save: All preferences, concurrency, timeout, and theme changes take effect immediately across all tabs. No manual save required.",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Section 1: Cloud Synchronization & Repository Sync
        SettingsSectionHeader(title = "Cloud Synchronization", icon = Icons.Default.CloudSync)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub Token (Auto-Commit)",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (DataStore.githubToken.isNotEmpty()) 
                                "Configured (••••••••${DataStore.githubToken.takeLast(4)})" 
                            else 
                                "Not configured — using local offline storage only",
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
                        border = BorderStroke(1.dp, if (DataStore.githubToken.isNotEmpty()) AppSurfaceBorder else Color.Transparent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (DataStore.githubToken.isNotEmpty()) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (DataStore.githubToken.isNotEmpty()) "Edit" else "Configure")
                    }
                }

                Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Repository Target",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Fragger7/personal-repo (project-strong)",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result = CommittedManager.syncFromCloud()
                                if (result != null) {
                                    ToastManager.success("Synced ${result.size} accounts from GitHub!")
                                } else {
                                    ToastManager.error("Cloud fetch failed. Check network or PAT token.")
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }
            }
        }

        // Section 2: Scanning & Networking Parameters
        SettingsSectionHeader(title = "Engine Parameters", icon = Icons.Default.Tune)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // HTTP Timeout Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HTTP Request Timeout",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${SettingsManager.httpTimeoutSeconds} seconds",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "Adjust connection timeout for slow IPTV servers (Default: 6s).",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = SettingsManager.httpTimeoutSeconds.toFloat(),
                        onValueChange = { SettingsManager.saveTimeout(it.toInt()) },
                        valueRange = 3f..20f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = AppPrimary,
                            activeTrackColor = AppPrimary,
                            inactiveTrackColor = AppSurfaceBorder
                        )
                    )
                }

                Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                // Max Concurrency / Semaphore Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Concurrent Async Workers",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${SettingsManager.maxConcurrency} threads",
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "Number of parallel async requests when bulk-scanning nodes (Default: 8).",
                        color = AppTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = SettingsManager.maxConcurrency.toFloat(),
                        onValueChange = { SettingsManager.saveConcurrency(it.toInt()) },
                        valueRange = 2f..24f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF34D399),
                            activeTrackColor = Color(0xFF34D399),
                            inactiveTrackColor = AppSurfaceBorder
                        )
                    )
                }

                Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                // Fast-Fail Hedging & Straggler Optimization Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Adaptive Fast-Fail Hedging",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0F766E).copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, Color(0xFF0F766E))
                            ) {
                                Text(
                                    text = "Tail-Latency Fix",
                                    color = Color(0xFF34D399),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Aggressively times out dead socket handshakes during bulk batch scans, eliminating the 98% hang.",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = SettingsManager.fastFailHedgingEnabled,
                        onCheckedChange = { SettingsManager.saveFastFailHedging(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34D399),
                            uncheckedThumbColor = AppTextSecondary,
                            uncheckedTrackColor = AppSurfaceBorder
                        )
                    )
                }

                Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                // Screen Wake Lock Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen On During Scans",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Prevents device sleep and background throttling while parsing thousands of nodes.",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = SettingsManager.keepScreenOnDuringScans,
                        onCheckedChange = { SettingsManager.saveKeepScreenOn(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppTextSecondary,
                            uncheckedTrackColor = AppSurfaceBorder
                        )
                    )
                }
            }
        }

        // Section 3: Visual Themes & Design Engine
        SettingsSectionHeader(title = "Visual Themes & Palettes", icon = Icons.Default.Palette)
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
                Text(
                    text = "Select your preferred visual atmosphere. Custom palettes dynamically re-style the entire UI across all tabs and data grids in real time.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                AppThemeMode.values().forEach { theme ->
                    val isSelected = SettingsManager.currentTheme == theme
                    val (primaryColor, secondaryColor, surfaceColor) = when (theme) {
                        AppThemeMode.SHERLOCK_AMBER -> Triple(Color(0xFFF59E0B), Color(0xFF06B6D4), Color(0xFF131B2A))
                        AppThemeMode.MIDNIGHT_PURPLE -> Triple(Color(0xFFA78BFA), Color(0xFFC084FC), Color(0xFF18132B))
                        AppThemeMode.OCEAN_BLUE -> Triple(Color(0xFF38BDF8), Color(0xFF06B6D4), Color(0xFF132034))
                        AppThemeMode.CRIMSON_DARK -> Triple(Color(0xFFEF4444), Color(0xFFF43F5E), Color(0xFF1E1014))
                        AppThemeMode.MACOS_LIQUID_GLASS -> Triple(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF1E1E24))
                        AppThemeMode.ROBINHOOD_NEON -> Triple(Color(0xFF00C805), Color(0xFF10B981), Color(0xFF000000))
                        AppThemeMode.CINEMATIC_DARK -> Triple(Color(0xFFE50914), Color(0xFFFACC15), Color(0xFF141414))
                        AppThemeMode.SYSTEM_MONET -> Triple(Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFF1E293B))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primaryColor.copy(alpha = 0.12f) else AppSurfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) primaryColor.copy(alpha = 0.6f) else AppSurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SettingsManager.saveTheme(theme)
                                ToastManager.success("Applied ${theme.title} theme!")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Color swatches preview
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(secondaryColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(surfaceColor)
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    )
                                }

                                Column {
                                    Text(
                                        text = theme.title,
                                        color = if (isSelected) primaryColor else AppTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = theme.description,
                                        color = AppTextMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = primaryColor,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Volatile Memory & Storage Cache
        SettingsSectionHeader(title = "Data & Cache Management", icon = Icons.Default.Storage)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scanned Memory Nodes",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${DataStore.scannedNodes.size} volatile nodes in active session",
                            color = AppTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { showPurgeConfirmDialog = true },
                        enabled = DataStore.scannedNodes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppWarningContainer),
                        border = BorderStroke(1.dp, AppWarning.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AppWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Purge Cache", color = AppWarning, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = AppSurfaceBorder.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Committed Accounts",
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${CommittedManager.records.size} accounts permanently stored in committed.json",
                            color = AppSuccess,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val updated = CommittedManager.recheckAllStatus()
                                ToastManager.success("Rechecked status for $updated saved accounts.")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppSuccess),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify All")
                    }
                }
            }
        }

        // Section 4: System & Network Diagnostics
        SettingsSectionHeader(title = "Diagnostics & System Info", icon = Icons.Default.Dns)
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
                DiagnosticItem(
                    label = "Outbound Network IP",
                    value = if (DataStore.detectedIp.isNotEmpty()) DataStore.detectedIp else "Detecting..."
                )
                DiagnosticItem(
                    label = "Internet Service Provider (ISP)",
                    value = if (DataStore.detectedIsp.isNotEmpty()) DataStore.detectedIsp else "Unknown / Checking"
                )
                DiagnosticItem(
                    label = "Hardware VPN Gateway",
                    value = if (DataStore.isVpnActive) "🛡️ Active / Connected" else "Direct ISP Routing"
                )
                DiagnosticItem(
                    label = "Android Runtime OS",
                    value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                DiagnosticItem(
                    label = "Device Hardware",
                    value = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
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
fun DiagnosticItem(label: String, value: String) {
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
