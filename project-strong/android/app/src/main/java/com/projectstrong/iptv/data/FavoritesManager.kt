package com.projectstrong.iptv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.projectstrong.iptv.network.IPTVClient
import com.projectstrong.iptv.network.VerificationResult
import com.projectstrong.iptv.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@androidx.annotation.Keep
data class FavoriteItem(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("stream_id") val streamId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("original_name") val originalName: String = "",
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("icon_url") val iconUrl: String = "",
    @SerializedName("direct_url") val directUrl: String = "",
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("username") val user: String = "",
    @SerializedName("password") val pass: String = "",
    @SerializedName("parent_playlist") val parentPlaylist: String = "",
    @SerializedName("date_added") val dateAdded: String = "",
    @SerializedName("status") val status: String = "🟢 Active",
    @SerializedName("is_synced") val isSynced: Boolean = false
) {
    val displayName: String get() = if (name.isNotBlank()) name else (if (originalName.isNotBlank()) originalName else "Stream #$streamId")
}

@androidx.annotation.Keep
private data class FavoritesPayloadWrapper(
    @SerializedName("magic") val magic: String = "SHERLOCK_FAVORITES_PAYLOAD",
    @SerializedName("items") val items: List<FavoriteItem> = emptyList()
)

@androidx.annotation.Keep
private data class EncryptedCloudFavorites(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("signature") val signature: String = "SHERLOCK_FAVORITES_V1",
    @SerializedName("salt") val salt: String,
    @SerializedName("iv") val iv: String,
    @SerializedName("ciphertext") val ciphertext: String,
    @SerializedName("updated_at") val updatedAt: String
)

object FavoritesManager {
    private const val FILE_NAME = "favorites_channels.json"
    private const val GITHUB_REPO = "Fragger7/personal-repo"
    private const val GITHUB_PATH = "project-strong/favorites.json"
    private const val INTERNAL_KEY = "SHERLOCK_FAVORITES_STORAGE_SECRET_KEY_V1"

    private lateinit var localFile: File
    private val gson = Gson()
    private val mutex = Mutex()
    private val httpClient = OkHttpClient.Builder().build()

    val items = mutableStateListOf<FavoriteItem>()

    var isSyncing by mutableStateOf(false)
        private set

    fun init(context: Context) {
        localFile = File(context.filesDir, FILE_NAME)
        loadLocal()
    }

    fun addChannel(
        streamId: String,
        name: String,
        originalName: String,
        categoryId: String,
        categoryName: String,
        iconUrl: String,
        directUrl: String,
        baseUrl: String,
        user: String,
        pass: String,
        parentPlaylist: String
    ) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val existingIndex = items.indexOfFirst {
            it.streamId == streamId && it.baseUrl.equals(baseUrl, ignoreCase = true)
        }

        val newItem = FavoriteItem(
            streamId = streamId,
            name = name.ifBlank { originalName },
            originalName = originalName,
            categoryId = categoryId,
            categoryName = categoryName,
            iconUrl = iconUrl,
            directUrl = directUrl,
            baseUrl = baseUrl,
            user = user,
            pass = pass,
            parentPlaylist = parentPlaylist,
            dateAdded = nowStr,
            status = "🟢 Active",
            isSynced = false
        )

        if (existingIndex >= 0) {
            val old = items[existingIndex]
            items[existingIndex] = newItem.copy(id = old.id, dateAdded = old.dateAdded)
            ToastManager.success("Updated '${newItem.displayName}' in Favorites")
        } else {
            items.add(0, newItem)
            ToastManager.success("Saved '${newItem.displayName}' to Favorites")
        }

