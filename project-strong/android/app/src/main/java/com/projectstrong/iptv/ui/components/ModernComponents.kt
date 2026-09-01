package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.*

@Composable
fun GridHeader(
    text: String,
    width: Dp,
    onClick: (() -> Unit)? = null,
    isSorted: Boolean = false,
    isAscending: Boolean = false
) {
    val indicator = if (isSorted) (if (isAscending) " ▲" else " ▼") else ""
    val textColor = if (isSorted) AppPrimary else AppTextSecondary
    Text(
        text = "${text.uppercase()}$indicator",
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp)
            .let {
                if (onClick != null) it.clickable { onClick() } else it
            }
    )
}

@Composable
fun GridCell(
    text: String,
    width: Dp,
    isBold: Boolean = false,
    color: Color = AppTextPrimary,
    onClick: (() -> Unit)? = null
) {
    Text(
        text = text,
        color = color,
        style = if (isBold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp)
            .let {
                if (onClick != null) it.clickable { onClick() } else it
            }
    )
}

@Composable
fun StatusBadge(status: String, width: Dp) {
    val (bgColor, textColor, borderColor) = when {
        status.contains("Active", true) -> Triple(AppSuccessContainer, Color(0xFF34D399), AppSuccess.copy(alpha = 0.4f))
        status.contains("Block", true) || status.contains("Fail", true) || status.contains("Expired", true) -> Triple(AppErrorContainer, Color(0xFFF87171), AppError.copy(alpha = 0.4f))
        else -> Triple(AppWarningContainer, Color(0xFFFBBF24), AppWarning.copy(alpha = 0.4f))
    }
    
    Box(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Text(
                text = status,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun SyncBadge(isLocal: Boolean, width: Dp) {
    val bgColor = if (isLocal) AppWarningContainer else AppPrimaryContainer
    val textColor = if (isLocal) AppWarning else AppPrimary
    val borderColor = if (isLocal) AppWarning.copy(alpha = 0.4f) else AppPrimary.copy(alpha = 0.4f)
    val text = if (isLocal) "🟡 Local" else "🟢 Synced"

    Box(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AppPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppPrimary
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeaderCard(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppSurfaceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = badgeText,
                            color = AppPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

