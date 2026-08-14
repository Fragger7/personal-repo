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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val gson = Gson()

    private const val PREFS_NAME = "iptv_prefs"
    private const val KEY_GITHUB_TOKEN = "github_token"

    fun init(context: Context) {
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

    private fun save() {
        try {
            val json = gson.toJson(records.toList())
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
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
        notes: String = ""
    ) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val cleanBaseUrl = normalizeUrl(baseUrl)
        val cleanUser = user.trim()
        val cleanMac = mac.trim().uppercase()
        val m3u = if (type.contains("Xtream", ignoreCase = true) && cleanUser.isNotEmpty()) {
            "$cleanBaseUrl/get.php?username=$cleanUser&password=$pass&type=m3u_plus&output=ts"
        } else ""

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
                notes = if (notes.isNotEmpty()) notes else existing.safeNotes
            )
            ToastManager.success("Updated existing record in Saved Accounts")
        } else {
            records.add(0, newRecord)
            ToastManager.success("Saved $type connection to Saved Accounts")
        }
        sortByDateAddedDescending()
        save()
    }

    fun delete(record: CommittedRecord) {
        records.remove(record)
        save()
        ToastManager.info("Account removed from Saved Records")
    }

    fun syncFromCloud(): List<CommittedRecord>? {
        try {
            val url = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (DataStore.githubToken.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "token ${DataStore.githubToken}")
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
                    return records.toList()
                }
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun pushToCloud(token: String): Boolean {
        try {
            // Guard: Cannot push empty
            if (records.isEmpty()) {
                return false
            }

            // 1. Get current SHA
            val getUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val getConnection = getUrl.openConnection() as java.net.HttpURLConnection
            getConnection.requestMethod = "GET"
            getConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            getConnection.setRequestProperty("Authorization", "token $token")
            getConnection.connectTimeout = 6000
            getConnection.readTimeout = 6000

            var sha = ""
            if (getConnection.responseCode == 200) {
                val jsonResponse = getConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(jsonResponse)
                sha = jsonObj.optString("sha", "")
            } else {
                return false
            }

            // 2. Format records cleanly for push (stripping isLocalOnly temporary flag for clean cloud JSON)
            val cleanForCloud = records.map {
                it.copy(isLocalOnly = null)
            }

            val jsonContent = gson.toJson(cleanForCloud)
            val encodedContent = android.util.Base64.encodeToString(jsonContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

            // 3. Push updated content
            val putUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val putConnection = putUrl.openConnection() as java.net.HttpURLConnection
            putConnection.requestMethod = "PUT"
            putConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            putConnection.setRequestProperty("Authorization", "token $token")
            putConnection.setRequestProperty("Content-Type", "application/json")
            putConnection.doOutput = true

            val payload = org.json.JSONObject().apply {
                put("message", "Sync from Android App (${records.size} records)")
                put("content", encodedContent)
                put("sha", sha)
            }

            putConnection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val code = putConnection.responseCode
            if (code == 200 || code == 201) {
                // Mark all records as synced
                val syncedList = records.map { it.copy(isLocalOnly = false) }
                records.clear()
                records.addAll(syncedList)
                sortByDateAddedDescending()
                save()
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
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
            records[index] = record.copy(notes = newNotes)
            save()
            ToastManager.success("Notes saved successfully!")
        }
    }
}
