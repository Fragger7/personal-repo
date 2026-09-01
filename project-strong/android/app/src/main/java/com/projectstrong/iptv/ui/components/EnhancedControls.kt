package com.projectstrong.iptv.ui.components
import com.projectstrong.iptv.ui.components.core.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectstrong.iptv.ui.theme.*

/**
 * Premium Filter Toggle Switch for 'Active Only' filtering
 */
@Composable
fun FilterToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeCount: Int = 0,
    totalCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateFloatAsState(targetValue = if (checked) 22f else 2f, label = "toggle")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) AppPrimary.copy(alpha = 0.16f) else AppSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (checked) AppPrimary.copy(alpha = 0.45f) else AppSurfaceBorder
        ),
        modifier = modifier.clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme-adaptive pill switch track
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (checked) AppPrimary else AppSurfaceBorder)
                    .padding(2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (checked) Color.White else AppTextSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AppPrimary)
                        )
                    }
                }
            }

            // Descriptive text label with counter badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Active Only",
                    color = if (checked) AppPrimary else AppTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                )
                if (activeCount > 0) {
                    Text(
                        text = if (checked) "($activeCount)" else "($activeCount/$totalCount)",
                        color = if (checked) AppSuccess else AppTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Compact Action Icon Button for Data Tables to avoid truncated text
 */
@Composable
fun GridActionIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    color: Color = AppPrimary,
    bgColor: Color = color.copy(alpha = 0.15f),
    borderColor: Color = color.copy(alpha = 0.4f),
    size: Dp = 34.dp,
    iconSize: Dp = 16.dp,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) bgColor else AppSurfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (enabled) borderColor else AppSurfaceBorder),
        modifier = Modifier
            .size(size)
            .let {
                if (enabled) it.clickable { onClick() } else it
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                SherlockCircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    tint = if (enabled) color else AppTextMuted,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
