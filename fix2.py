import re

def insert_pull(filepath, filename):
    with open(filepath, 'r') as f:
        content = f.read()

    pull_code = f"""
    suspend fun pullFromCloud(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {{
        cloudMutex.withLock {{
            try {{
                val authToken = token.filter {{ !it.isWhitespace() }}
                if (authToken.isEmpty()) return@withLock false
                
                val client = OkHttpClient.Builder().build()
                val getReq = Request.Builder()
                    .url("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/{filename}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "SherlockStreams/1.0")
                    .header("Authorization", "Bearer $authToken")
                    .build()
                
                val getResp = client.newCall(getReq).execute()
                val getCode = getResp.code
                
                if (getCode != 200) {{
                    getResp.close()
                    return@withLock false
                }}
                
                val jsonResponse = getResp.body?.string() ?: ""
                getResp.close()
                val jsonObj = org.json.JSONObject(jsonResponse)
                val contentB64 = jsonObj.optString("content", "").filter {{ !it.isWhitespace() }}
                if (contentB64.isEmpty()) return@withLock false
                
                val decodedBytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
                val remoteJson = String(decodedBytes, Charsets.UTF_8)
                val list: List<CommittedRecord> = try {{
                    gson.fromJson(remoteJson, Array<CommittedRecord>::class.java)?.toList() ?: emptyList()
                }} catch (e: Exception) {{
                    val type = object : TypeToken<List<CommittedRecord>>() {{}}.type
                    gson.fromJson(remoteJson, type) ?: emptyList()
                }}
                
                val remoteRecords = list.map {{
                    it.copy(
                        baseUrl = normalizeUrl(it.safeBaseUrl),
                        user = it.safeUser.trim(),
                        mac = it.safeMac.trim().uppercase()
                    )
                }}
                
                // Union Merge (Remote -> Local)
                val mergedList = remoteRecords.toMutableList()
                for (localRec in records) {{
                    val localBase = normalizeUrl(localRec.safeBaseUrl)
                    val localUser = localRec.safeUser.trim()
                    val localMac = localRec.safeMac.trim().uppercase()
                    
                    val matchIdx = mergedList.indexOfFirst {{ rem ->
                        normalizeUrl(rem.safeBaseUrl).equals(localBase, ignoreCase = true) &&
                        ((localRec.safeType == "Xtream" && rem.safeUser.trim() == localUser) ||
                         (localRec.safeType == "Stalker" && rem.safeMac.trim().equals(localMac, ignoreCase = true)))
                    }}
                    
                    if (matchIdx != -1) {{
                        val existingRem = mergedList[matchIdx]
                        mergedList[matchIdx] = localRec.copy(
                            dateAdded = if (existingRem.safeDateAdded.isNotEmpty()) existingRem.safeDateAdded else localRec.safeDateAdded,
                            notes = if (localRec.safeNotes.isNotEmpty()) localRec.safeNotes else existingRem.safeNotes,
                            isLocalOnly = if (localRec.isLocalOnly == true) true else existingRem.isLocalOnly
                        )
                    }} else {{
                        mergedList.add(0, localRec)
                    }}
                }}
                
                withContext(Dispatchers.Main) {{
                    records.clear()
                    records.addAll(mergedList)
                    sortByDateAddedDescending()
                }}
                save()
                return@withLock true
            }} catch (e: Exception) {{
                e.printStackTrace()
                return@withLock false
            }}
        }}
    }}
"""
    if "suspend fun pullFromCloud" not in content:
        content = content.replace("suspend fun pushToCloud", pull_code + "\n    suspend fun pushToCloud")
        
    with open(filepath, 'w') as f:
        f.write(content)

insert_pull("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/CommittedManager.kt", "committed.json")
insert_pull("project-strong/android/app/src/main/java/com/projectstrong/iptv/data/ArchiveManager.kt", "archivedfavorites.json")
