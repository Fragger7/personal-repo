import re
f_path = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/components/CommitDialog.kt"
with open(f_path, "r") as f:
    content = f.read()

content = content.replace(
    'var notes by remember { mutableStateOf(',
    'var showDuplicateWarning by remember { mutableStateOf(false) }\n    var notes by remember { mutableStateOf('
)

save_btn = """
                    PrimaryButton(
                        text = "Save & Commit",
                        color = AppSuccess,
                        onClick = {
                            rootFocusManager.clearFocus()
                            if (CommittedManager.hasExactDuplicate(type, baseUrl, user, pass, mac)) {
                                showDuplicateWarning = true
                            } else {
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
                                    rooms = selectedRooms.joinToString(", "),
                                    content = selectedContent.joinToString(", "),
                                    sourceLink = finalSource,
                                    originLink = finalOrigin,
                                    egressStatus = egressStatus,
                                    egressDetails = egressDetails
                                )
                                onCommitted()
                            }
                        },
"""
content = re.sub(r'PrimaryButton\(\s*text = "Save & Commit",\s*color = AppSuccess,\s*onClick = \{\s*rootFocusManager\.clearFocus\(\)[\s\S]*?onCommitted\(\)\s*\},\n', save_btn.lstrip(), content)

warning_dialog = """
    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("Duplicate Detected") },
            text = { Text("An exact match for this connection already exists in the Committed Data. Are you sure you want to add a duplicate?") },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateWarning = false
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
                        rooms = selectedRooms.joinToString(", "),
                        content = selectedContent.joinToString(", "),
                        sourceLink = finalSource,
                        originLink = finalOrigin,
                        egressStatus = egressStatus,
                        egressDetails = egressDetails
                    )
                    onCommitted()
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
}"""

content = content.rstrip()
if content.endswith("}"):
    content = content[:-1] + warning_dialog
else:
    print("WARNING: File does not end with }")

with open(f_path, "w") as f:
    f.write(content)

