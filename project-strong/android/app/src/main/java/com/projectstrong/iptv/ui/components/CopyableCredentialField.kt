package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.ui.theme.*
import kotlinx.coroutines.delay

/**
 * High-reliability Copyable Credential Field Row.
 * Ensures the copy button NEVER gets squeezed, truncated, or pushed off screen.
 * Text is given flex weight while copy button has a rigid, uncompressed width + tactile copy feedback.
 */
@Composable
fun CopyableCredentialField(
    label: String,
    value: String,
    toastMessage: String,
    modifier: Modifier = Modifier,
    isMonospaceOrPrimary: Boolean = false,
    maxLines: Int = 1
) {
    val clipboardManager = LocalClipboardManager.current
    var isJustCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isJustCopied) {
        if (isJustCopied) {
            delay(1500)
            isJustCopied = false
        }
    }

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            color = AppTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppSurfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, if (isJustCopied) AppSuccess.copy(alpha = 0.6f) else AppSurfaceBorder.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.ifEmpty { "—" },
                    color = if (isMonospaceOrPrimary) AppTextPrimary else AppTextPrimary.copy(alpha = 0.9f),
                    style = if (isMonospaceOrPrimary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (isMonospaceOrPrimary) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                // Rigid copy button container that cannot be shrunk
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isJustCopied) AppSuccessContainer else AppPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isJustCopied) AppSuccess.copy(alpha = 0.5f) else AppPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .requiredSize(32.dp)
                        .clickable {
                            if (value.isNotEmpty() && value != "—") {
                                clipboardManager.setText(AnnotatedString(value))
                                isJustCopied = true
                                ToastManager.success(toastMessage)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isJustCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy $label",
                            tint = if (isJustCopied) AppSuccess else Color(0xFF60A5FA),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
