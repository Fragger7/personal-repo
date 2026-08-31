package com.projectstrong.iptv.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.data.SourceArchiveManager
import com.projectstrong.iptv.ui.theme.*
import com.projectstrong.iptv.utils.ClipboardHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceArchiveViewerDialog(
    sourceLink: String,
    archiveFileName: String = "",
    onDismiss: () -> Unit,
    onSendToScanner: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val effectiveFileName = remember(sourceLink, archiveFileName) {
        if (archiveFileName.isNotBlank()) archiveFileName
        else SourceArchiveManager.generateArchiveFileName(sourceLink)
    }

    var isLoading by remember { mutableStateOf(true) }
    var contentText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isWordWrap by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceLink, effectiveFileName) {
        isLoading = true
        errorMessage = null
        val result = SourceArchiveManager.getOrFetchArchive(
            context = context,
            archiveFileName = effectiveFileName,
            sourceLink = sourceLink,
            token = DataStore.githubToken
        )
        if (!result.isNullOrBlank()) {
            contentText = result
        } else {
            errorMessage = "No saved snapshot could be found for this source yet."
        }
        isLoading = false
    }

    val lines = remember(contentText) {
        if (contentText.isBlank()) emptyList() else contentText.lines()
    }

    val matchCount = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) 0
        else lines.count { it.contains(searchQuery, ignoreCase = true) }
    }

    val fileSizeKb = remember(contentText) {
        String.format("%.1f KB", contentText.toByteArray().size / 1024.0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Article, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Column {
                            Text(
                                "Forever Source Snapshot",
                                color = AppTextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (effectiveFileName.isNotBlank()) effectiveFileName else sourceLink,
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action & Badge Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge info
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder)
                    ) {
                        Text(
                            text = "Lines: ${lines.size}  •  $fileSizeKb",
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (matchCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "🔍 $matchCount matches",
                                color = AppPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Wrap Lines Toggle
                    IconButton(
                        onClick = { isWordWrap = !isWordWrap },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isWordWrap) Icons.Default.WrapText else Icons.Default.Notes,
                            contentDescription = "Toggle Word Wrap",
                            tint = if (isWordWrap) AppPrimary else AppTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Copy All
                    IconButton(
                        onClick = {
                            if (contentText.isNotBlank()) {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Source Snapshot", contentText)
                                    clipboard.setPrimaryClip(clip)
                                    ToastManager.success("Copied snapshot (${lines.size} lines) to clipboard!")
                                } catch (e: Exception) {
                                    ToastManager.error("Could not copy: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = contentText.isNotBlank()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All", tint = AppTextPrimary, modifier = Modifier.size(18.dp))
                    }

                    // GitHub Link Button
                    IconButton(
                        onClick = {
                            val gitUrl = "https://github.com/Fragger7/personal-repo/blob/main/project-strong/sources/$effectiveFileName"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gitUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                ToastManager.error("Could not open browser: ${e.message}")
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = "View on GitHub", tint = AppPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter snapshot text...", color = AppTextSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedContainerColor = AppSurfaceVariant,
                        unfocusedContainerColor = AppSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Main Viewer Body
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A), // Dark slate terminal bg
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = AppPrimary, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                    Text("Loading snapshot archive...", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        errorMessage != null -> {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AppWarning, modifier = Modifier.size(36.dp))
                                    Text(errorMessage ?: "", color = AppTextSecondary, style = MaterialTheme.typography.bodySmall)
                                    if (sourceLink.startsWith("http://", ignoreCase = true) || sourceLink.startsWith("https://", ignoreCase = true)) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isLoading = true
                                                    val fetched = com.projectstrong.iptv.network.IPTVClient.fetchRemoteText(sourceLink)
                                                    if (!fetched.isNullOrBlank()) {
                                                        SourceArchiveManager.saveArchiveLocally(context, effectiveFileName, fetched)
                                                        DataStore.sourceSnapshots[sourceLink] = fetched
                                                        contentText = fetched
                                                        errorMessage = null
                                                    } else {
                                                        ToastManager.error("Could not fetch remote content directly from link")
                                                    }
                                                    isLoading = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Fetch & Archive Now", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            val vScroll = rememberScrollState()
                            val hScroll = rememberScrollState()

                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(vScroll)
                                        .then(if (!isWordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                                        .padding(12.dp)
                                ) {
                                    lines.forEachIndexed { index, line ->
                                        val isHighlighted = searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (isHighlighted) Modifier.background(AppPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)) else Modifier)
                                                .padding(vertical = 1.dp)
                                        ) {
                                            // Line number
                                            Text(
                                                text = "%3d | ".format(index + 1),
                                                color = Color(0xFF64748B),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            // Text with search match highlighting
                                            if (searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)) {
                                                val annotated = buildAnnotatedString {
                                                    val lowerLine = line.lowercase()
                                                    val lowerQ = searchQuery.lowercase()
                                                    var start = 0
                                                    while (start < line.length) {
                                                        val idx = lowerLine.indexOf(lowerQ, start)
                                                        if (idx == -1) {
                                                            append(line.substring(start))
                                                            break
                                                        }
                                                        append(line.substring(start, idx))
                                                        withStyle(SpanStyle(background = AppPrimary, color = Color.White, fontWeight = FontWeight.Bold)) {
                                                            append(line.substring(idx, idx + searchQuery.length))
                                                        }
                                                        start = idx + searchQuery.length
                                                    }
                                                }
                                                Text(
                                                    text = annotated,
                                                    color = Color(0xFFF1F5F9),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                )
                                            } else {
                                                Text(
                                                    text = line,
                                                    color = if (line.trim().startsWith("#") || line.trim().startsWith("//")) Color(0xFF94A3B8) else Color(0xFFF1F5F9),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Footer Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (contentText.isNotBlank()) {
                        Button(
                            onClick = {
                                DataStore.scannerInput = contentText
                                DataStore.scannerSourceLink = sourceLink
                                DataStore.sourceSnapshots[sourceLink] = contentText
                                onSendToScanner?.invoke(contentText)
                                ToastManager.success("Snapshot loaded into Scanner!")
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚡ Send to Scanner", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AppSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", color = AppTextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
