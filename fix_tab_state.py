import re

f_path = "project-strong/android/app/src/main/java/com/projectstrong/iptv/MainActivity.kt"
with open(f_path, "r") as f:
    content = f.read()

# Add rememberSaveableStateHolder
content = content.replace(
    'var selectedTab by rememberSaveable { mutableStateOf(4) }',
    'var selectedTab by rememberSaveable { mutableStateOf(4) }\n    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()'
)

# Wrap when(selectedTab)
old_when = """
            when (selectedTab) {
                0 -> com.projectstrong.iptv.ui.tabs.Base64Tab(onNextTab = { selectedTab = 1 })
                1 -> com.projectstrong.iptv.ui.tabs.ScannerTab(onNextTab = { selectedTab = 2 })
                2 -> com.projectstrong.iptv.ui.tabs.XtreamTab(onNextTab = { selectedTab = 3 })
                3 -> com.projectstrong.iptv.ui.tabs.StalkerTab(onNextTab = { selectedTab = 4 })
                4 -> com.projectstrong.iptv.ui.tabs.CommittedTab()
                5 -> com.projectstrong.iptv.ui.tabs.ArchiveTab()
                6 -> com.projectstrong.iptv.ui.tabs.AnalyticsTab(onNavigateToCommitted = { selectedTab = 4 })
            }
"""

new_when = """
            saveableStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    0 -> com.projectstrong.iptv.ui.tabs.Base64Tab(onNextTab = { selectedTab = 1 })
                    1 -> com.projectstrong.iptv.ui.tabs.ScannerTab(onNextTab = { selectedTab = 2 })
                    2 -> com.projectstrong.iptv.ui.tabs.XtreamTab(onNextTab = { selectedTab = 3 })
                    3 -> com.projectstrong.iptv.ui.tabs.StalkerTab(onNextTab = { selectedTab = 4 })
                    4 -> com.projectstrong.iptv.ui.tabs.CommittedTab()
                    5 -> com.projectstrong.iptv.ui.tabs.ArchiveTab()
                    6 -> com.projectstrong.iptv.ui.tabs.AnalyticsTab(onNavigateToCommitted = { selectedTab = 4 })
                }
            }
"""

content = content.replace(old_when.strip(), new_when.strip())

with open(f_path, "w") as f:
    f.write(content)

