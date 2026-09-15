import re

def fix_ime(f_path):
    with open(f_path, 'r') as f:
        content = f.read()

    # Find the pattern
    # .verticalScroll(detailScrollState)
    # .imePadding()
    
    # Replace with
    # .imePadding()
    # .verticalScroll(detailScrollState)

    content = re.sub(
        r'\.verticalScroll\(detailScrollState\)\s*\.imePadding\(\)',
        '.imePadding()\n            .verticalScroll(detailScrollState)',
        content
    )

    # Also check if pointerInput is there, usually it's better to put imePadding before everything else that affects layout size
    # Let's just swap them exactly.
    with open(f_path, 'w') as f:
        f.write(content)

fix_ime("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt")
fix_ime("project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt")

