def replace_all(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
        
    # 1. Add Mutex
    content = content.replace(
        "private val saveMutex = kotlinx.coroutines.sync.Mutex()",
        "private val cloudMutex = kotlinx.coroutines.sync.Mutex()\n    fun hasLocalChanges(): Boolean = records.any { it.isLocalOnly == true }\n    private val saveMutex = kotlinx.coroutines.sync.Mutex()"
    )
    
    # 2. wrap pushToCloud
    content = content.replace(
        "suspend fun pushToCloud(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {\n        try {",
        "suspend fun pushToCloud(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {\n        cloudMutex.withLock {\n        try {"
    )
    content = content.replace(
        "ToastManager.error(\"Sync Exception: $msg\")\n            return@withContext false\n        }\n    }",
        "ToastManager.error(\"Sync Exception: $msg\")\n            return@withContext false\n        }\n        }\n    }"
    )
    
    # 3. wrap deleteFromCloud
    content = content.replace(
        "suspend fun deleteFromCloud(record: CommittedRecord, token: String): Boolean = withContext(Dispatchers.IO) {\n        try {",
        "suspend fun deleteFromCloud(record: CommittedRecord, token: String): Boolean = withContext(Dispatchers.IO) {\n        cloudMutex.withLock {\n        try {"
    )
    content = content.replace(
        "ToastManager.error(\"Delete Exception: $msg\")\n            return@withContext false\n        }\n    }",
        "ToastManager.error(\"Delete Exception: $msg\")\n            return@withContext false\n        }\n        }\n    }"
    )
    
    with open(filepath, 'w') as f:
        f.write(content)

replace_all("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/CommittedManager.kt")
replace_all("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/ArchiveManager.kt")
