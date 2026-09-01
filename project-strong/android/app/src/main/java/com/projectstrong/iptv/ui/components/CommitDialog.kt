package com.projectstrong.iptv.ui.components
import com.projectstrong.iptv.ui.components.core.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.DataStore
import com.projectstrong.iptv.ui.theme.*
import com.projectstrong.iptv.utils.ClipboardHelper

@Composable
fun CommitAccountDialog(
    type: String,
    baseUrl: String,
    user: String = "",
    pass: String = "",
    mac: String = "",
    status: String = "🟢 Active",
    expires: String = "",
    daysLeft: String = "",
    channels: String = "",
    vods: String = "",
    activeConn: String = "",
    maxConn: String = "",
    provider: String = "Unknown",
    serverTimezone: String = "",
    initialNotes: String = "",
    sourceLink: String = "Direct Ingestion",
    originLink: String = "",
    onDismiss: () -> Unit,
    onCommitted: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var notes by remember { mutableStateOf(initialNotes) }
    
    // Auto-populate Source Link: check node sourceLink first, fallback to DataStore.scannerSourceLink
    var sourceLinkInput by remember {
        val candidate = if (sourceLink.isNotBlank() && sourceLink != "Direct Ingestion") {
            sourceLink
        } else if (DataStore.scannerSourceLink.isNotBlank() && DataStore.scannerSourceLink != "Direct Ingestion") {
            DataStore.scannerSourceLink
        } else ""
        mutableStateOf(candidate)
    }

    // Auto-populate Origin Link: check node originLink first, fallback to DataStore.scannerOriginLink
    var originLinkInput by remember {
        val candidate = if (originLink.isNotBlank()) {
            originLink
        } else if (DataStore.scannerOriginLink.isNotBlank()) {
            DataStore.scannerOriginLink
        } else ""
        mutableStateOf(candidate)
    }

    val resolvedProvider = remember(baseUrl, provider) {
        if (provider.isNotEmpty() && provider != "Unknown" && provider != "Unbranded") {
            provider
        } else {
            val profile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(baseUrl)
            if (profile?.isIdentified == true) profile.cleanBrand else "Unbranded"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        SherlockCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Commit to Saved Accounts",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Preview
                SherlockCard(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurfaceVariant),
                    border = BorderStroke(1.dp, AppSurfaceBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HOST: $baseUrl",
                                color = AppPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (resolvedProvider.isNotEmpty() && resolvedProvider != "Unbranded") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AppPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "🎯 $resolvedProvider",
                                        color = AppPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (user.isNotEmpty()) {
                            Text(
                                text = "USER: $user | PASS: $pass",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (mac.isNotEmpty()) {
                            Text(
                                text = "MAC: $mac",
                                color = AppTextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (expires.isNotEmpty() && expires != "N/A") {
                            Text(
                                text = "Expires: $expires ($daysLeft days left)",
                                color = AppTextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Source Link / Pastebin Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source Link / Payload URL",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (sourceLinkInput.isNotEmpty()) {
                        Text(
                            text = "Auto-Filled",
                            color = Color(0xFF34D399),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SherlockTextField(
                    value = sourceLinkInput,
                    onValueChange = { sourceLinkInput = it },
                    placeholder = { Text("e.g. https://pastebin.com/raw/... or https://paste.sh/...", color = AppTextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = "Source", tint = AppPrimary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clip = ClipboardHelper.getSafeClipboardText(context, clipboardManager)
                                if (!clip.isNullOrBlank()) {
                                    sourceLinkInput = clip.trim()
                                    ToastManager.info("Pasted Source Link from clipboard")
                                } else {
                                    ToastManager.warning("Clipboard is empty")
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AppPrimary, modifier = Modifier.size(16.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedContainerColor = AppSurfaceVariant,
                        unfocusedContainerColor = AppSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Origin / Forum Thread Link Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Origin / Forum Thread Link",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (originLinkInput.isNotEmpty()) {
                        Text(
                            text = "Auto-Filled",
                            color = Color(0xFFF59E0B),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SherlockTextField(
                    value = originLinkInput,
                    onValueChange = { originLinkInput = it },
                    placeholder = { Text("e.g. https://reddit.com/r/... or Telegram thread", color = AppTextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = "Origin", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clip = ClipboardHelper.getSafeClipboardText(context, clipboardManager)
                                if (!clip.isNullOrBlank()) {
                                    originLinkInput = clip.trim()
                                    ToastManager.info("Pasted Origin Link from clipboard")
                                } else {
                                    ToastManager.warning("Clipboard is empty")
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedContainerColor = AppSurfaceVariant,
                        unfocusedContainerColor = AppSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Notes Field
                Text(
                    text = "Notes & Annotations (Optional)",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                SherlockTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("e.g. Living room TV, US 4K channels, backup link...", color = AppTextMuted) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppTextPrimary,
                        unfocusedTextColor = AppTextPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppSurfaceBorder,
                        focusedContainerColor = AppSurfaceVariant,
                        unfocusedContainerColor = AppSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Saved accounts persist on this device and can be pushed to cloud via GitHub token.",
                    color = AppTextMuted,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp)
                    )
                    PrimaryButton(
                        text = "Save & Commit",
                        color = AppSuccess,
                        onClick = {
                            val finalSource = if (sourceLinkInput.trim().isEmpty()) "Direct Ingestion" else sourceLinkInput.trim()
                            val finalOrigin = originLinkInput.trim().ifEmpty { null }
                            CommittedManager.commit(
                                type = type,
                                baseUrl = baseUrl,
                                user = user,
                                pass = pass,
                                mac = mac,
                                status = status,
                                expires = expires,
                                daysLeft = daysLeft,
                                channels = channels,
                                vods = vods,
                                activeConn = activeConn,
                                maxConn = maxConn,
                                provider = resolvedProvider,
                                serverTimezone = serverTimezone,
                                notes = notes.trim(),
                                sourceLink = finalSource,
                                originLink = finalOrigin
                            )
                            onCommitted()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp)
                    )
                }
            }
        }
    }
}
