package com.projectstrong.iptv.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import java.io.File

data class CommittedRecord(
    @SerializedName("type") val type: String? = "Unknown",
    @SerializedName("base_url") val baseUrl: String? = "",
    @SerializedName("username") val user: String? = "",
    @SerializedName("password") val pass: String? = "",
    @SerializedName("mac") val mac: String? = "",
    @SerializedName("Notes") val notes: String? = "",
    @SerializedName("Date Selected") val dateAdded: String? = null
)
 {
    val safeType get() = type ?: "Unknown"
    val safeBaseUrl get() = baseUrl ?: ""
    val safeUser get() = user ?: ""
    val safePass get() = pass ?: ""
    val safeMac get() = mac ?: ""
    val safeNotes get() = notes ?: ""
    val safeDateAdded get() = dateAdded ?: ""
}


object CommittedManager {
    val records = mutableStateListOf<CommittedRecord>()
    private lateinit var file: File
    private val gson = Gson()

    fun init(context: Context) {
        file = File(context.filesDir, "committed.json")
        load()
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
                records.addAll(list)
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

    fun commit(record: CommittedRecord) {
        // Avoid exact duplicates
        val exists = records.any { it.baseUrl == record.baseUrl && it.user == record.user && it.mac == record.mac }
        if (!exists) {
            records.add(record)
            save()
        }
    }

    fun delete(record: CommittedRecord) {
        records.remove(record)
        save()
    }
    
                fun syncFromCloud(): List<CommittedRecord>? {
        try {
            val url = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(jsonResponse)
                val contentB64 = jsonObj.optString("content", "").replace("\n", "")
                
                if (contentB64.isNotEmpty()) {
                    val decodedBytes = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
                    val json = String(decodedBytes, Charsets.UTF_8)
                    
                    val type = object : TypeToken<List<CommittedRecord>>() {}.type
                    val remoteList: List<CommittedRecord> = gson.fromJson(json, type)
                    
                    // Merge local into remote
                    val merged = remoteList.toMutableList()
                    for (localRec in records) {
                        val exists = remoteList.any { 
                            it.baseUrl == localRec.baseUrl && 
                            it.user == localRec.user && 
                            it.mac == localRec.mac 
                        }
                        if (!exists) {
                            merged.add(localRec)
                        }
                    }
                    
                    records.clear()
                    records.addAll(merged)
                    save()
                    
                    return merged
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun pushToCloud(token: String): Boolean {
        try {
            // 1. Get current SHA
            val getUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val getConnection = getUrl.openConnection() as java.net.HttpURLConnection
            getConnection.requestMethod = "GET"
            getConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            getConnection.setRequestProperty("Authorization", "token $token")
            getConnection.connectTimeout = 5000
            getConnection.readTimeout = 5000
            
            var sha = ""
            if (getConnection.responseCode == 200) {
                val jsonResponse = getConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(jsonResponse)
                sha = jsonObj.optString("sha", "")
            } else {
                return false
            }
            
            // 2. Push updated content
            val putUrl = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/committed.json")
            val putConnection = putUrl.openConnection() as java.net.HttpURLConnection
            putConnection.requestMethod = "PUT"
            putConnection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            putConnection.setRequestProperty("Authorization", "token $token")
            putConnection.setRequestProperty("Content-Type", "application/json")
            putConnection.doOutput = true
            
            val jsonContent = gson.toJson(records.toList())
            val encodedContent = android.util.Base64.encodeToString(jsonContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            
            val payload = org.json.JSONObject().apply {
                put("message", "Sync from Android App")
                put("content", encodedContent)
                put("sha", sha)
            }
            
            putConnection.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val code = putConnection.responseCode
            return code == 200 || code == 201
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun updateNotes(record: CommittedRecord, newNotes: String) {
        val index = records.indexOf(record)
        if (index != -1) {
            records[index] = record.copy(notes = newNotes)
            save()
        }
    }
}
