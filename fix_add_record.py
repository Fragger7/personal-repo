import re
def fix(f_path):
    with open(f_path, "r") as f:
        content = f.read()
    
    # Replace the bad addRecord logic
    old_code = """
    fun addRecord(record: CommittedRecord) {
        val cleanRecord = record.copy(isLocal = false)
        if (!records.any { it.host == cleanRecord.host && it.username == cleanRecord.username && it.mac == cleanRecord.mac }) {
            records.add(0, cleanRecord)
            saveLocalOnly()
            pushToCloud()
        }
    }
"""
    new_code = """
    fun addRecord(record: CommittedRecord) {
        val cleanRecord = record.copy(isLocalOnly = true)
        if (!records.any { it.safeBaseUrl == cleanRecord.safeBaseUrl && it.safeUser == cleanRecord.safeUser && it.safeMac == cleanRecord.safeMac }) {
            records.add(0, cleanRecord)
            save()
        }
    }
"""
    # Just to be safe with indentation
    content = re.sub(r'\s*fun addRecord\(record: CommittedRecord\) \{[\s\S]*?pushToCloud\(\)\s*\}\s*\}\n', '\n' + new_code + '\n', content)
    with open(f_path, "w") as f:
        f.write(content)

fix("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/CommittedManager.kt")
fix("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/ArchiveManager.kt")
