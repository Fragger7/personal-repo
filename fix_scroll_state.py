import re
def fix_tab(f_path, master_grid_name):
    with open(f_path, 'r') as f:
        content = f.read()

    # 1. Hoist rememberLazyListState to the top of the Tab composable
    # We find "var showTokenDialog by remember { mutableStateOf(false) }" or similar
    # and insert listState there.
    
    # Actually, it's easier to just find the top of the Tab function.
    tab_def_match = re.search(r'@Composable\s*fun \w+Tab\([^\)]*\)\s*\{', content)
    if tab_def_match:
        tab_def = tab_def_match.group(0)
        if "val listState =" not in content[:content.find("AnimatedContent")]:
            content = content.replace(tab_def, tab_def + "\n    val listState = androidx.compose.foundation.lazy.rememberLazyListState()")

    # 2. Add listState to MasterGrid definition
    master_grid_def_match = re.search(r'fun ' + master_grid_name + r'\([\s\S]*?\) \{', content)
    if master_grid_def_match:
        master_grid_def = master_grid_def_match.group(0)
        if "listState: androidx.compose.foundation.lazy.LazyListState" not in master_grid_def:
            new_master_grid_def = master_grid_def.replace(
                "fun " + master_grid_name + "(",
                "fun " + master_grid_name + "(\n    listState: androidx.compose.foundation.lazy.LazyListState,"
            )
            content = content.replace(master_grid_def, new_master_grid_def)

    # 3. Pass listState when calling MasterGrid
    call_match = re.search(r'' + master_grid_name + r'\(\s*records =', content)
    if call_match:
        content = content.replace(call_match.group(0), master_grid_name + "(\n                listState = listState,\n                records =")
    
    call_match2 = re.search(r'' + master_grid_name + r'\(\s*nodes =', content)
    if call_match2:
        content = content.replace(call_match2.group(0), master_grid_name + "(\n                listState = listState,\n                nodes =")

    # 4. Remove listState definition inside MasterGrid
    content = re.sub(r'\s*val listState = rememberLazyListState\(\)\n', '\n', content)

    with open(f_path, 'w') as f:
        f.write(content)

fix_tab("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt", "CommittedMasterGrid")
fix_tab("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt", "ArchiveMasterGrid")
fix_tab("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/XtreamTab.kt", "XtreamMasterGrid")
fix_tab("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/StalkerTab.kt", "StalkerMasterGrid")

