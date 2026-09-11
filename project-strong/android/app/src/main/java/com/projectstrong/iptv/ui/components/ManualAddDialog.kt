package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.projectstrong.iptv.data.CommittedManager
import com.projectstrong.iptv.data.CommittedRecord
import com.projectstrong.iptv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onCommitted: () -> Unit
) {
    var selectedType by remember { mutableStateOf("Xtream") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedRooms by remember { mutableStateOf(setOf<String>()) }
    var selectedContent by remember { mutableStateOf(setOf<String>()) }
    var sourceLink by remember { mutableStateOf("Manual Entry") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppSurfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manually Add Connection", color = AppTextPrimary, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextSecondary)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Type Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Xtream", "Stalker", "M3U").forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = { selectedType = t },
                                label = { Text(t) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = AppPrimary
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / Server URL", color = AppTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedTextColor = AppTextPrimary
                        )
                    )

                    if (selectedType == "Xtream") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = user,
                                onValueChange = { user = it },
                                label = { Text("Username", color = AppTextSecondary) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppPrimary,
                                    unfocusedBorderColor = AppSurfaceBorder,
                                    focusedTextColor = AppTextPrimary
                                )
                            )
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text("Password", color = AppTextSecondary) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppPrimary,
                                    unfocusedBorderColor = AppSurfaceBorder,
                                    focusedTextColor = AppTextPrimary
                                )
                            )
                        }
                    } else if (selectedType == "Stalker") {
                        OutlinedTextField(
                            value = mac,
                            onValueChange = { mac = it },
                            label = { Text("MAC Address (00:1A:79:...)", color = AppTextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppSurfaceBorder,
                                focusedTextColor = AppTextPrimary
                            )
                        )
                    }

                    Divider(color = AppSurfaceBorder, modifier = Modifier.padding(vertical = 4.dp))
                    
                    MultiSelectToggles(
                        label = "Room",
                        options = listOf("Master", "Living", "Guest", "Office", "Basement"),
                        selectedOptions = selectedRooms,
                        onOptionToggled = { selectedRooms = it }
                    )

                    MultiSelectToggles(
                        label = "Content Type",
                        options = listOf("NFL", "Pak", "A", "Philly", "V", "L", "S"),
                        selectedOptions = selectedContent,
                        onOptionToggled = { selectedContent = it }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes", color = AppTextSecondary) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppSurfaceBorder,
                            focusedTextColor = AppTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            if (host.isBlank()) {
                                ToastManager.error("Host is required!")
                                return@Button
                            }
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val dateStr = sdf.format(Date())
                            
                            val record = CommittedRecord(
                                type = selectedType,
                                baseUrl = host.trim(),
                                user = user.trim(),
                                pass = pass.trim(),
                                mac = mac.trim(),
                                status = "🟢 Active",
                                provider = "Unknown",
                                rooms = selectedRooms.joinToString(", "),
                                content = selectedContent.joinToString(", "),
                                notes = notes.trim(),
                                dateAdded = dateStr,
                                isLocalOnly = true,
                                sourceLink = sourceLink,
                                originLink = ""
                            )
                            CommittedManager.records.add(0, record)
                            CommittedManager.saveLocalOnly()
                            ToastManager.success("Manual Connection Added!")
                            onCommitted()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Connection", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
