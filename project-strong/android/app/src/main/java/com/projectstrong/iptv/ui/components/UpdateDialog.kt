package com.projectstrong.iptv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectstrong.iptv.network.UpdateState

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDownloadRequested: (downloadUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (updateState is UpdateState.Idle || updateState is UpdateState.Checking) {
        return
    }

    AlertDialog(
        onDismissRequest = {
            // Prevent dismiss during download
            if (updateState !is UpdateState.Downloading && updateState !is UpdateState.ReadyToInstall) {
                onDismiss()
            }
        },
        icon = {
            Icon(Icons.Filled.SystemUpdate, contentDescription = "Update Available", tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                text = when (updateState) {
                    is UpdateState.Available -> "Update Available (v${updateState.version})"
                    is UpdateState.Downloading -> "Downloading Update"
                    is UpdateState.ReadyToInstall -> "Ready to Install"
                    is UpdateState.Error -> "Update Failed"
                    else -> "Update"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateState) {
                    is UpdateState.Available -> {
                        Text(
                            text = "A new version of Sherlock Streams is available. Would you like to download and install it now?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(updateState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "Launching native installer. Please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateState.Error -> {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Available -> {
                    Button(onClick = { onDownloadRequested(updateState.downloadUrl) }) {
                        Text("Update Now")
                    }
                }
                is UpdateState.Error -> {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (updateState is UpdateState.Available) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
