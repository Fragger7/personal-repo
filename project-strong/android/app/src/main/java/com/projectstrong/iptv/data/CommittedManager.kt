package com.projectstrong.iptv.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class CommittedRecord(
    val type: String,
    val baseUrl: String,
    val user: String,
    val pass: String,
    val mac: String,
    var notes: String = "",
    val dateAdded: Long = System.currentTimeMillis()
)

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
            val url = java.net.URL("https://raw.githubusercontent.com/Fragger7/personal-repo/main/project-strong/committed.json")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                if (json.isNotBlank()) {
                    val type = object : TypeToken<List<CommittedRecord>>() {}.type
                    val list: List<CommittedRecord> = gson.fromJson(json, type)
                    
                    // Save it locally as well to mirror Python app behavior
                    records.clear()
                    records.addAll(list)
                    save()
                    
                    return list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun updateNotes(record: CommittedRecord, newNotes: String) {
        val index = records.indexOf(record)
        if (index != -1) {
            records[index] = record.copy(notes = newNotes)
            save()
        }
    }
}
