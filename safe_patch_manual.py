import re
f_path = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/components/ManualAddDialog.kt"
with open(f_path, "r") as f:
    content = f.read()

content = content.replace(
    'var selectedType by remember { mutableStateOf("Xtream") }',
    'var showDuplicateWarning by remember { mutableStateOf(false) }\n    var selectedType by remember { mutableStateOf("Xtream") }'
)

save_btn = """
                    Button(
                        onClick = {
                            if (host.isBlank()) {
                                ToastManager.error("Host is required!")
                                return@Button
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
                            }
                        },
"""
content = re.sub(r'Button\(\s*onClick = \{\s*if \(host\.isBlank\(\)\)[\s\S]*?colors = ButtonDefaults\.buttonColors', save_btn.strip() + '\n                        colors = ButtonDefaults.buttonColors', content)

warning_dialog = """
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
}"""

content = content.rstrip()
if content.endswith("}"):
    content = content[:-1] + warning_dialog
else:
    print("WARNING: File does not end with }")

with open(f_path, "w") as f:
    f.write(content)

