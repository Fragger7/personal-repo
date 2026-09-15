import re

f_archive = "project-strong/android/app/src/main/java/com/projectstrong/iptv/data/ArchiveManager.kt"
with open(f_archive, "r") as f:
    content = f.read()

# 1. Remove the entire data class CommittedRecord from ArchiveManager
content = re.sub(r'data class CommittedRecord\([\s\S]*?\}\n\n', '', content)

# 2. Fix hasExactDuplicate
content = content.replace("it.host ==", "it.safeBaseUrl ==")
content = content.replace("it.username ==", "it.safeUser ==")
content = content.replace("it.password ==", "it.safePass ==")
content = content.replace("it.mac ==", "it.safeMac ==")

with open(f_archive, "w") as f:
    f.write(content)

f_commit = "project-strong/android/app/src/main/java/com/projectstrong/iptv/data/CommittedManager.kt"
with open(f_commit, "r") as f:
    content = f.read()

# 2. Fix hasExactDuplicate
content = content.replace("it.host ==", "it.safeBaseUrl ==")
content = content.replace("it.username ==", "it.safeUser ==")
content = content.replace("it.password ==", "it.safePass ==")
content = content.replace("it.mac ==", "it.safeMac ==")

with open(f_commit, "w") as f:
    f.write(content)

