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
    var selectedType by remember { mutableStateOf("Xtream") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var host by remember { mutableStateOf("") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var user by remember { mutableStateOf("") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var pass by remember { mutableStateOf("") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var mac by remember { mutableStateOf("") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var notes by remember { mutableStateOf("") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var selectedRooms by remember { mutableStateOf(setOf<String>()) 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var selectedContent by remember { mutableStateOf(setOf<String>()) 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    var sourceLink by remember { mutableStateOf("Manual Entry") 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


    var showDuplicateWarning by remember { mutableStateOf(false) 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

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
                    
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
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
                        
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                    
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
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
                        
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
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
                    
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

                    Divider(color = AppSurfaceBorder, modifier = Modifier.padding(vertical = 4.dp))
                    
                    MultiSelectToggles(
                        label = "Room",
                        options = listOf("P", "LR", "MB", "MR", "M", "G", "O"),
                        selectedOptions = selectedRooms,
                        onOptionToggled = { selectedRooms = it 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                    )

                    MultiSelectToggles(
                        label = "Content Type",
                        options = listOf("NFL", "Pak", "A", "Philly", "S"),
                        selectedOptions = selectedContent,
                        onOptionToggled = { selectedContent = it 
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
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
                            
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                            if (CommittedManager.hasExactDuplicate(selectedType, host, user, pass, mac)) {
                                showDuplicateWarning = true
                            } else {
                                CommittedManager.commit(
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
                                    sourceLink = sourceLink,
                                    originLink = ""
                                )
                                ToastManager.success("Manual Connection Added!")
                                onCommitted()
                                onDismiss()
                            
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Connection", fontWeight = FontWeight.Bold)
                    
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
                
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
            
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
        
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
                    CommittedManager.commit(
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
                        sourceLink = sourceLink,
                        originLink = ""
                    )
                    ToastManager.success("Duplicate Connection Added!")
                    onCommitted()
                    onDismiss()
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
