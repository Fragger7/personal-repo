package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.projectstrong.iptv.data.CommittedManager

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
    onDismiss: () -> Unit,
    onCommitted: () -> Unit
) {
    var notes by remember { mutableStateOf(initialNotes) }

    val resolvedProvider = remember(baseUrl, provider) {
        if (provider.isNotEmpty() && provider != "Unknown" && provider != "Unbranded") {
            provider
        } else {
            val profile = com.projectstrong.iptv.data.ProviderIntelligenceManager.getProfile(baseUrl)
            if (profile?.isIdentified == true) profile.cleanBrand else "Unbranded"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
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
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Preview
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12121A)),
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
                                color = Color(0xFF60A5FA),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (resolvedProvider.isNotEmpty() && resolvedProvider != "Unbranded") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFC084FC).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "🎯 $resolvedProvider",
                                        color = Color(0xFFC084FC),
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
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (mac.isNotEmpty()) {
                            Text(
                                text = "MAC: $mac",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (expires.isNotEmpty() && expires != "N/A") {
                            Text(
                                text = "Expires: $expires ($daysLeft days left)",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes Field
                Text(
                    text = "Notes & Annotations (Optional)",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("e.g. Living room TV, US 4K channels, backup link...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedContainerColor = Color(0xFF12121A),
                        unfocusedContainerColor = Color(0xFF12121A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Saved accounts persist on this device and can be pushed to cloud via GitHub token.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                        color = Color(0xFF10B981),
                        onClick = {
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
                                notes = notes.trim()
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
