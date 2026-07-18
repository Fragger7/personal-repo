package com.projectstrong.iptv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.components.GlassButton
import com.projectstrong.iptv.ui.components.GlassCard

@Composable
fun CommittedTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Committed Data",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
            GlassCard(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "0 Records",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth()) {
            GlassButton(
                text = "Reload from Cloud",
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No committed records found.",
                        color = Color.White.copy(alpha=0.5f)
                    )
                }
            }
        }
    }
}
