package com.projectstrong.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.ui.components.ToastHost
import com.projectstrong.iptv.ui.components.ToastManager
import com.projectstrong.iptv.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToastManager.init(applicationContext)
        CommittedManager.init(applicationContext)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainDashboard()
                        ToastHost(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val xtreamNodesCount = DataStore.scannedNodes.count { it.type == "Xtream" }
    val stalkerNodesCount = DataStore.scannedNodes.count { it.type == "Stalker" }
    val committedCount = CommittedManager.records.size
    
    val tabs = listOf(
        "Base64", 
        "Scanner", 
        if (xtreamNodesCount > 0) "Xtream ($xtreamNodesCount)" else "Xtream", 
        if (stalkerNodesCount > 0) "Stalker ($stalkerNodesCount)" else "Stalker", 
        if (committedCount > 0) "Committed ($committedCount)" else "Committed"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // App Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sherlock Streams",
                    color = AppTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "IPTV Intelligence & Diagnostics",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (DataStore.ipInfo.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (DataStore.isCloudHosting) AppWarningContainer else AppSurfaceVariant,
                    border = BorderStroke(
                        1.dp, 
                        if (DataStore.isCloudHosting) AppWarning.copy(alpha = 0.5f) else AppSuccess.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = if (DataStore.isCloudHosting) "⚠️ Cloud Hosting" else "🌐 Direct ISP",
                        color = if (DataStore.isCloudHosting) Color(0xFFFBBF24) else Color(0xFF34D399),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Modern Tab Navigation
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) AppPrimary.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f)) else null
                    ) {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF60A5FA) else AppTextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            when (selectedTab) {
                0 -> com.projectstrong.iptv.ui.tabs.Base64Tab(onNextTab = { selectedTab = 1 })
                1 -> com.projectstrong.iptv.ui.tabs.ScannerTab(onNextTab = { selectedTab = 2 })
                2 -> com.projectstrong.iptv.ui.tabs.XtreamTab(onNextTab = { selectedTab = 3 })
                3 -> com.projectstrong.iptv.ui.tabs.StalkerTab(onNextTab = { selectedTab = 4 })
                4 -> com.projectstrong.iptv.ui.tabs.CommittedTab()
            }
        }
    }
}

