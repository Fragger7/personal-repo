package com.projectstrong.iptv.ui.tabs

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.ArchiveManager
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.data.FavoritesManager
import com.projectstrong.iptv.data.ProviderIntelligenceManager
import com.projectstrong.iptv.data.VaultManager
import com.projectstrong.iptv.ui.theme.*
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private fun formatCompactNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> String.format(Locale.US, "%.2fM", num / 1_000_000.0)
        num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsTab(onNavigateToCommitted: () -> Unit) {
    val records = CommittedManager.records
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = AppTextMuted, modifier = Modifier.size(48.dp))
                Text("No committed records to analyze.", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("Save and verify accounts in the catalog to generate operational intelligence.", color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    val favoritesCount = FavoritesManager.items.size
    val vaultCount = VaultManager.items.size
    val archiveCount = ArchiveManager.records.size

    // High-Performance Consolidated Analytics Computation (Memoized once per dataset change)
    val analytics = remember(records.toList(), favoritesCount, vaultCount, archiveCount) {
        val totalNodes = records.size
        val activeCount = records.count { it.isAuthenticActive }
        val offlineCount = totalNodes - activeCount
        val livenessRate = if (totalNodes > 0) (activeCount.toFloat() / totalNodes * 100).toInt() else 0

        // Media Asset Footprint
        val totalChannels = records.sumOf { it.safeChannels.toLongOrNull() ?: 0L }
        val totalVods = records.sumOf { it.safeVods.toLongOrNull()?.takeIf { v -> v >= 0 } ?: 0L }
        val totalMedia = totalChannels + totalVods
        val activeChannelRecords = records.filter { it.isAuthenticActive && (it.safeChannels.toLongOrNull() ?: 0L) > 0 }
        val avgChannelsPerActive = if (activeChannelRecords.isNotEmpty()) {
            activeChannelRecords.sumOf { it.safeChannels.toLongOrNull() ?: 0L } / activeChannelRecords.size
        } else 0L

        // Hardware Concurrency Headroom
        val totalActiveConn = records.sumOf { it.safeActiveConn.toIntOrNull() ?: 0 }
        val totalMaxConn = records.sumOf { it.safeMaxConn.toIntOrNull() ?: 1 }
        val connLoadPct = if (totalMaxConn > 0) ((totalActiveConn.toFloat() / totalMaxConn) * 100).toInt() else 0

        // Stream Egress & Ghost Line Health
        val egressVerified = records.count { it.safeEgressStatus.startsWith("🟢") || it.safeEgressStatus.contains("Verified", true) }
        val egressGhost = records.count { it.safeEgressStatus.contains("Ghost", true) || it.safeEgressStatus.contains("456") }
        val egressLockout = records.count { it.safeEgressStatus.contains("884") || it.safeEgressStatus.contains("Lock", true) }
        val egressInconclusive = records.count { it.safeEgressStatus.contains("Inconclusive", true) }
        val egressUnchecked = totalNodes - (egressVerified + egressGhost + egressLockout + egressInconclusive)

        val egressLatencies = records.mapNotNull { rec ->
            val match = Regex("""in\s+(\d+)\s*ms""").find(rec.safeEgressDetails)
            match?.groupValues?.get(1)?.toIntOrNull()
        }
        val avgLatencyMs = if (egressLatencies.isNotEmpty()) egressLatencies.average().toInt() else null

        // Protocol Architecture Split
        val xtreamCount = records.count { it.safeType.equals("Xtream", true) }
        val stalkerCount = records.count { it.safeType.equals("Stalker", true) }

        // Subscription Expiry Horizon (Strictly categorized)
        val expiryGroups = records.groupingBy { rec ->
            val days = rec.safeDaysLeft.toIntOrNull()
            when {
                rec.safeExpires.equals("Unlimited", true) || rec.safeDaysLeft == "9999" -> "Lifetime"
                rec.safeType.equals("Stalker", true) && rec.safeDaysLeft.isBlank() -> "MAC / No Expiry"
                days == null || days <= 0 -> "Expired"
                days <= 30 -> "< 30 Days"
                days <= 90 -> "30 - 90 Days"
                days <= 180 -> "90 - 180 Days"
                else -> "180+ Days"
            }
        }.eachCount().toList().sortedBy {
            when (it.first) {
                "Expired" -> 0
                "< 30 Days" -> 1
                "30 - 90 Days" -> 2
                "90 - 180 Days" -> 3
                "180+ Days" -> 4
                "Lifetime" -> 5
                else -> 6
            }
        }

        // Hardware Connections Capability Limits
        val connectionLimits = records.groupingBy {
            val conn = it.safeMaxConn.toIntOrNull() ?: 1
            if (conn >= 3) "3+ Conns" else "$conn Conn(s)"
        }.eachCount().toList().sortedBy { it.first }

        // Content Vectors
        val contentCounts = records.flatMap { it.safeContent.split(",").map { c -> c.trim() }.filter { c -> c.isNotEmpty() } }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(6)

        // Regional Bouquet Specialization (Demonym Intelligence)
        val regionalBouquets = records.mapNotNull { it.safeRegionalFocus }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(6)

        // Provider Consolidation (Using clean safeProvider)
        val providerCounts = records.groupingBy {
            it.safeProvider.ifEmpty { "Unbranded" }
        }.eachCount().toList().sortedByDescending { it.second }.take(8)

        // Provider Dead Weight (% Offline)
        val providerDeadWeight = records.groupBy {
            it.safeProvider.ifEmpty { "Unbranded" }
        }.mapNotNull { (brand, group) ->
            if (group.size < 2) null else {
                val offline = group.count { !it.isAuthenticActive }
                val pct = (offline.toFloat() / group.size) * 100f
                if (pct > 0f) Triple(brand, pct, group.size) else null
            }
        }.sortedByDescending { it.second }.take(6)

        // Catalog Density (Avg Channels per Provider)
        val densityData = records.groupBy {
            it.safeProvider.ifEmpty { "Unbranded" }
        }.mapNotNull { (brand, group) ->
            val chs = group.mapNotNull { it.safeChannels.toIntOrNull() }.filter { it > 0 }
            if (chs.isNotEmpty()) brand to chs.average().toFloat() else null
        }.sortedByDescending { it.second }.take(6)

        // Server Timezone Footprint
        val timezoneCounts = records.groupingBy {
            it.safeTimezone.ifEmpty { "Unknown" }
        }.eachCount().toList().sortedByDescending { it.second }.take(6)

        object {
            val totalNodes = totalNodes
            val activeCount = activeCount
            val offlineCount = offlineCount
            val livenessRate = livenessRate
            val totalChannels = totalChannels
            val totalVods = totalVods
            val totalMedia = totalMedia
            val avgChannelsPerActive = avgChannelsPerActive
            val totalActiveConn = totalActiveConn
            val totalMaxConn = totalMaxConn
            val connLoadPct = connLoadPct
            val egressVerified = egressVerified
            val egressGhost = egressGhost
            val egressLockout = egressLockout
            val egressInconclusive = egressInconclusive
            val egressUnchecked = egressUnchecked
            val avgLatencyMs = avgLatencyMs
            val xtreamCount = xtreamCount
            val stalkerCount = stalkerCount
            val expiryGroups = expiryGroups
            val connectionLimits = connectionLimits
            val contentCounts = contentCounts
            val regionalBouquets = regionalBouquets
            val providerCounts = providerCounts
            val providerDeadWeight = providerDeadWeight
            val densityData = densityData
            val timezoneCounts = timezoneCounts
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Main Interactive Dashboard Container
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Operational Intelligence",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Live telemetry across ${analytics.totalNodes} accounts • ${formatCompactNumber(analytics.totalMedia)} cataloged assets",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                }

                // Curated Footprint Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (favoritesCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "⭐ $favoritesCount Favs",
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (vaultCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🔒 $vaultCount Vault",
                                color = AppPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero KPI Grid (4 Interactive Cards)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Active Nodes (Clickable -> filters to active accounts)
                InteractiveMetricCard(
                    title = "Active Nodes",
                    value = "${analytics.activeCount}",
                    subValue = "${analytics.livenessRate}% Liveness",
                    valueColor = AppSuccess,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val activeStatuses = records.filter { it.isAuthenticActive }.map { it.safeStatus }.toSet()
                        CommittedFilterStore.clear()
                        CommittedFilterStore.status.value = activeStatuses
                        onNavigateToCommitted()
                    }
                )

                // 2. Dead Weight (Clickable -> filters to dead/offline accounts)
                InteractiveMetricCard(
                    title = "Dead Weight",
                    value = "${analytics.offlineCount}",
                    subValue = "${100 - analytics.livenessRate}% Failure",
                    valueColor = AppError,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val offlineStatuses = records.filter { !it.isAuthenticActive }.map { it.safeStatus }.toSet()
                        CommittedFilterStore.clear()
                        CommittedFilterStore.status.value = offlineStatuses
                        onNavigateToCommitted()
                    }
                )

                // 3. Catalog Assets (Channels + VODs)
                InteractiveMetricCard(
                    title = "Total Media",
                    value = formatCompactNumber(analytics.totalMedia),
                    subValue = "${formatCompactNumber(analytics.totalChannels)} Live / ${formatCompactNumber(analytics.totalVods)} VOD",
                    valueColor = AppPrimary,
                    modifier = Modifier.weight(1.1f),
                    onClick = {
                        CommittedFilterStore.clear()
                        onNavigateToCommitted()
                    }
                )

                // 4. Hardware Headroom (Active vs Max Conns)
                InteractiveMetricCard(
                    title = "Hardware Load",
                    value = "${analytics.totalActiveConn}/${analytics.totalMaxConn}",
                    subValue = "${analytics.connLoadPct}% Concurrency",
                    valueColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1.1f),
                    onClick = {
                        CommittedFilterStore.clear()
                        onNavigateToCommitted()
                    }
                )
            }
        }

        // Responsive Body Layout: 2-Column on Landscape, 1-Column on Portrait
        if (isLandscape) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Left Column: Stream Health, Expiry, Asset Balance
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Stream Egress & Ghost Line Inspector
                        AnalyticsCard("Stream Egress Health & Anti-Dump") {
                            EgressHealthContent(
                                verified = analytics.egressVerified,
                                ghost = analytics.egressGhost,
                                lockout = analytics.egressLockout,
                                inconclusive = analytics.egressInconclusive,
                                unchecked = analytics.egressUnchecked,
                                avgLatencyMs = analytics.avgLatencyMs,
                                total = analytics.totalNodes
                            )
                        }

                        // Media Balance: Live Channels vs VOD Density
                        AnalyticsCard("Media Balance (Live vs VOD)") {
                            MediaBalanceContent(
                                channels = analytics.totalChannels,
                                vods = analytics.totalVods,
                                avgChannelsPerActive = analytics.avgChannelsPerActive
                            )
                        }

                        // Subscription Expiry Horizon
                        if (analytics.expiryGroups.isNotEmpty()) {
                            AnalyticsCard("Subscription Expiry Horizon") {
                                SegmentedProgressBar(data = analytics.expiryGroups)
                            }
                        }

                        // Protocol Architecture
                        AnalyticsCard("Infrastructure Protocol") {
                            ProtocolArchitectureContent(
                                xtream = analytics.xtreamCount,
                                stalker = analytics.stalkerCount,
                                total = analytics.totalNodes
                            )
                        }
                    }

                    // Right Column: Providers, Bouquets, Content Vectors, Density
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Provider Consolidation (Interactive Pie with Center Hole tap guard)
                        AnalyticsCard("Provider Consolidation") {
                            InteractivePieChart(
                                data = analytics.providerCounts,
                                onSliceClick = { providerName ->
                                    val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                                    CommittedFilterStore.clear()
                                    CommittedFilterStore.provider.value = setOf(target)
                                    onNavigateToCommitted()
                                }
                            )
                        }

                        // Provider Dead Weight (% Offline) (Interactive Bar Chart)
                        if (analytics.providerDeadWeight.isNotEmpty()) {
                            AnalyticsCard("Provider Dead Weight (% Offline)") {
                                AnimatedHorizontalBarChart(
                                    data = analytics.providerDeadWeight.map { Pair(it.first, it.second) },
                                    isPercentage = true,
                                    colorOverride = AppError,
                                    onItemClick = { providerName ->
                                        val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                                        CommittedFilterStore.clear()
                                        CommittedFilterStore.provider.value = setOf(target)
                                        onNavigateToCommitted()
                                    }
                                )
                            }
                        }

                        // Regional Bouquets
                        if (analytics.regionalBouquets.isNotEmpty()) {
                            AnalyticsCard("Regional Bouquet Footprint") {
                                RegionalBouquetContent(data = analytics.regionalBouquets)
                            }
                        }

                        // Content Vectors & Hardware Limits
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnalyticsCard("Content Tags") {
                                    MiniDonutChart(
                                        data = analytics.contentCounts.take(4),
                                        onItemClick = { contentTag ->
                                            CommittedFilterStore.clear()
                                            CommittedFilterStore.content.value = setOf(contentTag)
                                            onNavigateToCommitted()
                                        }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                AnalyticsCard("Conn Limits") {
                                    MiniDonutChart(data = analytics.connectionLimits)
                                }
                            }
                        }

                        // Catalog Density
                        if (analytics.densityData.isNotEmpty()) {
                            AnalyticsCard("Catalog Density (Avg Channels)") {
                                AnimatedHorizontalBarChart(
                                    data = analytics.densityData,
                                    onItemClick = { providerName ->
                                        val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                                        CommittedFilterStore.clear()
                                        CommittedFilterStore.provider.value = setOf(target)
                                        onNavigateToCommitted()
                                    }
                                )
                            }
                        }

                        // Timezone Footprint
                        if (analytics.timezoneCounts.isNotEmpty()) {
                            AnalyticsCard("Server Timezone Footprint") {
                                InteractivePieChart(
                                    data = analytics.timezoneCounts,
                                    onSliceClick = { tzName ->
                                        val target = if (tzName == "Unknown" || tzName.isBlank()) "None" else tzName
                                        CommittedFilterStore.clear()
                                        CommittedFilterStore.timezone.value = setOf(target)
                                        onNavigateToCommitted()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Portrait Layout: Single Streamlined Column
            // 1. Stream Egress & Ghost Line Health
            item {
                AnalyticsCard("Stream Egress Health & Anti-Dump") {
                    EgressHealthContent(
                        verified = analytics.egressVerified,
                        ghost = analytics.egressGhost,
                        lockout = analytics.egressLockout,
                        inconclusive = analytics.egressInconclusive,
                        unchecked = analytics.egressUnchecked,
                        avgLatencyMs = analytics.avgLatencyMs,
                        total = analytics.totalNodes
                    )
                }
            }

            // 2. Media Balance: Live Channels vs VOD
            item {
                AnalyticsCard("Media Balance (Live vs VOD)") {
                    MediaBalanceContent(
                        channels = analytics.totalChannels,
                        vods = analytics.totalVods,
                        avgChannelsPerActive = analytics.avgChannelsPerActive
                    )
                }
            }

            // 3. Subscription Expiry Horizon
            if (analytics.expiryGroups.isNotEmpty()) {
                item {
                    AnalyticsCard("Subscription Expiry Horizon") {
                        SegmentedProgressBar(data = analytics.expiryGroups)
                    }
                }
            }

            // 4. Provider Dead Weight (% Offline)
            if (analytics.providerDeadWeight.isNotEmpty()) {
                item {
                    AnalyticsCard("Provider Dead Weight (% Offline)") {
                        AnimatedHorizontalBarChart(
                            data = analytics.providerDeadWeight.map { Pair(it.first, it.second) },
                            isPercentage = true,
                            colorOverride = AppError,
                            onItemClick = { providerName ->
                                val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                                CommittedFilterStore.clear()
                                CommittedFilterStore.provider.value = setOf(target)
                                onNavigateToCommitted()
                            }
                        )
                    }
                }
            }

            // 5. Regional Bouquet Specialization
            if (analytics.regionalBouquets.isNotEmpty()) {
                item {
                    AnalyticsCard("Regional Bouquet Specialization") {
                        RegionalBouquetContent(data = analytics.regionalBouquets)
                    }
                }
            }

            // 6. Content Vectors & Hardware Limits
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnalyticsCard("Content Tags") {
                            MiniDonutChart(
                                data = analytics.contentCounts.take(4),
                                onItemClick = { contentTag ->
                                    CommittedFilterStore.clear()
                                    CommittedFilterStore.content.value = setOf(contentTag)
                                    onNavigateToCommitted()
                                }
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AnalyticsCard("Hardware Limits") {
                            MiniDonutChart(data = analytics.connectionLimits)
                        }
                    }
                }
            }

            // 7. Provider Consolidation
            item {
                AnalyticsCard("Provider Consolidation") {
                    InteractivePieChart(
                        data = analytics.providerCounts,
                        onSliceClick = { providerName ->
                            val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                            CommittedFilterStore.clear()
                            CommittedFilterStore.provider.value = setOf(target)
                            onNavigateToCommitted()
                        }
                    )
                }
            }

            // 8. Catalog Density
            if (analytics.densityData.isNotEmpty()) {
                item {
                    AnalyticsCard("Catalog Density (Avg Channels)") {
                        AnimatedHorizontalBarChart(
                            data = analytics.densityData,
                            onItemClick = { providerName ->
                                val target = if (providerName == "Unbranded" || providerName == "Unknown") "None" else providerName
                                CommittedFilterStore.clear()
                                CommittedFilterStore.provider.value = setOf(target)
                                onNavigateToCommitted()
                            }
                        )
                    }
                }
            }

            // 9. Timezone Footprint
            if (analytics.timezoneCounts.isNotEmpty()) {
                item {
                    AnalyticsCard("Server Timezone Footprint") {
                        InteractivePieChart(
                            data = analytics.timezoneCounts,
                            onSliceClick = { tzName ->
                                val target = if (tzName == "Unknown" || tzName.isBlank()) "None" else tzName
                                CommittedFilterStore.clear()
                                CommittedFilterStore.timezone.value = setOf(target)
                                onNavigateToCommitted()
                            }
                        )
                    }
                }
            }

            // 10. Protocol Architecture Split
            item {
                AnalyticsCard("Infrastructure Protocol") {
                    ProtocolArchitectureContent(
                        xtream = analytics.xtreamCount,
                        stalker = analytics.stalkerCount,
                        total = analytics.totalNodes
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// --- Specialized Analytical Sub-Components ---

@Composable
fun InteractiveMetricCard(
    title: String,
    value: String,
    subValue: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppSurfaceBorder),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(title, color = AppTextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subValue, color = AppTextMuted, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EgressHealthContent(
    verified: Int,
    ghost: Int,
    lockout: Int,
    inconclusive: Int,
    unchecked: Int,
    avgLatencyMs: Int?,
    total: Int
) {
    val totalF = total.toFloat().coerceAtLeast(1f)
    val verifiedPct = (verified / totalF * 100).toInt()
    val ghostPct = (ghost / totalF * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Continuous Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSurfaceVariant)
        ) {
            if (verified > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(verified.toFloat()).background(AppSuccess))
            }
            if (ghost > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(ghost.toFloat()).background(AppError))
            }
            if (lockout > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(lockout.toFloat()).background(Color(0xFFDC2626)))
            }
            if (inconclusive > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(inconclusive.toFloat()).background(Color(0xFFF59E0B)))
            }
            if (unchecked > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(unchecked.toFloat()).background(Color(0xFF475569)))
            }
        }

        // Legend Breakdown Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                EgressPill("Verified", verified, verifiedPct, AppSuccess)
                if (ghost > 0) EgressPill("Ghost (456)", ghost, ghostPct, AppError)
                if (lockout > 0) EgressPill("Lock (884)", lockout, (lockout / totalF * 100).toInt(), Color(0xFFDC2626))
                if (unchecked > 0) EgressPill("Unchecked", unchecked, (unchecked / totalF * 100).toInt(), Color(0xFF94A3B8))
            }

            if (avgLatencyMs != null && avgLatencyMs > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "⚡ ${avgLatencyMs}ms avg",
                        color = Color(0xFF34D399),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EgressPill(label: String, count: Int, pct: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text("$label: $count", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text("($pct%)", color = AppTextMuted, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
    }
}

@Composable
fun MediaBalanceContent(channels: Long, vods: Long, avgChannelsPerActive: Long) {
    val total = (channels + vods).toFloat().coerceAtLeast(1f)
    val channelPct = (channels / total * 100).toInt()
    val vodPct = (vods / total * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSurfaceVariant)
        ) {
            if (channels > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(channels.toFloat()).background(AppPrimary))
            }
            if (vods > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight(vods.toFloat()).background(Color(0xFF8B5CF6)))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(AppPrimary, RoundedCornerShape(2.dp)))
                    Text("Live TV: ${formatCompactNumber(channels)} ($channelPct%)", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF8B5CF6), RoundedCornerShape(2.dp)))
                    Text("VOD Movies/Series: ${formatCompactNumber(vods)} ($vodPct%)", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                text = "${formatCompactNumber(avgChannelsPerActive)} avg / active",
                color = AppTextMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RegionalBouquetContent(data: List<Pair<String, Int>>) {
    val maxVal = data.maxOfOrNull { it.second } ?: 1
    val colors = listOf(AppPrimary, Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        data.forEachIndexed { index, (region, count) ->
            val pct = count.toFloat() / maxVal
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "🌍 $region",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(130.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AppSurfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct)
                            .fillMaxHeight()
                            .background(colors[index % colors.size], RoundedCornerShape(7.dp))
                    )
                }
                Text(
                    text = "$count nodes",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(60.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProtocolArchitectureContent(xtream: Int, stalker: Int, total: Int) {
    val totalF = total.toFloat().coerceAtLeast(1f)
    val xtreamPct = (xtream / totalF * 100).toInt()
    val stalkerPct = (stalker / totalF * 100).toInt()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppSurfaceVariant,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                    Text("Xtream Codes API", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("$xtream Nodes ($xtreamPct%)", color = AppPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppSurfaceVariant,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    Text("Stalker Portals (MAC)", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("$stalker Nodes ($stalkerPct%)", color = Color(0xFFF59E0B), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}

// --- Foundational Visual Charts (Optimized with Tap Guards & Deep Linking) ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentedProgressBar(data: List<Pair<String, Int>>) {
    val total = data.sumOf { it.second }.toFloat()
    if (total == 0f) return
    val colors = listOf(AppError, Color(0xFFF59E0B), Color(0xFFFBBF24), AppPrimary, AppSuccess, Color(0xFF8B5CF6), Color(0xFF14B8A6))

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSurfaceVariant)
        ) {
            data.forEachIndexed { index, (_, count) ->
                val weight = (count / total) * animationProgress.value
                if (weight > 0f) {
                    Box(modifier = Modifier.fillMaxHeight().weight(weight).background(colors[index % colors.size]))
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEachIndexed { index, (name, count) ->
                val pct = ((count / total) * 100).toInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(4.dp)))
                    Text(name, color = AppTextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Text("$count ($pct%)", color = AppTextMuted, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AnimatedHorizontalBarChart(
    data: List<Pair<String, Float>>,
    isPercentage: Boolean = false,
    colorOverride: Color? = null,
    onItemClick: ((String) -> Unit)? = null
) {
    val maxVal = if (isPercentage) 100f else (data.maxOfOrNull { it.second } ?: 1f)
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        data.forEach { (name, value) ->
            val pct = if (maxVal > 0) (value / maxVal) * animationProgress.value else 0f
            Column(
                modifier = if (onItemClick != null) Modifier.clickable { onItemClick(name) } else Modifier
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = AppTextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        if (isPercentage) "${value.toInt()}%" else value.toInt().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPercentage && colorOverride != null) colorOverride else AppTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppSurfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct)
                            .fillMaxHeight()
                            .background(colorOverride ?: AppPrimary, RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppSurfaceBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = AppTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun MiniDonutChart(
    data: List<Pair<String, Int>>,
    onItemClick: ((String) -> Unit)? = null
) {
    val total = data.sumOf { it.second }.toFloat()
    val colors = listOf(AppPrimary, Color(0xFF14B8A6), Color(0xFF8B5CF6), Color(0xFFF59E0B), Color(0xFFEC4899))

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
    }

    val angles = remember(data) {
        var startAngle = -90f
        data.map { (name, count) ->
            val sweep = if (total > 0f) (count / total) * 360f else 0f
            val itemStart = startAngle
            startAngle += sweep
            Triple(name, itemStart, sweep)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasSize = size.minDimension
                val strokeWidth = 18.dp.toPx()
                angles.forEachIndexed { index, (_, startAngle, sweepAngle) ->
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * animationProgress.value,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        size = Size(canvasSize - strokeWidth, canvasSize - strokeWidth),
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    )
                }
            }
            Text("${total.toInt()}", style = MaterialTheme.typography.titleMedium, color = AppTextPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        data.forEachIndexed { index, (name, count) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onItemClick != null) Modifier.clickable { onItemClick(name) } else Modifier)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(name, color = AppTextPrimary, style = MaterialTheme.typography.labelSmall)
                }
                Text("$count", color = AppTextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun InteractivePieChart(data: List<Pair<String, Int>>, onSliceClick: (String) -> Unit) {
    val total = data.sumOf { it.second }.toFloat()
    val colors = listOf(AppPrimary, AppSuccess, Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFFEF4444))

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
    }

    val angles = remember(data) {
        var startAngle = -90f
        data.map { (name, count) ->
            val sweep = if (total > 0f) (count / total) * 360f else 0f
            val itemStart = startAngle
            startAngle += sweep
            Triple(name, itemStart, sweep)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(angles) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val r = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val outerR = min(size.width, size.height).toFloat() / 2f
                        val strokePx = 28.dp.toPx()
                        val innerR = outerR - strokePx

                        // Euclidean radius check: only trigger click if tap falls on the donut stroke, not the center hole
                        if (r in innerR..outerR) {
                            var tapAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (tapAngle < -90f) tapAngle += 360f

                            angles.forEach { (name, start, sweep) ->
                                if (tapAngle >= start && tapAngle < start + sweep) {
                                    onSliceClick(name)
                                }
                            }
                        }
                    }
                }
            ) {
                val canvasSize = size.minDimension
                val strokeWidth = 28.dp.toPx()

                angles.forEachIndexed { index, (_, startAngle, sweepAngle) ->
                    val activeSweep = sweepAngle * animationProgress.value
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        size = Size(canvasSize - strokeWidth, canvasSize - strokeWidth),
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    )
                }
            }

            // Center Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total.toInt()}", style = MaterialTheme.typography.titleMedium, color = AppTextPrimary, fontWeight = FontWeight.Black)
                Text("Total", style = MaterialTheme.typography.labelSmall, color = AppTextMuted, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            data.forEachIndexed { index, (name, count) ->
                val pct = if (total > 0f) ((count / total) * 100).toInt() else 0
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onSliceClick(name) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp).fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(colors[index % colors.size], RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(name, color = AppTextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$pct%", color = AppTextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
