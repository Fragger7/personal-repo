import re

def fix_main(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Add LaunchedEffect(Unit) inside MainDashboard
    pattern1 = r'fun MainDashboard\(\) \{'
    insert1 = r"""fun MainDashboard() {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val token = com.projectstrong.iptv.data.DataStore.githubToken
        if (token.isNotEmpty()) {
            kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) { com.projectstrong.iptv.data.CommittedManager.pullFromCloud(token) }
            kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) { com.projectstrong.iptv.data.ArchiveManager.pullFromCloud(token) }
        }
    }
"""
    if "pullFromCloud(token)" not in content:
        content = re.sub(pattern1, insert1, content, count=1)

    # Add LaunchedEffect(selectedTab)
    pattern2 = r'(var selectedTab by remember \{ mutableIntStateOf\(\d+\) \})'
    insert2 = r"""\1
    
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        if (selectedTab == 4) { // CommittedTab
            val token = com.projectstrong.iptv.data.DataStore.githubToken
            if (token.isNotEmpty() && com.projectstrong.iptv.data.CommittedManager.hasLocalChanges()) {
                kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) { com.projectstrong.iptv.data.CommittedManager.pushToCloud(token) }
            }
        } else if (selectedTab == 5) { // ArchiveTab
            val token = com.projectstrong.iptv.data.DataStore.githubToken
            if (token.isNotEmpty() && com.projectstrong.iptv.data.ArchiveManager.hasLocalChanges()) {
                kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) { com.projectstrong.iptv.data.ArchiveManager.pushToCloud(token) }
            }
        }
    }"""
    
    if "hasLocalChanges()" not in content:
        content = re.sub(pattern2, insert2, content, count=1)

    with open(filepath, 'w') as f:
        f.write(content)

fix_main("project-strong/android/app/src/main/java/com/projectstrong/iptv/MainActivity.kt")
