package com.projectstrong.iptv.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.ui.theme.*

/**
 * Premium Hero Banner for Sherlock Streams
 * Displays active metrics, real-time node discoveries, and quick status overview.
 */
@Composable
fun HeroBanner(
    activeNodesCount: Int,
    committedCount: Int,
    onOpenScanner: () -> Unit,
    onOpenCommitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    AppPrimary.copy(alpha = 0.5f),
                    AppSecondary.copy(alpha = 0.35f),
                    Color(0xFF0F766E).copy(alpha = 0.3f)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF131D33),
                            Color(0xFF0F172A),
                            Color(0xFF0A1120)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Row: Title & Subtitle + Detective Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Glowing Sherlock Icon Emblem
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(AppPrimary, AppSecondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Sherlock Streams",
                                    color = AppTextPrimary,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppSuccess.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AppSuccess.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "PRO",
                                        color = AppSuccess,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Intelligent IPTV Extraction & Forensic Analytics",
                                color = AppTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Quick Stat Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStatChip(
                        title = "Active Nodes",
                        value = if (activeNodesCount > 0) "$activeNodesCount Online" else "0 Ready",
                        icon = Icons.Default.Sensors,
                        accentColor = if (activeNodesCount > 0) AppSuccess else AppTextMuted,
                        onClick = onOpenScanner,
                        modifier = Modifier.weight(1f)
                    )

                    HeroStatChip(
                        title = "Committed Vault",
                        value = "$committedCount Accounts",
                        icon = Icons.Default.FolderSpecial,
                        accentColor = Color(0xFF38BDF8),
                        onClick = onOpenCommitted,
                        modifier = Modifier.weight(1f)
                    )

                    HeroStatChip(
                        title = "Network Guard",
                        value = if (DataStore.isVpnActive) "VPN Shield" else "Direct ISP",
                        icon = if (DataStore.isVpnActive) Icons.Default.VpnKey else Icons.Default.Dns,
                        accentColor = if (DataStore.isVpnActive) Color(0xFF38BDF8) else AppWarning,
                        onClick = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatChip(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppSurfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, AppSurfaceBorder),
        modifier = modifier.let { if (onClick != null) it.clickable { onClick() } else it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = value,
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
