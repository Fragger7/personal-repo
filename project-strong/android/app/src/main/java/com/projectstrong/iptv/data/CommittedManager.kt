package com.projectstrong.iptv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommittedRecord(
    @SerializedName("type") val type: String? = "Unknown",
    @SerializedName("base_url") val baseUrl: String? = "",
    @SerializedName("username") val user: String? = "",
    @SerializedName("password") val pass: String? = "",
    @SerializedName("mac") val mac: String? = "",
    @SerializedName("Status") val status: String? = "🟢 Active",
    @SerializedName("Expires") val expires: String? = "",
    @SerializedName("Days Left") val daysLeft: Any? = null,
    @SerializedName("Channels") val channels: Any? = null,
    @SerializedName("VODs") val vods: Any? = null,
    @SerializedName("Active Conns") val activeConn: Any? = null,
    @SerializedName("Max Conns") val maxConn: Any? = null,
    @SerializedName("Provider") val provider: String? = "Unknown",
    @SerializedName("Server Timezone") val serverTimezone: String? = "",
    @SerializedName("Server Time") val serverTime: String? = "",
    @SerializedName("M3U Link") val m3uLink: String? = "",
    @SerializedName("Source") val source: String? = "",
    @SerializedName("Source Link") val sourceLink: String? = "Direct Ingestion",
    @SerializedName("Origin Link", alternate = ["origin_link", "Origin", "origin", "Origin URL", "origin_url"]) val originLink: String? = null,
    @SerializedName("source_archive_file") val sourceArchiveFile: String? = null,
    @SerializedName("egress_status", alternate = ["Egress Status", "egressStatus", "stream_status", "Stream Status"]) val egressStatus: String? = null,
    @SerializedName("egress_details", alternate = ["Egress Details", "egressDetails", "stream_details"]) val egressDetails: String? = null,
    @SerializedName("Notes") val notes: String? = "",
    @SerializedName("Date Selected") val dateAdded: String? = null,
    @SerializedName("isLocalOnly") val isLocalOnly: Boolean? = false
) {
    val safeType get() = type ?: source ?: "Unknown"
    val safeBaseUrl get() = baseUrl ?: ""
    val safeUser get() = user ?: ""
    val safePass get() = pass ?: ""
    val safeMac get() = mac ?: ""
    val safeStatus get() = status ?: "🟢 Active"
    val safeExpires get() = expires ?: ""
    val safeDaysLeft get() = daysLeft?.toString() ?: ""
    val safeChannels get() = channels?.toString() ?: ""
    val safeVods get() = vods?.toString() ?: ""
    val safeActiveConn get() = activeConn?.toString() ?: ""
    val safeMaxConn get() = maxConn?.toString() ?: ""
    val safeSourceLink get() = if (sourceLink.isNullOrBlank()) "Direct Ingestion" else sourceLink
    val safeOriginLink get() = originLink ?: ""
    val hasOrigin get() = safeOriginLink.isNotBlank() && (safeOriginLink.startsWith("http://") || safeOriginLink.startsWith("https://"))
    val safeSourceArchiveFile get() = sourceArchiveFile ?: ""
    val safeEgressStatus get() = egressStatus ?: "Unchecked"
    val safeEgressDetails get() = egressDetails ?: ""
    val safeProvider: String
        get() {
            if (!provider.isNullOrEmpty() && provider != "Unknown") return provider
            return try {
                val uri = java.net.URI(safeBaseUrl)
                uri.host ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        }
    val safeTimezone get() = serverTimezone ?: ""
    val safeNotes get() = notes ?: ""
    val safeDateAdded get() = dateAdded ?: ""
    val isLocal get() = isLocalOnly == true
}

object CommittedManager {
    val records = mutableStateListOf<CommittedRecord>()
    private lateinit var file: File
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val gson = Gson()

    private const val PREFS_NAME = "iptv_prefs"
    private const val KEY_GITHUB_TOKEN = "github_token"

    fun init(context: Context) {
        appContext = context.applicationContext
        file = File(context.filesDir, "committed.json")
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        DataStore.githubToken = savedToken
        load()
    }

    fun saveGithubToken(token: String) {
        DataStore.githubToken = token
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun clearGithubToken() {
        DataStore.githubToken = ""
        prefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    private fun sortByDateAddedDescending() {
        records.sortWith(
            compareByDescending<CommittedRecord> { it.safeDateAdded }
                .thenByDescending { it.safeDaysLeft.toIntOrNull() ?: -1 }
        )
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    private fun load() {
        if (!file.exists()) {
            records.clear()
            return
        }
        try {
            val json = file.readText()
            if (json.isNotBlank()) {
                val type = object : TypeToken<List<CommittedRecord>>() {}.type
                val list: List<CommittedRecord> = gson.fromJson(json, type)
                records.clear()
                records.addAll(list.map { it.copy(baseUrl = normalizeUrl(it.safeBaseUrl)) })
                sortByDateAddedDescending()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    
    private fun save() {
        val snapshot = records.toList()
        CoroutineScope(Dispatchers.IO).launch {
            saveMutex.withLock {
                try {
                    val json = gson.toJson(snapshot)
                    file.writeText(json)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun commit(
        type: String,
        baseUrl: String,
        user: String = "",
        pass: String = "",
        mac: String = "",
        status: String = "🟢 Active",
        expires: String = "",
        daysLeft: String = "",
        channels: String = "",
        vods: String = "",
        activeConn: String = "",
        maxConn: String = "",
        provider: String = "Unknown",
        serverTimezone: String = "",
        notes: String = "",
        sourceLink: String = "Direct Ingestion",
        originLink: String? = null,
        sourceArchiveFile: String? = null,
        egressStatus: String? = null,
        egressDetails: String? = null
    ) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val cleanBaseUrl = normalizeUrl(baseUrl)
        val cleanUser = user.trim()
        val cleanMac = mac.trim().uppercase()
        val cleanOrigin = originLink?.trim()?.ifEmpty { null }
        val m3u = if (type.contains("Xtream", ignoreCase = true) && cleanUser.isNotEmpty()) {
            "$cleanBaseUrl/get.php?username=$cleanUser&password=$pass&type=m3u_plus&output=${com.projectstrong.iptv.data.SettingsManager.streamOutputFormat}"
        } else ""

        val finalArchiveFile = if (!sourceArchiveFile.isNullOrBlank()) {
            sourceArchiveFile
        } else if (sourceLink.isNotBlank() && sourceLink != "Direct Ingestion") {
            val generated = SourceArchiveManager.generateArchiveFileName(sourceLink)
            val snapshot = DataStore.sourceSnapshots[sourceLink]
            if (::appContext.isInitialized && !snapshot.isNullOrBlank()) {
                SourceArchiveManager.saveArchiveLocally(appContext, generated, snapshot)
            }
            generated
        } else null

        val newRecord = CommittedRecord(
            type = type,
            baseUrl = cleanBaseUrl,
            user = cleanUser,
            pass = pass,
            mac = cleanMac,
            status = status,
            expires = expires,
            daysLeft = daysLeft,
            channels = channels,
            vods = vods,
            activeConn = activeConn,
            maxConn = maxConn,
            provider = provider,
            serverTimezone = serverTimezone,
            m3uLink = m3u,
            source = type,
            sourceLink = sourceLink,
            originLink = cleanOrigin,
            sourceArchiveFile = finalArchiveFile,
            egressStatus = egressStatus,
            egressDetails = egressDetails,
            notes = notes,
            dateAdded = nowStr,
            isLocalOnly = true
        )

        // Check if existing (using normalized comparison)
        val existingIndex = records.indexOfFirst {
            normalizeUrl(it.safeBaseUrl).equals(cleanBaseUrl, ignoreCase = true) &&
            ((type == "Xtream" && it.safeUser.trim() == cleanUser) ||
             (type == "Stalker" && it.safeMac.trim().equals(cleanMac, ignoreCase = true)))
        }

        if (existingIndex != -1) {
            val existing = records[existingIndex]
            records[existingIndex] = newRecord.copy(
                dateAdded = if (existing.safeDateAdded.isNotEmpty()) existing.safeDateAdded else nowStr,
                notes = if (notes.isNotEmpty()) notes else existing.safeNotes,
                originLink = cleanOrigin ?: existing.originLink,
                sourceArchiveFile = finalArchiveFile ?: existing.sourceArchiveFile,
                egressStatus = egressStatus ?: existing.egressStatus,
                egressDetails = egressDetails ?: existing.egressDetails
            )
            ToastManager.success("Updated existing record in Saved Accounts")
        } else {
            records.add(0, newRecord)
            ToastManager.success("Saved $type connection to Saved Accounts")
        }
        sortByDateAddedDescending()
        save()
    }

    suspend fun probeEgressForRecord(record: CommittedRecord): CommittedRecord = withContext(Dispatchers.IO) {
        if (record.safeType != "Xtream" || record.safeUser.isBlank() || record.safePass.isBlank()) {
            return@withContext record
        }
        val egressResult = IPTVClient.probeStreamEgress(record.safeBaseUrl, record.safeUser, record.safePass)
        val updated = when (egressResult) {
            is com.projectstrong.iptv.network.StreamEgressResult.Verified -> {
                record.copy(
                    egressStatus = "🟢 Verified (${egressResult.latencyMs}ms)",
                    egressDetails = "Stream #${egressResult.streamId} responded with HTTP 200 OK (${egressResult.contentType ?: "video/mp2t"}) in ${egressResult.latencyMs}ms"
                )
            }
            is com.projectstrong.iptv.network.StreamEgressResult.GhostBlocked -> {
                val label = if (egressResult.code == 456) "👻 Ghost (456)" else if (egressResult.code == 884) "🔒 Dump Lock (884)" else "🛡️ Blocked (${egressResult.code})"
                record.copy(
                    egressStatus = label,
                    egressDetails = egressResult.technicalDetails
                )
            }
            is com.projectstrong.iptv.network.StreamEgressResult.Inconclusive -> {
                record.copy(
                    egressStatus = "❓ Inconclusive",
                    egressDetails = egressResult.reason
                )
            }
        }
        val idx = records.indexOfFirst {
            normalizeUrl(it.safeBaseUrl).equals(normalizeUrl(record.safeBaseUrl), ignoreCase = true) &&
            it.safeUser.trim() == record.safeUser.trim() &&
            it.safePass == record.safePass
        }
        if (idx >= 0) {
            withContext(Dispatchers.Main) {
                records[idx] = updated
            }
            save()
        }
        updated
    }

    fun delete(record: CommittedRecord, token: String = DataStore.githubToken, onComplete: ((Boolean) -> Unit)? = null) {
        records.remove(record)
        save()
        ToastManager.info("Account removed from Saved Records")

        val authToken = token.trim()
        if (authToken.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val success = deleteFromCloud(record, authToken)
                withContext(Dispatchers.Main) {
                    if (success) {
                        ToastManager.success("Deleted from GitHub repository")
                    } else {
                        ToastManager.warning("Local deletion complete (Cloud sync pending)")
                    }
                    onComplete?.invoke(success)
                }
            }
        } else {
            onComplete?.invoke(true)
        }
    }

    suspend fun deleteFromCloud(record: CommittedRecord, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val getUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val getConnection = getUrl.openConnection() as java.net.HttpURLConnection
            getConnection.requestMethod = "GET"
            getConnection.useCaches = false
            getConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            getConnection.setRequestProperty("User-Agent", "SherlockStreams/1.0")
            getConnection.setRequestProperty("Authorization", "Bearer $authToken")
            getConnection.connectTimeout = 6000
            getConnection.readTimeout = 6000

            if (getConnection.responseCode != 200) return@withContext false

            val jsonResponse = getConnection.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = org.json.JSONObject(jsonResponse)
            val sha = jsonObj.optString("sha", "")
            val contentB64 = jsonObj.optString("content", "").replace("\n", "")

            val decodedBytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
            val remoteJson = String(decodedBytes, Charsets.UTF_8)
            val type = object : TypeToken<List<CommittedRecord>>() {}.type
            val remoteList: List<CommittedRecord> = gson.fromJson(remoteJson, type)

            val targetBase = normalizeUrl(record.safeBaseUrl)
            val targetUser = record.safeUser.trim()
            val targetMac = record.safeMac.trim().uppercase()

            val updatedRemote = remoteList.filterNot { rem ->
                normalizeUrl(rem.safeBaseUrl).equals(targetBase, ignoreCase = true) &&
                ((record.safeType == "Xtream" && rem.safeUser.trim() == targetUser) ||
                 (record.safeType == "Stalker" && rem.safeMac.trim().equals(targetMac, ignoreCase = true)))
            }

            val cleanForCloud = updatedRemote.map { it.copy(isLocalOnly = null) }
            val jsonContent = gson.toJson(cleanForCloud)
            val encodedContent = android.util.Base64.encodeToString(jsonContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

            val putUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val putConnection = putUrl.openConnection() as java.net.HttpURLConnection
            putConnection.requestMethod = "PUT"
            putConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            putConnection.setRequestProperty("User-Agent", "SherlockStreams/1.0")
            putConnection.setRequestProperty("Authorization", "Bearer $authToken")
            putConnection.setRequestProperty("Content-Type", "application/json")
            putConnection.doOutput = true

            val payload = org.json.JSONObject().apply {
                put("message", "Delete ${record.safeBaseUrl} (${if (record.safeType == "Xtream") record.safeUser else record.safeMac}) via Android")
                put("content", encodedContent)
                put("sha", sha)
            }

            putConnection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val code = putConnection.responseCode
            return@withContext (code == 200 || code == 201)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun syncFromCloud(): List<CommittedRecord>? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "SherlockStreams/1.0")
            if (DataStore.githubToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${DataStore.githubToken}")
            }
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (connection.responseCode == 200) {
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(jsonResponse)
                val contentB64 = jsonObj.optString("content", "").replace("\n", "")

                if (contentB64.isNotEmpty()) {
                    val decodedBytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
                    val json = String(decodedBytes, Charsets.UTF_8)

                    val type = object : TypeToken<List<CommittedRecord>>() {}.type
                    val remoteList: List<CommittedRecord> = gson.fromJson(json, type)

                    // Normalize remote records and mark as synced (isLocalOnly = false)
                    val normalizedRemote = remoteList.map {
                        it.copy(
                            baseUrl = normalizeUrl(it.safeBaseUrl),
                            user = it.safeUser.trim(),
                            mac = it.safeMac.trim().uppercase(),
                            isLocalOnly = false
                        )
                    }

                    // Merge: Keep all remote records, and add local records only if they don't match any remote record
                    val merged = normalizedRemote.toMutableList()
                    for (localRec in records) {
                        val localBase = normalizeUrl(localRec.safeBaseUrl)
                        val localUser = localRec.safeUser.trim()
                        val localMac = localRec.safeMac.trim().uppercase()

                        val existsInRemote = normalizedRemote.any { rem ->
                            normalizeUrl(rem.safeBaseUrl).equals(localBase, ignoreCase = true) &&
                            ((localRec.safeType == "Xtream" && rem.safeUser.trim() == localUser) ||
                             (localRec.safeType == "Stalker" && rem.safeMac.trim().equals(localMac, ignoreCase = true)))
                        }
                        if (!existsInRemote) {
                            merged.add(localRec.copy(isLocalOnly = true))
                        }
                    }

                    records.clear()
                    records.addAll(merged)
                    sortByDateAddedDescending()
                    save()
                    return@withContext records.toList()
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun pushToCloud(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {
        try {
            val authToken = token.trim()
            if (authToken.isEmpty()) {
                return@withContext false
            }
            // Guard: Cannot push empty
            if (records.isEmpty()) {
                return@withContext false
            }

            // 1. Get current SHA and fetch remote content to merge before pushing (Never Overwrite)
            val getUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val getConnection = getUrl.openConnection() as java.net.HttpURLConnection
            getConnection.requestMethod = "GET"
            getConnection.useCaches = false
            getConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            getConnection.setRequestProperty("User-Agent", "SherlockStreams/1.0")
            getConnection.setRequestProperty("Authorization", "Bearer $authToken")
            getConnection.connectTimeout = 6000
            getConnection.readTimeout = 6000

            var sha = ""
            val remoteRecords = mutableListOf<CommittedRecord>()
            if (getConnection.responseCode == 200) {
                val jsonResponse = getConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(jsonResponse)
                sha = jsonObj.optString("sha", "")
                val contentB64 = jsonObj.optString("content", "").replace("\n", "")
                if (contentB64.isNotEmpty()) {
                    try {
                        val decodedBytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
                        val remoteJson = String(decodedBytes, Charsets.UTF_8)
                        val type = object : TypeToken<List<CommittedRecord>>() {}.type
                        val list: List<CommittedRecord> = gson.fromJson(remoteJson, type)
                        remoteRecords.addAll(list.map {
                            it.copy(
                                baseUrl = normalizeUrl(it.safeBaseUrl),
                                user = it.safeUser.trim(),
                                mac = it.safeMac.trim().uppercase()
                            )
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                return@withContext false
            }

            // 2. Safe Union Merge: Merge all remote records with current local records so no existing cloud accounts are lost
            val mergedList = remoteRecords.toMutableList()
            for (localRec in records) {
                val localBase = normalizeUrl(localRec.safeBaseUrl)
                val localUser = localRec.safeUser.trim()
                val localMac = localRec.safeMac.trim().uppercase()

                val matchIdx = mergedList.indexOfFirst { rem ->
                    normalizeUrl(rem.safeBaseUrl).equals(localBase, ignoreCase = true) &&
                    ((localRec.safeType == "Xtream" && rem.safeUser.trim() == localUser) ||
                     (localRec.safeType == "Stalker" && rem.safeMac.trim().equals(localMac, ignoreCase = true)))
                }

                if (matchIdx != -1) {
                    // Update metadata of matching remote record with local changes (preserving original dateAdded and notes if not overwritten)
                    val existingRem = mergedList[matchIdx]
                    mergedList[matchIdx] = localRec.copy(
                        dateAdded = if (existingRem.safeDateAdded.isNotEmpty()) existingRem.safeDateAdded else localRec.safeDateAdded,
                        notes = if (localRec.safeNotes.isNotEmpty()) localRec.safeNotes else existingRem.safeNotes,
                        isLocalOnly = null
                    )
                } else {
                    mergedList.add(0, localRec.copy(isLocalOnly = null))
                }
            }

            val cleanForCloud = mergedList.map { it.copy(isLocalOnly = null) }
            val jsonContent = gson.toJson(cleanForCloud)
            val encodedContent = android.util.Base64.encodeToString(jsonContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

            // 3. Push updated content
            val putUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val putConnection = putUrl.openConnection() as java.net.HttpURLConnection
            putConnection.requestMethod = "PUT"
            putConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            putConnection.setRequestProperty("User-Agent", "SherlockStreams/1.0")
            putConnection.setRequestProperty("Authorization", "Bearer $authToken")
            putConnection.setRequestProperty("Content-Type", "application/json")
            putConnection.doOutput = true

            val payload = org.json.JSONObject().apply {
                put("message", "Sync from Android App (${cleanForCloud.size} records)")
                put("content", encodedContent)
                put("sha", sha)
            }

            putConnection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val code = putConnection.responseCode
            if (code == 200 || code == 201) {
                // Also push any local source snapshot files to GitHub sources/
                if (::appContext.isInitialized) {
                    for (rec in cleanForCloud) {
                        val archiveFile = rec.safeSourceArchiveFile
                        if (archiveFile.isNotEmpty()) {
                            val localContent = SourceArchiveManager.getArchiveLocally(appContext, archiveFile)
                            if (!localContent.isNullOrBlank()) {
                                try {
                                    SourceArchiveManager.pushArchiveToGithubSync(archiveFile, localContent, token)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                // Update local records state to match the merged and synced result
                val syncedList = cleanForCloud.map { it.copy(isLocalOnly = false) }
                records.clear()
                records.addAll(syncedList)
                sortByDateAddedDescending()
                save()
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun recheckAllStatus(): Int = withContext(Dispatchers.IO) {
        var updatedCount = 0
        val semaphore = Semaphore(8)

        coroutineScope {
            val deferreds = records.mapIndexed { index, record ->
                async {
                    semaphore.withPermit {
                        try {
                            if (record.safeType == "Xtream") {
                                val res = IPTVClient.verifyXtream(record.safeBaseUrl, record.safeUser, record.safePass)
                                when (res) {
                                    is VerificationResult.Success -> {
                                        val updated = record.copy(
                                            status = if (res.status == "Active") "🟢 Active" else "🟡 ${res.status}",
                                            expires = if (res.expires != "N/A") res.expires else record.safeExpires,
                                            daysLeft = if (res.daysLeft != "N/A") res.daysLeft else record.safeDaysLeft,
                                            activeConn = if (res.activeConn != "N/A") res.activeConn else record.safeActiveConn,
                                            maxConn = if (res.maxConn != "N/A") res.maxConn else record.safeMaxConn,
                                            serverTimezone = if (res.serverTimezone != "N/A") res.serverTimezone else record.safeTimezone
                                        )
                                        withContext(Dispatchers.Main) {
                                            records[index] = updated
                                        }
                                        updatedCount++
                                    }
                                    is VerificationResult.Failed -> {
                                        val updated = record.copy(
                                            status = "🔴 ${res.reason.take(20)}"
                                        )
                                        withContext(Dispatchers.Main) {
                                            records[index] = updated
                                        }
                                        updatedCount++
                                    }
                                    else -> {}
                                }
                            } else if (record.safeType == "Stalker") {
                                val res = IPTVClient.verifyStalker(record.safeBaseUrl, record.safeMac)
                                when (res) {
                                    is VerificationResult.Success -> {
                                        val updated = record.copy(
                                            status = "🟢 Active"
                                        )
                                        withContext(Dispatchers.Main) {
                                            records[index] = updated
                                        }
                                        updatedCount++
                                    }
                                    is VerificationResult.Failed -> {
                                        val updated = record.copy(
                                            status = "🔴 ${res.reason.take(20)}"
                                        )
                                        withContext(Dispatchers.Main) {
                                            records[index] = updated
                                        }
                                        updatedCount++
                                    }
                                    else -> {}
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        Unit
                    }
                }
            }
            deferreds.awaitAll()
        }

        save()
        return@withContext updatedCount
    }

    fun updateNotes(record: CommittedRecord, newNotes: String) {
        val index = records.indexOf(record)
        if (index != -1) {
            records[index] = record.copy(notes = newNotes, isLocalOnly = true)
            save()
            ToastManager.success("Notes saved locally")
            
            val token = DataStore.githubToken
            if (token.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val success = pushToCloud(token)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            ToastManager.success("Notes saved & synced to Git!")
                        } else {
                            ToastManager.warning("Saved locally, but cloud push failed")
                        }
                    }
                }
            }
        }
    }
}
