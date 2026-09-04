package com.projectstrong.iptv.network

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.projectstrong.iptv.BuildConfig
import com.projectstrong.iptv.data.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val version: String, val releaseNotes: String, val downloadUrl: String) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdater {
    private val client = OkHttpClient.Builder().build()
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var updateFile: File? = null

    suspend fun checkForUpdates(context: Context) {
        if (_updateState.value is UpdateState.Checking) return
        
        _updateState.value = UpdateState.Checking
        try {
            withContext(Dispatchers.IO) {
                val currentVersion = BuildConfig.VERSION_NAME

                val request = Request.Builder()
                    .url("https://api.github.com/repos/Fragger7/personal-repo/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .apply {
                        val token = DataStore.githubToken
                        if (token.isNotEmpty()) {
                            header("Authorization", "Bearer $token")
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Idle
                        return@withContext
                    }

                    val responseBody = response.body?.string() ?: return@withContext
                    val json = JSONObject(responseBody)
                    val tagName = json.optString("tag_name", "").removePrefix("v")
                    val body = json.optString("body", "No release notes provided.")
                    
                    if (isNewerVersion(currentVersion, tagName)) {
                        val assets = json.optJSONArray("assets")
                        var apkUrl: String? = null
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.optString("url")
                                    break
                                }
                            }
                        }

                        if (apkUrl != null) {
                            _updateState.value = UpdateState.Available(tagName, body, apkUrl)
                        } else {
                            _updateState.value = UpdateState.Idle
                        }
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Idle
        }
    }

    suspend fun downloadAndInstallUpdate(context: Context, downloadUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .apply {
                        val token = DataStore.githubToken
                        if (token.isNotEmpty()) {
                            header("Authorization", "Bearer $token")
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Failed to download update (HTTP ${response.code})")
                        return@withContext
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val totalBytes = body.contentLength()
                    val inputStream = body.byteStream()

                    val updateDir = File(context.cacheDir, "updates")
                    if (!updateDir.exists()) updateDir.mkdirs()
                    
                    val file = File(updateDir, "update.apk")
                    if (file.exists()) file.delete()

                    val outputStream = FileOutputStream(file)
                    var bytesCopied: Long = 0
                    val buffer = ByteArray(8 * 1024)
                    var bytes = inputStream.read(buffer)
                    
                    var lastReportedProgress = 0f
                    
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        if (totalBytes > 0) {
                            val progress = bytesCopied.toFloat() / totalBytes.toFloat()
                            // Only emit if changed significantly to avoid flooding UI
                            if (progress - lastReportedProgress > 0.01f || progress == 1f) {
                                _updateState.value = UpdateState.Downloading(progress)
                                lastReportedProgress = progress
                            }
                        }
                        bytes = inputStream.read(buffer)
                    }

                    outputStream.close()
                    inputStream.close()
                    
                    updateFile = file
                    _updateState.value = UpdateState.ReadyToInstall
                    installUpdate(context, file)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed")
        }
    }

    private fun installUpdate(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            
            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error("Failed to launch installer: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) return true
            if (rem < curr) return false
        }
        return false
    }
}
