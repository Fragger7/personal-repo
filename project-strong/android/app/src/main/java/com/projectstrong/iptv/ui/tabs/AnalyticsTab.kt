package com.projectstrong.iptv.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.ProviderIntelligenceManager
import com.projectstrong.iptv.ui.theme.*
import kotlin.math.atan2

@Composable
fun AnalyticsTab(onNavigateToCommitted: () -> Unit) {
    val records = CommittedManager.records
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No committed records to analyze.", color = AppTextMuted)
        }
        return
    }

    // Prepare Data
    val totalChannels = records.sumOf { it.safeChannels.toIntOrNull() ?: 0 }
    val totalVods = records.sumOf { it.safeVods.toIntOrNull() ?: 0 }
    val totalConnections = records.size

    val providerCounts = records.groupingBy { 
        ProviderIntelligenceManager.getProfile(it.safeBaseUrl)?.cleanBrand ?: it.safeProvider.ifEmpty { "Unbranded" }
    }.eachCount().toList().sortedByDescending { it.second }.take(8)

    val statusCounts = records.groupingBy { 
        if (it.safeStatus.contains("Active", ignoreCase = true)) "🟢 Active" else "🔴 Offline" 
    }.eachCount().toList().sortedByDescending { it.second }

    // Catalog Density (Avg Channels by Provider)
    val densityData = records.groupBy { 
        ProviderIntelligenceManager.getProfile(it.safeBaseUrl)?.cleanBrand ?: it.safeProvider.ifEmpty { "Unbranded" }
    }.map { (brand, group) ->
        brand to group.mapNotNull { it.safeChannels.toIntOrNull() }.average().toFloat()
    }.filter { !it.second.isNaN() && it.second > 0 }.sortedByDescending { it.second }.take(6)

    // Timezone Coverage
    val timezoneCounts = records.groupingBy { 
        it.safeTimezone.ifEmpty { "Unknown" }
    }.eachCount().toList().sortedByDescending { it.second }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Intelligence Dashboard",
                style = MaterialTheme.typography.titleLarge,
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Connections", "$totalConnections", Modifier.weight(1f))
                MetricCard("Live Channels", "${totalChannels / 1000}k", Modifier.weight(1f))
                MetricCard("VOD Library", "${totalVods / 1000}k", Modifier.weight(1f))
            }
        }
        
        item {
            AnalyticsCard("Provider Consolidation") {
                InteractivePieChart(
                    data = providerCounts,
                    onSliceClick = { providerName ->
                        CommittedFilterStore.clear()
                        CommittedFilterStore.provider.value = setOf(providerName)
                        onNavigateToCommitted()
                    }
                )
            }
        }

        if (densityData.isNotEmpty()) {
            item {
                AnalyticsCard("Catalog Density (Avg Channels)") {
                    AnimatedHorizontalBarChart(data = densityData)
                }
            }
        }

        item {
            AnalyticsCard("Connection Health") {
                InteractivePieChart(
                    data = statusCounts,
                    onSliceClick = { statusName ->
                        CommittedFilterStore.clear()
                        CommittedFilterStore.status.value = setOf(statusName)
                        onNavigateToCommitted()
                    }
                )
            }
        }

        if (timezoneCounts.isNotEmpty()) {
            item {
                AnalyticsCard("Server Timezone Footprint") {
                    InteractivePieChart(
                        data = timezoneCounts,
                        onSliceClick = { tzName ->
                            CommittedFilterStore.clear()
                            CommittedFilterStore.timezone.value = setOf(if (tzName == "Unknown") "" else tzName)
                            onNavigateToCommitted()
                        }
                    )
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = AppPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = AppTextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AnimatedHorizontalBarChart(data: List<Pair<String, Float>>) {
    val maxVal = data.maxOfOrNull { it.second } ?: 1f
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        data.forEach { (name, value) ->
            val pct = if (maxVal > 0) (value / maxVal) * animationProgress.value else 0f
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = AppTextPrimary, fontWeight = FontWeight.Bold)
                    Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = AppTextSecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(AppSurfaceVariant, RoundedCornerShape(7.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(AppPrimary, RoundedCornerShape(7.dp)))
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
        border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = AppTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
fun InteractivePieChart(data: List<Pair<String, Int>>, onSliceClick: (String) -> Unit) {
    val total = data.sumOf { it.second }.toFloat()
    val colors = listOf(AppPrimary, AppSuccess, Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFFEF4444))
    
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }

    val angles = remember(data) {
        var startAngle = -90f
        data.map { (name, count) ->
            val sweep = (count / total) * 360f
            val itemStart = startAngle
            startAngle += sweep
            Triple(name, itemStart, sweep)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(angles) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        var tapAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        
                        if (tapAngle < -90f) tapAngle += 360f
                        
                        angles.forEach { (name, start, sweep) ->
                            if (tapAngle >= start && tapAngle < start + sweep) {
                                onSliceClick(name)
                            }
                        }
                    }
                }
            ) {
                val canvasSize = size.minDimension
                val strokeWidth = 32.dp.toPx()
                
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
                Text("${total.toInt()}", style = MaterialTheme.typography.titleLarge, color = AppTextPrimary, fontWeight = FontWeight.Bold)
                Text("Total", style = MaterialTheme.typography.labelSmall, color = AppTextMuted)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            data.forEachIndexed { index, (name, count) ->
                val pct = ((count / total) * 100).toInt()
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onSliceClick(name) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp).fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size], RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$name", color = AppTextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("$pct%", color = AppTextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
