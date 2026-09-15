import re

f_path = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt"
with open(f_path, "r") as f:
    content = f.read()

# Add Archive button before the Delete button
archive_btn = """
                                        GridActionIconButton(
                                            icon = Icons.Default.Archive,
                                            tooltip = "Move to Archived Favorites",
                                            color = AppPrimary,
                                            onClick = {
                                                com.projectstrong.iptv.data.ArchiveManager.addRecord(record)
                                                com.projectstrong.iptv.data.CommittedManager.delete(record)
                                                com.projectstrong.iptv.ui.components.ToastManager.success("Moved to Archived Favorites")
                                            }
                                        )

                                        GridActionIconButton(
                                            icon = Icons.Default.Delete,"""

content = content.replace("                                        GridActionIconButton(\n                                            icon = Icons.Default.Delete,", archive_btn)

# Increase GridHeader size from 200.dp to 240.dp
content = content.replace('GridHeader("Actions", 200.dp)', 'GridHeader("Actions", 240.dp)')
content = content.replace('modifier = Modifier.width(200.dp),', 'modifier = Modifier.width(240.dp),')

with open(f_path, "w") as f:
    f.write(content)

