import re

f_archive = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/ArchiveTab.kt"
with open(f_archive, "r") as f:
    content = f.read()

content = content.replace(")                            Surface(", ")\n                            Surface(")
content = content.replace(")                        Surface(", ")\n                        Surface(")
content = content.replace("ArchiveManager.deleteRecord(", "ArchiveManager.delete(")
content = content.replace("CommittedManager.deleteRecord(", "CommittedManager.delete(")

with open(f_archive, "w") as f:
    f.write(content)

f_commit = "project-strong/android/app/src/main/java/com/projectstrong/iptv/ui/tabs/CommittedTab.kt"
with open(f_commit, "r") as f:
    content = f.read()

content = content.replace("ArchiveManager.deleteRecord(", "ArchiveManager.delete(")
content = content.replace("CommittedManager.deleteRecord(", "CommittedManager.delete(")

with open(f_commit, "w") as f:
    f.write(content)
