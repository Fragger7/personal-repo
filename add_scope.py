def add_scope(filepath, func_name):
    with open(filepath, 'r') as f:
        content = f.read()

    content = content.replace(
        "val clipboardManager = LocalClipboardManager.current",
        "val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()\n    val clipboardManager = LocalClipboardManager.current"
    )
    with open(filepath, 'w') as f:
        f.write(content)

add_scope("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt", "CommittedDetailScreen")
add_scope("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt", "ArchiveDetailScreen")
