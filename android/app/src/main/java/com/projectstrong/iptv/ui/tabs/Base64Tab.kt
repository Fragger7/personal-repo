package com.projectstrong.iptv.ui.tabs

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun Base64Tab() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

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
                        val cleanInput = input.replace(Regex("\\s+"), "")
                        val decodedBytes = Base64.decode(cleanInput, Base64.DEFAULT)
                        output = String(decodedBytes)
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
        }
    }
}
