package com.projectstrong.iptv.data

import android.content.Context
import android.util.Base64
import com.projectstrong.iptv.ui.components.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

object SourceArchiveManager {
    private const val GITHUB_REPO = "Fragger7/personal-repo"
    private const val SOURCES_SUBDIR = "project-strong/sources"

    fun generateArchiveFileName(sourceLink: String, rawContent: String = ""): String {
        val link = sourceLink.trim()
        if (link.isEmpty() || link.equals("Direct Ingestion", ignoreCase = true)) {
            val hash = sha1Short(if (rawContent.isNotEmpty()) rawContent else System.currentTimeMillis().toString())
            return "raw_snapshot_$hash.txt"
        }

        try {
            val uri = URI(link)
            val host = (uri.host ?: "source").replace(".", "_").replace("www_", "").lowercase()
            val path = uri.path ?: ""
            val cleanPath = path.trim('/').replace('/', '_').replace(Regex("[^a-zA-Z0-9_-]"), "")
            
            val id = if (cleanPath.isNotEmpty()) cleanPath else sha1Short(link)
            return "${host}_$id.txt"
        } catch (e: Exception) {
            val hash = sha1Short(link)
            return "source_$hash.txt"
        }
    }

    private fun sha1Short(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }.take(10)
        } catch (e: Exception) {
            input.hashCode().toString().replace("-", "x").take(10)
        }
    }

    fun getLocalSourcesDir(context: Context): File {
        val dir = File(context.filesDir, "sources")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveArchiveLocally(context: Context, fileName: String, content: String): Boolean {
        if (fileName.isBlank() || content.isBlank()) return false
        return try {
            val dir = getLocalSourcesDir(context)
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getArchiveLocally(context: Context, fileName: String): String? {
        if (fileName.isBlank()) return null
        return try {
            val dir = getLocalSourcesDir(context)
            val file = File(dir, fileName)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getOrFetchArchive(
        context: Context,
        archiveFileName: String,
        sourceLink: String = "",
        token: String = DataStore.githubToken
    ): String? = withContext(Dispatchers.IO) {
        // 1. Check in-memory snapshot store first
        if (sourceLink.isNotBlank() && DataStore.sourceSnapshots.containsKey(sourceLink)) {
            val snap = DataStore.sourceSnapshots[sourceLink]
            if (!snap.isNullOrBlank()) {
                saveArchiveLocally(context, archiveFileName, snap)
                return@withContext snap
            }
        }

        // 2. Check local disk
        val local = getArchiveLocally(context, archiveFileName)
        if (!local.isNullOrBlank()) {
            return@withContext local
        }

        // 3. Try fetching from GitHub repository
        val remote = fetchArchiveFromGithub(archiveFileName, token)
        if (!remote.isNullOrBlank()) {
            saveArchiveLocally(context, archiveFileName, remote)
            if (sourceLink.isNotBlank()) {
                DataStore.sourceSnapshots[sourceLink] = remote
            }
            return@withContext remote
        }

        // 4. Fallback: try fetching original link directly if available and valid URL
        if (sourceLink.startsWith("http://", ignoreCase = true) || sourceLink.startsWith("https://", ignoreCase = true)) {
            try {
                val fetched = com.projectstrong.iptv.network.IPTVClient.fetchRemoteText(sourceLink)
                if (!fetched.isNullOrBlank()) {
                    saveArchiveLocally(context, archiveFileName, fetched)
                    DataStore.sourceSnapshots[sourceLink] = fetched
                    return@withContext fetched
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext null
    }

    suspend fun fetchArchiveFromGithub(fileName: String, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanName = fileName.replace("sources/", "").trim()
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/contents/$SOURCES_SUBDIR/$cleanName")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "token $token")
            }
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val contentB64 = obj.optString("content", "").replace("\n", "")
                if (contentB64.isNotEmpty()) {
                    val bytes = Base64.decode(contentB64, Base64.DEFAULT)
                    return@withContext String(bytes, Charsets.UTF_8)
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun pushArchiveToGithub(fileName: String, content: String, token: String): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank() || fileName.isBlank() || content.isBlank()) return@withContext false
        try {
            val cleanName = fileName.replace("sources/", "").trim()
            val getUrl = URL("https://api.github.com/repos/$GITHUB_REPO/contents/$SOURCES_SUBDIR/$cleanName")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.requestMethod = "GET"
            getConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            getConn.setRequestProperty("Authorization", "token $token")
            getConn.connectTimeout = 6000
            getConn.readTimeout = 6000

            var sha: String? = null
            if (getConn.responseCode == 200) {
                val resp = getConn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(resp)
                sha = obj.optString("sha", null)
            }

            val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val putConn = getUrl.openConnection() as HttpURLConnection
            putConn.requestMethod = "PUT"
            putConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            putConn.setRequestProperty("Authorization", "token $token")
            putConn.setRequestProperty("Content-Type", "application/json")
            putConn.doOutput = true

            val payload = JSONObject().apply {
                put("message", "Archive snapshot $cleanName (Forever Source Archive)")
                put("content", encoded)
                if (sha != null) put("sha", sha)
            }

            putConn.outputStream.use { os ->
                val input = payload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val code = putConn.responseCode
            return@withContext (code == 200 || code == 201)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
