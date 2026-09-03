package com.tvmime.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.tvmime.theme.DesignSystemTokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVMimeTvApp()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TVMimeTvApp() {
    val bgColor = Color(DesignSystemTokens.Colors.Background)
    val cardColor = Color(DesignSystemTokens.Colors.Card)
    val crimson = Color(DesignSystemTokens.Colors.Crimson)
    val textPrimary = Color(DesignSystemTokens.Colors.TextPrimary)
    val textSecondary = Color(DesignSystemTokens.Colors.TextSecondary)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(crimson, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TV", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }

                Text(
                    text = "TVMIME",
                    color = textPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Text(
                text = "Next-Gen IPTV Streaming Player for Android TV",
                color = textSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // TV Action Cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TvCard(
                    title = "Live TV",
                    subtitle = "Browse Catalogs & EPG",
                    color = cardColor,
                    accent = crimson,
                    onClick = { }
                )

                TvCard(
                    title = "Movies (VOD)",
                    subtitle = "On-Demand Cinema",
                    color = cardColor,
                    accent = Color(0xFF3B82F6),
                    onClick = { }
                )

                TvCard(
                    title = "TV Series",
                    subtitle = "Seasons & Episodes",
                    color = cardColor,
                    accent = Color(0xFFA855F7),
                    onClick = { }
                )

                TvCard(
                    title = "Cloud Sync",
                    subtitle = "tivimime.vercel.app",
                    color = cardColor,
                    accent = Color(0xFF10B981),
                    onClick = { }
                )

                TvCard(
                    title = "OTA Updates",
                    subtitle = "Check for New APK",
                    color = cardColor,
                    accent = Color(0xFFF59E0B),
                    onClick = {
                        // Triggers in-place OTA update check & installer
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCard(
    title: String,
    subtitle: String,
    color: Color,
    accent: Color,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,

        colors = CardDefaults.colors(
            containerColor = color,
            focusedContainerColor = Color(0xFF222230)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, accent)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        modifier = Modifier
            .width(180.dp)
            .height(110.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}
