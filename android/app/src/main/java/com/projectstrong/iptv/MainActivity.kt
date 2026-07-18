package com.projectstrong.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.GlassTheme
import com.projectstrong.iptv.ui.theme.GlassBackground
import com.projectstrong.iptv.ui.components.GlassCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlassTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E1B4B)
                                )
                            )
                        )
                ) {
                    MainDashboard()
                }
            }
        }
    }
}

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Base64", "Scanner", "Xtream", "Stalker", "Committed")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    GlassCard(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        GlassCard(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                when (selectedTab) {
                    0 -> com.projectstrong.iptv.ui.tabs.Base64Tab()
                    1 -> com.projectstrong.iptv.ui.tabs.ScannerTab()
                    2 -> com.projectstrong.iptv.ui.tabs.XtreamTab()
                    3 -> com.projectstrong.iptv.ui.tabs.StalkerTab()
                    4 -> com.projectstrong.iptv.ui.tabs.CommittedTab()
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tabs[selectedTab]} Tab Construction Pending",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
