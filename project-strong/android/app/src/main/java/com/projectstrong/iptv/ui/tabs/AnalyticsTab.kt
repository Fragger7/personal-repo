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

    // Advanced Metrics
    val activeCount = records.count { it.safeStatus.contains("Active", ignoreCase = true) }
    val offlineCount = records.size - activeCount
    val totalChannels = records.sumOf { it.safeChannels.toIntOrNull() ?: 0 }

    // Expiry Horizon
    val expiryGroups = records.groupingBy { 
        val days = it.safeDaysLeft.toIntOrNull() ?: 0
        when {
            days <= 0 -> "Expired"
            days <= 30 -> "< 30 Days"
            days <= 90 -> "30 - 90 Days"
            days <= 180 -> "90 - 180 Days"
            else -> "180+ Days"
        }
    }.eachCount().toList().sortedBy { 
        when(it.first) { "Expired" -> 0; "< 30 Days" -> 1; "30 - 90 Days" -> 2; "90 - 180 Days" -> 3; else -> 4 }
    }

    // Hardware Connections Capability
    val connectionLimits = records.groupingBy { 
        val conn = it.safeConnections.toIntOrNull() ?: 1
        if (conn >= 3) "3+ Conns" else "$conn Conn(s)"
    }.eachCount().toList().sortedBy { it.first }

    // Provider Dead Weight (% Offline)
    val providerDeadWeight = records.groupBy { 
        ProviderIntelligenceManager.getProfile(it.safeBaseUrl)?.cleanBrand ?: it.safeProvider.ifEmpty { "Unbranded" }
    }.map { (brand, group) ->
        val offline = group.count { !it.safeStatus.contains("Active", ignoreCase = true) }
        val pct = if (group.isNotEmpty()) (offline.toFloat() / group.size) * 100 else 0f
        Triple(brand, pct, group.size)
    }.filter { it.third >= 3 && it.second > 0 }.sortedByDescending { it.second }.take(6)
    
    // Original Metrics
    val providerCounts = records.groupingBy { 
        ProviderIntelligenceManager.getProfile(it.safeBaseUrl)?.cleanBrand ?: it.safeProvider.ifEmpty { "Unbranded" }
    }.eachCount().toList().sortedByDescending { it.second }.take(8)

    val contentCounts = records.flatMap { it.safeContent.split(",").map { c -> c.trim() }.filter { c -> c.isNotEmpty() } }
        .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Operational Intelligence",
                style = MaterialTheme.typography.titleLarge,
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Active Nodes", "$activeCount", AppSuccess, Modifier.weight(1f))
                MetricCard("Dead Weight", "$offlineCount", AppError, Modifier.weight(1f))
                MetricCard("Avg Channels", "${if(activeCount > 0) totalChannels / activeCount else 0}", AppPrimary, Modifier.weight(1f))
            }
        }

        if (expiryGroups.isNotEmpty()) {
            item {
                AnalyticsCard("Subscription Expiry Horizon") {
                    SegmentedProgressBar(data = expiryGroups)
                }
            }
        }

        if (providerDeadWeight.isNotEmpty()) {
            item {
                AnalyticsCard("Provider Dead Weight (% Offline)") {
                    AnimatedHorizontalBarChart(data = providerDeadWeight.map { Pair(it.first, it.second) }, isPercentage = true, colorOverride = AppError)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AnalyticsCard("Hardware Limits") {
                        MiniDonutChart(data = connectionLimits)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    AnalyticsCard("Content Vectors") {
                        MiniDonutChart(data = contentCounts.take(4))
                    }
                }
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
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun MetricCard(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
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
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = AppTextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SegmentedProgressBar(data: List<Pair<String, Int>>) {
    val total = data.sumOf { it.second }.toFloat()
    if (total == 0f) return
    val colors = listOf(AppError, Color(0xFFF59E0B), Color(0xFFFBBF24), AppPrimary, AppSuccess)

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1500, easing = FastOutSlowInEasing))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(24.dp).background(AppSurfaceVariant, RoundedCornerShape(12.dp))) {
            data.forEachIndexed { index, (_, count) ->
                val weight = (count / total) * animationProgress.value
                if (weight > 0f) {
                    Box(modifier = Modifier.fillMaxHeight().weight(weight).background(colors[index % colors.size], RoundedCornerShape(12.dp)))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEachIndexed { index, (name, count) ->
                val pct = ((count / total) * 100).toInt()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(name, color = AppTextPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("$count nodes ($pct%)", color = AppTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AnimatedHorizontalBarChart(data: List<Pair<String, Float>>, isPercentage: Boolean = false, colorOverride: Color? = null) {
    val maxVal = if (isPercentage) 100f else (data.maxOfOrNull { it.second } ?: 1f)
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
                    Text(if (isPercentage) "${value.toInt()}%" else value.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = AppTextSecondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(AppSurfaceVariant, RoundedCornerShape(7.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(colorOverride ?: AppPrimary, RoundedCornerShape(7.dp)))
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
fun MiniDonutChart(data: List<Pair<String, Int>>) {
    val total = data.sumOf { it.second }.toFloat()
    val colors = listOf(AppPrimary, Color(0xFF14B8A6), Color(0xFF8B5CF6), Color(0xFFF59E0B), Color(0xFFEC4899))
    
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing))
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasSize = size.minDimension
                val strokeWidth = 20.dp.toPx()
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
        Spacer(modifier = Modifier.height(16.dp))
        data.forEachIndexed { index, (name, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("$name", color = AppTextPrimary, style = MaterialTheme.typography.labelSmall)
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
