package com.projectstrong.iptv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.network.NetworkMonitor
import com.projectstrong.iptv.ui.components.ConnectionStateDialog
import com.projectstrong.iptv.ui.components.SettingsDialog
import com.projectstrong.iptv.ui.components.ToastHost
import com.projectstrong.iptv.ui.components.ToastManager
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToastManager.init(applicationContext)
        CommittedManager.init(applicationContext)
        com.projectstrong.iptv.data.SettingsManager.init(applicationContext)

        // Setup reactive network callback to dynamically track VPN / WiFi / Cellular / Offline transitions
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        NetworkMonitor.updateHardwareVpnState(applicationContext)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                NetworkMonitor.updateHardwareVpnState(applicationContext)
                DataStore.scanScope.launch {
                    NetworkMonitor.refreshNetworkState(applicationContext)
                }
            }

            override fun onLost(network: Network) {
                NetworkMonitor.updateHardwareVpnState(applicationContext)
                DataStore.scanScope.launch {
                    NetworkMonitor.refreshNetworkState(applicationContext)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                DataStore.isVpnActive = hasVpn
                DataStore.scanScope.launch {
                    NetworkMonitor.refreshNetworkState(applicationContext)
                }
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            // Fallback gracefully
        }

        // Initial network fetch
        DataStore.scanScope.launch {
            NetworkMonitor.refreshNetworkState(applicationContext)
        }

        // Auto-sync committed records from cloud in background to guarantee fresh state
        DataStore.scanScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                CommittedManager.syncFromCloud()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

    override fun onResume() {
        super.onResume()
        NetworkMonitor.updateHardwareVpnState(applicationContext)
        DataStore.scanScope.launch {
            NetworkMonitor.refreshNetworkState(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {}
        }
    }
}

data class TabItem(
    val title: String,
    val count: Int = 0,
    val icon: ImageVector
)

@Composable
fun MainDashboard() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val xtreamNodesCount = DataStore.scannedNodes.count { it.type == "Xtream" }
    val stalkerNodesCount = DataStore.scannedNodes.count { it.type == "Stalker" }
    val activeNodesCount = DataStore.scannedNodes.count { it.status.contains("Active", true) }
    val committedCount = CommittedManager.records.size
    
    val tabItems = listOf(
        TabItem("Base64", 0, Icons.Default.Code),
        TabItem("Scanner", 0, Icons.Default.Sensors),
        TabItem("Xtream", xtreamNodesCount, Icons.Default.LiveTv),
        TabItem("Stalker", stalkerNodesCount, Icons.Default.Dns),
        TabItem("Committed", committedCount, Icons.Default.FolderSpecial)
    )

    if (showConnectionDialog) {
        ConnectionStateDialog(onDismiss = { showConnectionDialog = false })
    }

    if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Top Bar: App Branding + Connection State Pill + Dedicated Modern Settings Gear Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mini Sherlock Emblem Brand Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0D3B36),
                    border = BorderStroke(1.dp, Color(0xFF0F766E)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_sherlock_brand),
                            contentDescription = "Sherlock Detective",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Sherlock Streams",
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "The Digital Stream Detective",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connection State Indicator Pill (Clickable for full diagnostics & manual VPN recheck)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        DataStore.detectedIsp == "Offline" || DataStore.ipInfo.contains("DISCONNECTED") -> AppErrorContainer
                        DataStore.isVpnActive -> AppPrimaryContainer
                        DataStore.isCloudHosting -> AppWarningContainer
                        else -> AppSurfaceVariant
                    },
                    border = BorderStroke(
                        1.dp, 
                        when {
                            DataStore.detectedIsp == "Offline" || DataStore.ipInfo.contains("DISCONNECTED") -> AppError.copy(alpha = 0.5f)
                            DataStore.isVpnActive -> Color(0xFF38BDF8).copy(alpha = 0.5f)
                            DataStore.isCloudHosting -> AppWarning.copy(alpha = 0.5f)
                            else -> AppSuccess.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.clickable { showConnectionDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        DataStore.detectedIsp == "Offline" || DataStore.ipInfo.contains("DISCONNECTED") -> Color(0xFFEF4444)
                                        DataStore.isVpnActive -> Color(0xFF38BDF8)
                                        DataStore.isCloudHosting -> Color(0xFFFBBF24)
                                        else -> Color(0xFF34D399)
                                    }
                                )
                        )
                        Text(
                            text = when {
                                DataStore.detectedIsp == "Offline" || DataStore.ipInfo.contains("DISCONNECTED") -> "Offline"
                                DataStore.isVpnActive -> "VPN"
                                DataStore.isCheckingNetwork -> "..."
                                DataStore.isCloudHosting -> "Cloud"
                                else -> "ISP"
                            },
                            color = when {
                                DataStore.detectedIsp == "Offline" || DataStore.ipInfo.contains("DISCONNECTED") -> Color(0xFFF87171)
                                DataStore.isVpnActive -> Color(0xFF38BDF8)
                                DataStore.isCloudHosting -> Color(0xFFFBBF24)
                                else -> Color(0xFF34D399)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dedicated Settings Gear UI Element (Modern IconButton with glowing badge)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurfaceVariant,
                    border = BorderStroke(1.dp, AppSurfaceBorder),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSettingsDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "App Settings",
                            tint = AppTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Distinct, High-Emphasis Classic Tab Navigation Bar (Elevated with active underline & badge styling)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceBorder)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                tabItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        // High-contrast, tactile pill for unselected and selected tabs
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                AppPrimary.copy(alpha = 0.22f)
                            } else {
                                Color.Transparent
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, AppPrimary.copy(alpha = 0.6f))
                            } else {
                                null
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF60A5FA) else AppTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) Color(0xFF93C5FD) else AppTextSecondary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (item.count > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AppPrimary else Color(0xFF26334D),
                                        modifier = Modifier.padding(start = 2.dp)
                                    ) {
                                        Text(
                                            text = "${item.count}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Tab Content Screen
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
