import re

def fix(f_path):
    with open(f_path, "r") as f:
        content = f.read()

    # Find: val index = records.indexOf(record)
    # Replace with: val index = records.indexOfFirst { it.safeBaseUrl == record.safeBaseUrl && it.safeUser == record.safeUser && it.safeMac == record.safeMac }
    
    content = content.replace(
        "val index = records.indexOf(record)",
        "val index = records.indexOfFirst { it.safeBaseUrl == record.safeBaseUrl && it.safeUser == record.safeUser && it.safeMac == record.safeMac }"
    )

    with open(f_path, "w") as f:
        f.write(content)

fix("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/CommittedManager.kt")
fix("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/ArchiveManager.kt")
