package com.projectstrong.iptv.ui.tabs

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*
import com.projectstrong.iptv.utils.ClipboardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

@Composable
fun Base64Tab(onNextTab: () -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var extractedUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingRemote by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Helper decoding function
    fun decodeBase64Payload(rawInput: String) {
        if (rawInput.isBlank()) {
            ToastManager.warning("Input is empty")
            output = ""
            extractedUrls = emptyList()
            return
        }

        // Check if rawInput is a single URL (e.g. pastebin, controlc, rentry, gist, raw url)
        val trimmed = rawInput.trim()
        val isSingleUrl = (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && !trimmed.contains("\n") && !trimmed.contains(" ")

        if (isSingleUrl) {
            coroutineScope.launch {
                isFetchingRemote = true
                ToastManager.info("Fetching remote payload from URL...")
                val remoteContent = IPTVClient.fetchRemoteText(trimmed)
                isFetchingRemote = false

                if (!remoteContent.isNullOrBlank()) {
                    // Try decoding the remote content if it contains Base64, otherwise treat it directly as plaintext
                    val chunkPattern = Pattern.compile("[A-Za-z0-9+/=]{16,}")
                    val chunkMatcher = chunkPattern.matcher(remoteContent)
                    val urlPattern = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE)
                    val results = mutableListOf<String>()
                    val foundUrls = mutableListOf<String>()

                    while (chunkMatcher.find() && results.size < 50) {
                        val candidate = chunkMatcher.group()
                        val pad = candidate.length % 4
                        val padded = candidate + "=".repeat(if (pad > 0) 4 - pad else 0)
                        try {
                            val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                            val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                            if (decodedStr.isNotBlank() && decodedStr.any { it.isLetterOrDigit() } && !results.contains(decodedStr)) {
                                results.add(decodedStr)
                                val uMatcher = urlPattern.matcher(decodedStr)
                                while (uMatcher.find()) {
                                    val u = uMatcher.group()
                                    if (!foundUrls.contains(u)) foundUrls.add(u)
                                }
                            }
                        } catch (e: Throwable) {
                            // ignore non-base64
                        }
                    }

                    if (results.isNotEmpty()) {
                        output = results.joinToString("\n\n")
                    } else {
                        // Fallback: full block or use remote content directly
                        output = remoteContent.trim()
                        val uMatcher = urlPattern.matcher(output)
                        while (uMatcher.find()) {
                            val u = uMatcher.group()
                            if (!foundUrls.contains(u)) foundUrls.add(u)
                        }
                    }
                    extractedUrls = foundUrls
                    ToastManager.success("Downloaded & processed ${output.length} characters from URL!")
                } else {
                    // Fallback to standard Base64 string decoding on the URL itself
                    processDirectBase64(trimmed)
                }
            }
        } else {
            processDirectBase64(rawInput)
        }
    }

    fun processDirectBase64(rawInput: String) {
        try {
            val results = mutableListOf<String>()
            val foundUrls = mutableListOf<String>()
            val urlPattern = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE)

            // 1. Scan for embedded Base64 chunks (minimum 16 alphanumeric Base64 chars)
            val chunkPattern = Pattern.compile("[A-Za-z0-9+/=]{16,}")
            val chunkMatcher = chunkPattern.matcher(rawInput)
            var foundChunks = 0

            while (chunkMatcher.find() && foundChunks < 50) {
                val candidate = chunkMatcher.group()
                val pad = candidate.length % 4
                val padded = candidate + "=".repeat(if (pad > 0) 4 - pad else 0)
                try {
                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                    val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                    if (decodedStr.isNotBlank() && decodedStr.any { it.isLetterOrDigit() } && !results.contains(decodedStr)) {
                        results.add(decodedStr)
                        foundChunks++
                        
                        // Extract any URLs inside this decoded chunk
                        val uMatcher = urlPattern.matcher(decodedStr)
                        while (uMatcher.find()) {
                            val u = uMatcher.group()
                            if (!foundUrls.contains(u)) foundUrls.add(u)
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore non-base64 candidates
                }
            }

            // 2. Fallback: Full block decode if no distinct chunks found
            if (results.isEmpty()) {
                val cleanInput = rawInput.replace(Regex("\\s+"), "")
                if (cleanInput.isNotBlank()) {
                    val pad = cleanInput.length % 4
                    val padded = cleanInput + "=".repeat(if (pad > 0) 4 - pad else 0)
                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                    val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                    output = decodedStr
                    val uMatcher = urlPattern.matcher(decodedStr)
                    while (uMatcher.find()) {
                        val u = uMatcher.group()
                        if (!foundUrls.contains(u)) foundUrls.add(u)
                    }
                } else {
                    output = ""
                }
            } else {
                output = results.joinToString("\n\n")
            }

            extractedUrls = foundUrls
            if (output.isNotBlank()) {
                ToastManager.success("Base64 payload decoded successfully!")
            } else {
                ToastManager.warning("No valid Base64 patterns found in text")
            }
        } catch (e: Throwable) {
            output = "Error decoding: ${e.message ?: "Invalid Base64 payload"}"
            extractedUrls = emptyList()
            ToastManager.error("Failed to decode Base64 data")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Description Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Base64 URL Decoder",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Deep Payload & Hidden Link Extractor",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Auto-Padding",
                            color = AppPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Extract hidden structural links embedded as text chunks inside unstructured text blocks, automatically stripping garbage or missing padding limits.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Input Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Tier 1: Header title
                Text(
                    text = "Base64 Encoded Block",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paste raw Base64 payload, token blocks, or messy URLs",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tier 2: Dedicated Action Controls Row (Paste & Decode, Clear)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Paste & Decode Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                val clipText = ClipboardHelper.getSafeClipboardText(context, clipboardManager)
                                if (!clipText.isNullOrBlank()) {
                                    input = clipText
                                    decodeBase64Payload(clipText)
                                } else {
                                    ToastManager.warning("Clipboard is empty or contains non-text data")
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste & Decode", color = AppPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Clear Button (Interactive when input or output has text)
                        if (input.isNotEmpty() || output.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppError.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AppError.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    input = ""
                                    output = ""
                                    extractedUrls = emptyList()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppError, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear", color = AppError, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Paste Base64 encoded payload or messy text with embedded Base64 strings...", color = AppTextMuted) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedBorderColor = AppPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedTextColor = AppTextPrimary,
                        unfocusedContainerColor = AppSurfaceVariant,
                        focusedContainerColor = AppSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrimaryButton(
                    text = if (isFetchingRemote) "⏳ Fetching URL Payload..." else "⚡ Decode Data",
                    onClick = { if (!isFetchingRemote) decodeBase64Payload(input) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }
        }

        // Output Card & Power Actions
        if (output.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Decoded Output",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (extractedUrls.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppSuccess.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "${extractedUrls.size} URLs Found",
                                        color = AppSuccess,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(output))
                                ToastManager.success("Output copied to clipboard!")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Output", tint = AppPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedTextField(
                        value = output,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedBorderColor = AppPrimary,
                            unfocusedTextColor = AppTextPrimary,
                            focusedTextColor = AppTextPrimary,
                            unfocusedContainerColor = AppSurfaceVariant,
                            focusedContainerColor = AppSurfaceVariant
                        )
                    )

                    // ⚡ POWER ACTION BAR
                    Text(
                        text = "POWER ACTIONS",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Send to Scanner Power Action
                        PrimaryButton(
                            text = "⚡ Send to Scanner",
                            onClick = {
                                val textToSend = output.ifBlank { extractedUrls.joinToString("\n") }
                                DataStore.scannerInput = textToSend
                                ToastManager.success("Decoded payload pushed to Scanner!")
                                onNextTab()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )

                        // 2. Filter URLs Only Button
                        if (extractedUrls.isNotEmpty()) {
                            SecondaryButton(
                                text = "🧹 Filter URLs",
                                onClick = {
                                    output = extractedUrls.joinToString("\n")
                                    ToastManager.info("Filtered output to ${extractedUrls.size} clean URL(s)")
                                },
                                modifier = Modifier.height(44.dp)
                            )
                        }
                    }

                    // 🌐 Discovered URL Action Cards
                    if (extractedUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DISCOVERED TARGET ENDPOINTS",
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            extractedUrls.forEach { url ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppSurfaceVariant,
                                    border = BorderStroke(1.dp, AppSurfaceBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = url,
                                            color = AppTextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Copy single URL
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(url))
                                                    ToastManager.success("Copied URL to clipboard!")
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppPrimary, modifier = Modifier.size(16.dp))
                                            }

                                            // Open in browser
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                        context.startActivity(intent)
                                                    } catch (e: Throwable) {
                                                        ToastManager.error("Cannot open browser: ${e.message}")
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open Browser", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                            }

                                            // Launch in external video player
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(Uri.parse(url), "video/*")
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Play Stream With..."))
                                                    } catch (e: Throwable) {
                                                        ToastManager.error("No compatible video player found")
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.PlayCircleOutline, contentDescription = "Play Stream", tint = AppSuccess, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SecondaryButton(
            text = "Continue to Scanner →",
            onClick = onNextTab,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}

