package com.projectstrong.iptv.ui.tabs

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassTextField

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
        Text(
            text = "Base64 URL Decoder",
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Extract hidden structural links embedded as text chunks inside unstructured text blocks, automatically stripping garbage or padding limits.",
            color = Color.White.copy(alpha = 0.7f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        GlassTextField(
            value = input,
            onValueChange = { input = it },
            label = "Paste Base64 Encoded Block",
            minLines = 6
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "Decode Data",
                onClick = {
                    try {
                        val pattern = java.util.regex.Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}")
                        val matcher = pattern.matcher(input)
                        val results = mutableListOf<String>()
                        while (matcher.find()) {
                            val p = matcher.group()
                            val pad = p.length % 4
                            val padded = p + "=".repeat(if (pad > 0) 4 - pad else 0)
                            try {
                                val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                                results.add(String(decodedBytes, Charsets.UTF_8))
                            } catch(e: Exception) {}
                        }
                        if (results.isEmpty()) {
                            val cleanInput = input.replace(Regex("\\s+"), "")
                            val decodedBytes = Base64.decode(cleanInput, Base64.DEFAULT)
                            output = String(decodedBytes, Charsets.UTF_8)
                        } else {
                            output = results.joinToString("\n\n")
                        }
                    } catch (e: Exception) {
                        output = "Error decoding: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            )
            
            GlassButton(
                text = "Clear",
                onClick = { 
                    input = ""
                    output = ""
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (output.isNotEmpty()) {
            Text(
                text = "Decoded Output",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            GlassTextField(
                value = output,
                onValueChange = {},
                label = "Result",
                minLines = 4
            )
            
            val trimmedOutput = output.trim()
            if (trimmedOutput.startsWith("http://") || trimmedOutput.startsWith("https://")) {
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(
                    text = "🌐 Launch Converted URL in Browser",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedOutput))
                        context.startActivity(intent)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        GlassButton(
            text = "Continue to Scanner →",
            onClick = onNextTab
        )
    }
}
