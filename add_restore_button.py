import re

f_path = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt"
with open(f_path, "r") as f:
    content = f.read()

# Add Restore button before the Delete button
restore_btn = """
                                        GridActionIconButton(
                                            icon = Icons.Default.Restore,
                                            tooltip = "Restore to Committed Data",
                                            color = AppPrimary,
                                            onClick = {
                                                com.projectstrong.iptv.data.CommittedManager.addRecord(record)
                                                com.projectstrong.iptv.data.ArchiveManager.delete(record)
                                                com.projectstrong.iptv.ui.components.ToastManager.success("Restored to Committed")
                                            }
                                        )

                                        GridActionIconButton(
                                            icon = Icons.Default.Delete,"""

content = content.replace("                                        GridActionIconButton(\n                                            icon = Icons.Default.Delete,", restore_btn)

# Increase GridHeader size from 200.dp to 240.dp
content = content.replace('GridHeader("Actions", 200.dp)', 'GridHeader("Actions", 240.dp)')
content = content.replace('modifier = Modifier.width(200.dp),', 'modifier = Modifier.width(240.dp),')

with open(f_path, "w") as f:
    f.write(content)

