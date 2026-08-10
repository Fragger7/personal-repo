package com.projectstrong.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommittedManager.init(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F172A))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
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
    
    val xtreamNodesCount = com.projectstrong.iptv.data.DataStore.scannedNodes.count { it.type == "Xtream" }
    val stalkerNodesCount = com.projectstrong.iptv.data.DataStore.scannedNodes.count { it.type == "Stalker" }
    val committedCount = com.projectstrong.iptv.data.CommittedManager.records.size
    
    val tabs = listOf(
        "Base64", 
        "Scanner", 
        if (xtreamNodesCount > 0) "Xtream ($xtreamNodesCount)" else "Xtream", 
        if (stalkerNodesCount > 0) "Stalker ($stalkerNodesCount)" else "Stalker", 
        if (committedCount > 0) "Committed ($committedCount)" else "Committed"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IPTV Analytics",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }

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
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF3B82F6) else Color.Gray,
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            when (selectedTab) {
                0 -> com.projectstrong.iptv.ui.tabs.Base64Tab(onNextTab = { selectedTab = 1 })
                1 -> com.projectstrong.iptv.ui.tabs.ScannerTab(onNextTab = { selectedTab = 2 })
                2 -> com.projectstrong.iptv.ui.tabs.XtreamTab()
                3 -> com.projectstrong.iptv.ui.tabs.StalkerTab()
                4 -> com.projectstrong.iptv.ui.tabs.CommittedTab()
            }
        }
    }
}