        saveLocal()
        syncToCloudAsync()
    }

    fun renameChannel(item: FavoriteItem, newName: String) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            val updated = items[index].copy(name = newName.trim(), isSynced = false)
            items[index] = updated
            saveLocal()
            syncToCloudAsync()
            ToastManager.success("Renamed to '${updated.displayName}'")
        }
    }

    fun deleteChannel(item: FavoriteItem) {
        val removed = items.removeAll { it.id == item.id }
        if (removed) {
            saveLocal()
            syncToCloudAsync()
            ToastManager.info("Removed '${item.displayName}' from Favorites")
        }
    }

    fun bulkDeleteChannels(ids: List<String>) {
        val idSet = ids.toSet()
        if (idSet.isEmpty()) return
        items.removeAll { idSet.contains(it.id) }
        saveLocal()
        syncToCloudAsync()
        ToastManager.info("Removed ${idSet.size} channels from Favorites")
    }

    fun isChannelSaved(streamId: String, baseUrl: String): Boolean {
        return items.any { it.streamId == streamId && it.baseUrl.equals(baseUrl, ignoreCase = true) }
    }

    private fun loadLocal() {
        if (!::localFile.isInitialized || !localFile.exists()) return
        try {
            val content = localFile.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<FavoriteItem>>() {}.type
            val loaded: List<FavoriteItem> = gson.fromJson(content, type) ?: emptyList()
            items.clear()
            items.addAll(loaded)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLocal() {
        if (!::localFile.isInitialized) return
        try {
            val json = gson.toJson(items.toList())
            localFile.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncToCloudAsync() {
        val token = DataStore.githubToken.trim()
        if (token.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            commitToCloud(token)
        }
    }

    suspend fun checkCloudFavoritesExists(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {
        val authToken = token.trim()
        if (authToken.isEmpty()) return@withContext false
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_PATH"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer $authToken")
                .header("User-Agent", "SherlockStreams/1.0")
                .build()

            val resp = httpClient.newCall(req).execute()
            val exists = resp.code == 200
            resp.close()
            exists
        } catch (e: Exception) {
            false
        }
    }

    suspend fun pullFromCloud(token: String = DataStore.githubToken): Result<Int> = syncFromCloud(token)

    /**
     * Pulls latest favorites from the cloud and merges with local channels.
     * Preserves unpushed local changes.
     */
    suspend fun syncFromCloud(token: String = DataStore.githubToken): Result<Int> = withContext(Dispatchers.IO) {
        val authToken = token.trim()
        if (authToken.isEmpty()) return@withContext Result.failure(Exception("GitHub Token is required to sync from Cloud."))

        isSyncing = true
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_PATH"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer $authToken")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "SherlockStreams/1.0")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.code != 200) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                resp.close()
                return@withContext Result.failure(Exception("Cloud favorites not found ($err)"))
            }

            val body = resp.body?.string() ?: ""
            resp.close()

            val jsonObj = org.json.JSONObject(body)
            val contentB64 = jsonObj.optString("content", "").filter { !it.isWhitespace() }
            val decodedJson = String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)

            val cloudFavorites = gson.fromJson(decodedJson, EncryptedCloudFavorites::class.java)
                ?: return@withContext Result.failure(Exception("Invalid Cloud Favorites structure"))

            val decryptedJson = decryptPayload(
                ciphertextB64 = cloudFavorites.ciphertext,
                saltB64 = cloudFavorites.salt,
                ivB64 = cloudFavorites.iv,
                password = INTERNAL_KEY
            ) ?: return@withContext Result.failure(Exception("Decryption of favorites payload failed."))

            val wrapper = gson.fromJson(decryptedJson, FavoritesPayloadWrapper::class.java)
            if (wrapper == null || wrapper.magic != "SHERLOCK_FAVORITES_PAYLOAD") {
                return@withContext Result.failure(Exception("Decrypted data corrupted or invalid signature."))
            }

            withContext(Dispatchers.Main) {
                val remoteItems = wrapper.items.map { it.copy(isSynced = true) }
                val mergedList = mutableListOf<FavoriteItem>()
                val handledKeys = mutableSetOf<String>()

                for (local in items) {
                    val key = "${local.streamId}_${local.baseUrl.trim().trimEnd('/')}"
                    handledKeys.add(key)
                    val matchingRemote = remoteItems.find { "${it.streamId}_${it.baseUrl.trim().trimEnd('/')}" == key }
                    if (!local.isSynced || matchingRemote == null) {
                        mergedList.add(local)
                    } else {
                        mergedList.add(matchingRemote.copy(status = local.status))
                    }
                }

                for (remote in remoteItems) {
                    val key = "${remote.streamId}_${remote.baseUrl.trim().trimEnd('/')}"
                    if (!handledKeys.contains(key)) {
                        mergedList.add(remote)
                    }
                }

                items.clear()
                items.addAll(mergedList)
                saveLocal()
            }

            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            isSyncing = false
        }
    }

    /**
     * Verifies the live connection status of specified channels in Favorites.
     * Deduplicates requests by server credentials (baseUrl, user, pass).
     */
    suspend fun recheckStatusForItems(targetItems: List<FavoriteItem>): Int = withContext(Dispatchers.IO) {
        if (targetItems.isEmpty()) return@withContext 0

        val credMap = targetItems.groupBy { "${it.baseUrl.trim()}|${it.user.trim()}|${it.pass.trim()}" }
        val semaphore = Semaphore(6)
        val statusResults = java.util.concurrent.ConcurrentHashMap<String, String>()

        coroutineScope {
            val deferreds = credMap.keys.map { credKey ->
                async {
                    semaphore.withPermit {
                        val parts = credKey.split("|")
                        if (parts.size >= 3) {
                            val baseUrl = parts[0]
                            val user = parts[1]
                            val pass = parts[2]
                            try {
                                val res = IPTVClient.verifyXtream(baseUrl, user, pass)
                                val statusStr = when (res) {
                                    is VerificationResult.Success -> {
                                        if (res.status.equals("Active", ignoreCase = true)) "🟢 Active" else "🟡 ${res.status}"
                                    }
                                    is VerificationResult.Failed -> {
                                        when {
                                            res.reason.contains("Expired", ignoreCase = true) || res.reason.contains("Inactive", ignoreCase = true) -> "🟡 Expired"
                                            res.reason.contains("403", ignoreCase = true) || res.reason.contains("Cloud Blocked", ignoreCase = true) -> "🛡️ Blocked"
                                            res.reason.contains("521", ignoreCase = true) || res.reason.contains("Dead", ignoreCase = true) || res.reason.contains("Offline", ignoreCase = true) -> "🔴 Offline"
                                            else -> "🔴 ${res.reason.take(18)}"
                                        }
                                    }
                                }
                                statusResults[credKey] = statusStr
                            } catch (e: Exception) {
                                statusResults[credKey] = "🔴 Error"
                            }
                        }
                    }
                }
            }
            deferreds.awaitAll()
        }

        withContext(Dispatchers.Main) {
            val targetIds = targetItems.map { it.id }.toSet()
            for (i in 0 until items.size) {
                val it = items[i]
                if (targetIds.contains(it.id)) {
                    val key = "${it.baseUrl.trim()}|${it.user.trim()}|${it.pass.trim()}"
                    val newStatus = statusResults[key]
                    if (newStatus != null && newStatus != it.status) {
                        items[i] = it.copy(status = newStatus)
                    }
                }
            }
            saveLocal()
        }

        targetItems.size
    }

    /**
     * Verifies the live connection status of all saved channels in Favorites.
     */
    suspend fun recheckAllStatus(): Int = withContext(Dispatchers.IO) {
        recheckStatusForItems(items.toList())
    }

    /**
     * Commits all favorites to GitHub as an encrypted payload using the internal encryption key.
     */
    suspend fun commitToCloud(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {
        val authToken = token.trim()
        if (authToken.isEmpty()) return@withContext false

        mutex.withLock {
            isSyncing = true
            try {
                val wrapper = FavoritesPayloadWrapper(items = items.toList())
                val plaintext = gson.toJson(wrapper)

                val enc = encryptPayload(plaintext, INTERNAL_KEY)
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

                val cloudFavorites = EncryptedCloudFavorites(
                    salt = enc.saltB64,
                    iv = enc.ivB64,
                    ciphertext = enc.ciphertextB64,
                    updatedAt = nowStr
                )

                val cloudJson = gson.toJson(cloudFavorites)
                val base64Content = android.util.Base64.encodeToString(cloudJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

                val getUrl = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_PATH"
                val getReq = Request.Builder()
                    .url(getUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", "SherlockStreams/1.0")
                    .build()

                val getResp = httpClient.newCall(getReq).execute()
                var currentSha: String? = null
                if (getResp.isSuccessful) {
                    val getBody = getResp.body?.string() ?: ""
                    val json = org.json.JSONObject(getBody)
                    if (json.has("sha")) {
                        currentSha = json.getString("sha")
                    }
                }
                getResp.close()

                val commitObj = org.json.JSONObject().apply {
                    put("message", "favorites: sync ${items.size} bookmarked channels")
                    put("content", base64Content)
                    if (!currentSha.isNullOrEmpty()) {
                        put("sha", currentSha)
                    }
                }

                val putReq = Request.Builder()
                    .url(getUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", "SherlockStreams/1.0")
                    .put(commitObj.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val putResp = httpClient.newCall(putReq).execute()
                val success = putResp.isSuccessful
                putResp.close()

                if (success) {
                    withContext(Dispatchers.Main) {
                        for (i in 0 until items.size) {
                            items[i] = items[i].copy(isSynced = true)
                        }
                        saveLocal()
                    }
                }
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                isSyncing = false
            }
        }
    }

    // --- Cryptographic Primitives ---

    private data class EncryptedResult(val saltB64: String, val ivB64: String, val ciphertextB64: String)

    private fun encryptPayload(plaintext: String, password: String): EncryptedResult {
        val salt = generateRandomBytes(16)
        val iv = generateRandomBytes(12)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptedResult(
            saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            ivB64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
            ciphertextB64 = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
        )
    }

    private fun decryptPayload(ciphertextB64: String, saltB64: String, ivB64: String, password: String): String? {
        return try {
            val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)
            val ciphertext = android.util.Base64.decode(ciphertextB64, android.util.Base64.NO_WRAP)

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
