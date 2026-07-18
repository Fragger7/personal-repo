package com.projectstrong.iptv.ui.tabs

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassTextField

@Composable
fun Base64Tab() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Base64 URL Decoder",
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )

        GlassTextField(
            value = input,
            onValueChange = { input = it },
            label = "Paste Base64 Encoded Block",
            minLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "Decode",
                onClick = {
                    try {
                        // Extract base64 parts
                        val decodedBytes = Base64.decode(input, Base64.DEFAULT)
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
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
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
