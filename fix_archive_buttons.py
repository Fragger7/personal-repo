import re

def fix_archive_button(filepath, from_mgr, to_mgr, is_archive):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # 1. Action Icon Button
    old_block_1 = f"""                                            onClick = {{
                                                com.projectstrong.iptv.data.{to_mgr}.addRecord(record)
                                                com.projectstrong.iptv.data.{from_mgr}.delete(record)
                                                com.projectstrong.iptv.ui.components.ToastManager.success("{"Moved to Archived Favorites" if is_archive else "Restored to Committed Data"}")
                                            }}"""
    new_block_1 = f"""                                            onClick = {{
                                                coroutineScope.launch {{
                                                    com.projectstrong.iptv.data.{to_mgr}.addRecord(record)
                                                    com.projectstrong.iptv.data.{from_mgr}.delete(record)
                                                    val token = com.projectstrong.iptv.data.DataStore.githubToken
                                                    if (token.isNotEmpty()) {{
                                                        com.projectstrong.iptv.data.{to_mgr}.pushToCloud(token)
                                                    }}
                                                    com.projectstrong.iptv.ui.components.ToastManager.success("{"Moved to Archived Favorites" if is_archive else "Restored to Committed Data"}")
                                                }}
                                            }}"""
    content = content.replace(old_block_1, new_block_1)

    # 2. IconButton in Header
    old_block_2 = f"""            IconButton(onClick = {{
                com.projectstrong.iptv.data.{to_mgr}.addRecord(record)
                com.projectstrong.iptv.data.{from_mgr}.delete(record)
                onBack()
                com.projectstrong.iptv.ui.components.ToastManager.success("{"Moved to Archived Favorites" if is_archive else "Restored to Committed Data"}")
            }})"""
    new_block_2 = f"""            IconButton(onClick = {{
                coroutineScope.launch {{
                    com.projectstrong.iptv.data.{to_mgr}.addRecord(record)
                    com.projectstrong.iptv.data.{from_mgr}.delete(record)
                    val token = com.projectstrong.iptv.data.DataStore.githubToken
                    if (token.isNotEmpty()) {{
                        com.projectstrong.iptv.data.{to_mgr}.pushToCloud(token)
                    }}
                    onBack()
                    com.projectstrong.iptv.ui.components.ToastManager.success("{"Moved to Archived Favorites" if is_archive else "Restored to Committed Data"}")
                }}
            }})"""
    content = content.replace(old_block_2, new_block_2)

    with open(filepath, 'w') as f:
        f.write(content)

fix_archive_button("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt", "CommittedManager", "ArchiveManager", True)
fix_archive_button("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt", "ArchiveManager", "CommittedManager", False)
