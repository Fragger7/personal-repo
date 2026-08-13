package com.projectstrong.iptv.ui.components

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

@Composable
fun GridHeader(
    text: String,
    width: Dp,
    onClick: (() -> Unit)? = null,
    isSorted: Boolean = false,
    isAscending: Boolean = false
) {
    val indicator = if (isSorted) (if (isAscending) " ▲" else " ▼") else ""
    val textColor = if (isSorted) Color(0xFF3B82F6) else Color(0xFFA0A0B0)
    Text(
        text = "${text.uppercase()}$indicator",
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp)
            .let {
                if (onClick != null) it.clickable { onClick() } else it
            }
    )
}

@Composable
fun GridCell(text: String, width: Dp, isBold: Boolean = false, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        style = if (isBold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(end = 8.dp)
    )
}

@Composable
fun StatusBadge(status: String, width: Dp) {
    val bgColor = when {
        status.contains("Active", true) -> Color(0xFF10B981).copy(alpha = 0.2f)
        status.contains("Block", true) || status.contains("Fail", true) || status.contains("Expired", true) -> Color(0xFFEF4444).copy(alpha = 0.2f)
        else -> Color(0xFFF59E0B).copy(alpha = 0.2f)
    }
    val textColor = when {
        status.contains("Active", true) -> Color(0xFF34D399)
        status.contains("Block", true) || status.contains("Fail", true) || status.contains("Expired", true) -> Color(0xFFF87171)
        else -> Color(0xFFFBBF24)
    }
    
    Box(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = status,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    color: Color = Color(0xFF3B82F6)
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold, color = Color(0xFF3B82F6))
    }
}
