package com.projectstrong.iptv.ui.tabs

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.*
import com.projectstrong.iptv.ui.theme.*

@Composable
fun Base64Tab(onNextTab: () -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Base64 URL Decoder",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Extract hidden structural links embedded as text chunks inside unstructured text blocks, automatically stripping garbage or padding limits.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Input Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Base64 Encoded Block", color = AppTextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                try {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        input = clipText
                                        ToastManager.info("Pasted text from clipboard")
                                    } else {
                                        ToastManager.warning("Clipboard is empty or contains non-text data")
                                    }
                                } catch (e: Throwable) {
                                    ToastManager.error("Unable to access clipboard")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", color = AppPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                input = ""
                                output = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = AppError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Paste Base64 encoded payload...", color = AppTextMuted) },
                    minLines = 5,
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
                    text = "⚡ Decode Data",
                    onClick = {
                        try {
                            if (input.isBlank()) {
                                ToastManager.warning("Input is empty")
                                return@PrimaryButton
                            }
                            val pattern = java.util.regex.Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}")
                            val matcher = pattern.matcher(input)
                            val results = mutableListOf<String>()
                            var count = 0
                            while (matcher.find() && count < 50) {
                                val p = matcher.group()
                                val pad = p.length % 4
                                val padded = p + "=".repeat(if (pad > 0) 4 - pad else 0)
                                try {
                                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                                    val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                                    if (decodedStr.isNotBlank() && !results.contains(decodedStr)) {
                                        results.add(decodedStr)
                                        count++
                                    }
                                } catch (e: Throwable) {}
                            }
                            if (results.isEmpty()) {
                                val cleanInput = input.replace(Regex("\\s+"), "")
                                if (cleanInput.isNotBlank()) {
                                    val pad = cleanInput.length % 4
                                    val padded = cleanInput + "=".repeat(if (pad > 0) 4 - pad else 0)
                                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                                    output = String(decodedBytes, Charsets.UTF_8)
                                } else {
                                    output = ""
                                }
                            } else {
                                output = results.joinToString("\n\n")
                            }
                            ToastManager.success("Base64 decoded successfully!")
                        } catch (e: Throwable) {
                            output = "Error decoding: ${e.message ?: "Invalid Base64 payload"}"
                            ToastManager.error("Failed to decode Base64 data")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )
            }
        }

        // Output Card
        if (output.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Decoded Output",
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                    
                    val trimmedOutput = output.trim()
                    if (trimmedOutput.startsWith("http://") || trimmedOutput.startsWith("https://")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryButton(
                            text = "🌐 Launch Converted URL in Browser",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedOutput))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                    }
                }
            }
        }
        
        SecondaryButton(
            text = "Continue to Scanner →",
            onClick = onNextTab,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        )
    }
}
