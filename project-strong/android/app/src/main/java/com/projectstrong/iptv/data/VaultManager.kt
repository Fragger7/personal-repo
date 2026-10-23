package com.projectstrong.iptv.data

import android.content.Context
import android.content.SharedPreferences
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
data class VaultItem(
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
private data class VaultPayloadWrapper(
    @SerializedName("magic") val magic: String = "SHERLOCK_VAULT_PAYLOAD",
    @SerializedName("items") val items: List<VaultItem> = emptyList()
)

@androidx.annotation.Keep
private data class EncryptedCloudVault(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("signature") val signature: String = "SHERLOCK_VAULT_V1",
    @SerializedName("salt") val salt: String,
    @SerializedName("iv") val iv: String,
    @SerializedName("ciphertext") val ciphertext: String,
    @SerializedName("updated_at") val updatedAt: String
)

object VaultManager {
    private const val PREFS_NAME = "sherlock_vault_prefs"
    private const val KEY_PASSWORD_SALT = "vault_pwd_salt"
    private const val KEY_PASSWORD_HASH = "vault_pwd_hash"
    private const val KEY_HAS_PASSWORD = "has_vault_password"
    private const val FILE_NAME = "vault_channels.json"
    private const val GITHUB_REPO = "Fragger7/personal-repo"
    private const val GITHUB_PATH = "project-strong/vault.json"

    private lateinit var prefs: SharedPreferences
    private lateinit var localFile: File
    private val gson = Gson()
    private val mutex = Mutex()
    private val httpClient = OkHttpClient.Builder().build()

    val items = mutableStateListOf<VaultItem>()
    var isUnlocked by mutableStateOf(false)
        private set

    var currentSessionPassword by mutableStateOf<String?>(null)
        private set

    var isSyncing by mutableStateOf(false)
        private set

    private var lastActiveTimestamp: Long = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        localFile = File(context.filesDir, FILE_NAME)
    }

    fun hasConfiguredPassword(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_HAS_PASSWORD, false) && prefs.getString(KEY_PASSWORD_HASH, null) != null
    }

    /**
     * Set or change the vault password.
     * If items exist, they are preserved and re-encrypted.
     */
    fun setPassword(newPassword: String) {
        val trimmed = newPassword.trim()
        if (trimmed.isEmpty()) return

        val salt = generateRandomBytes(16)
        val hash = hashPassword(trimmed, salt)

        prefs.edit()
            .putString(KEY_PASSWORD_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString(KEY_PASSWORD_HASH, android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
            .putBoolean(KEY_HAS_PASSWORD, true)
            .apply()

        currentSessionPassword = trimmed
        isUnlocked = true
        updateLastActive()

        saveLocal()
        syncToCloudAsync()
    }

    /**
     * Unlock vault using password.
     */
    fun unlock(password: String): Boolean {
        val trimmed = password.trim()
        if (trimmed.isEmpty()) return false

        // 1. Verify against local hash if present
        if (hasConfiguredPassword()) {
            val saltB64 = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false
            val storedHashB64 = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false

            val salt = android.util.Base64.decode(saltB64, android.util.Base64.NO_WRAP)
            val expectedHash = android.util.Base64.decode(storedHashB64, android.util.Base64.NO_WRAP)
            val actualHash = hashPassword(trimmed, salt)

            if (!actualHash.contentEquals(expectedHash)) {
                return false
            }

            currentSessionPassword = trimmed
            isUnlocked = true
            updateLastActive()
            loadLocal()
            return true
        }

        return false
    }

    fun lock() {
        isUnlocked = false
        currentSessionPassword = null
    }

    fun updateLastActive() {
        lastActiveTimestamp = System.currentTimeMillis()
    }

    fun isSessionExpired(): Boolean {
        if (!isUnlocked) return true
        val timeoutMin = SettingsManager.vaultTimeoutMinutes
        if (timeoutMin <= 0) return true // 0 means Immediate lock upon leaving tab or screen off
        val elapsedMs = System.currentTimeMillis() - lastActiveTimestamp
        return elapsedMs > timeoutMin * 60 * 1000L
    }

    fun checkTimeout(): Boolean {
        if (!isUnlocked) return true
        val timeoutMin = SettingsManager.vaultTimeoutMinutes
        if (timeoutMin <= 0) {
            lock()
            return true
        }

        val elapsedMs = System.currentTimeMillis() - lastActiveTimestamp
        if (elapsedMs > timeoutMin * 60 * 1000L) {
            lock()
            return true
        }
        return false
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

        val newItem = VaultItem(
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
            parentPlaylist = parentPlaylist.ifBlank { baseUrl },
            dateAdded = nowStr,
            isSynced = false
        )

        if (existingIndex != -1) {
            items[existingIndex] = newItem.copy(
                id = items[existingIndex].id,
                dateAdded = items[existingIndex].dateAdded
            )
            ToastManager.success("Updated channel in Vault")
        } else {
            items.add(0, newItem)
            ToastManager.success("Saved channel to Vault")
        }

        saveLocal()
        syncToCloudAsync()
    }

    fun renameChannel(item: VaultItem, newName: String) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            items[idx] = items[idx].copy(name = newName.trim(), isSynced = false)
            saveLocal()
            syncToCloudAsync()
            ToastManager.success("Channel renamed")
        }
    }

    fun deleteChannel(item: VaultItem) {
        items.removeAll { it.id == item.id }
        saveLocal()
        syncToCloudAsync()
        ToastManager.info("Removed channel from Vault")
    }

    fun bulkDeleteChannels(ids: List<String>) {
        val idSet = ids.toSet()
        if (idSet.isEmpty()) return
        items.removeAll { idSet.contains(it.id) }
        saveLocal()
        syncToCloudAsync()
        ToastManager.info("Removed ${idSet.size} channels from Vault")
    }

    fun isChannelSaved(streamId: String, baseUrl: String): Boolean {
        return items.any { it.streamId == streamId && it.baseUrl.equals(baseUrl, ignoreCase = true) }
    }

    private fun loadLocal() {
        if (!::localFile.isInitialized || !localFile.exists()) return
        try {
            val content = localFile.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<VaultItem>>() {}.type
            val loaded: List<VaultItem> = gson.fromJson(content, type) ?: emptyList()
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

    fun resetVault(token: String = DataStore.githubToken, onComplete: ((Boolean) -> Unit)? = null) {
        items.clear()
        if (::localFile.isInitialized && localFile.exists()) {
            localFile.delete()
        }
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
        lock()

        val authToken = token.trim()
        if (authToken.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val success = deleteCloudVault(authToken)
                withContext(Dispatchers.Main) {
                    if (success) {
                        ToastManager.success("Vault wiped locally and from Cloud")
                    } else {
                        ToastManager.info("Vault wiped locally")
                    }
                    onComplete?.invoke(success)
                }
            }
        } else {
            ToastManager.info("Vault reset successfully")
            onComplete?.invoke(true)
        }
    }

    private fun syncToCloudAsync() {
        val password = currentSessionPassword ?: return
        val token = DataStore.githubToken.trim()
        if (token.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            commitToCloud(password, token)
        }
    }

    suspend fun checkCloudVaultExists(token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {
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

    /**
     * Restores the vault from the cloud during a fresh install using the entered password.
     */
    suspend fun restoreFromCloud(password: String, token: String = DataStore.githubToken): Result<Int> = withContext(Dispatchers.IO) {
        val authToken = token.trim()
        if (authToken.isEmpty()) return@withContext Result.failure(Exception("GitHub Token is required to restore from Cloud."))

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
                return@withContext Result.failure(Exception("Cloud vault not found ($err)"))
            }

            val body = resp.body?.string() ?: ""
            resp.close()

            val jsonObj = org.json.JSONObject(body)
            val contentB64 = jsonObj.optString("content", "").filter { !it.isWhitespace() }
            val decodedJson = String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)

            val cloudVault = gson.fromJson(decodedJson, EncryptedCloudVault::class.java)
                ?: return@withContext Result.failure(Exception("Invalid Cloud Vault structure"))

            val decryptedJson = decryptPayload(
                ciphertextB64 = cloudVault.ciphertext,
                saltB64 = cloudVault.salt,
                ivB64 = cloudVault.iv,
                password = password
            ) ?: return@withContext Result.failure(Exception("Incorrect Password. Decryption failed."))

            val wrapper = gson.fromJson(decryptedJson, VaultPayloadWrapper::class.java)
            if (wrapper == null || wrapper.magic != "SHERLOCK_VAULT_PAYLOAD") {
                return@withContext Result.failure(Exception("Decrypted data corrupted or invalid signature."))
            }

            withContext(Dispatchers.Main) {
                // Initialize password locally
                setPassword(password)
                items.clear()
                val restored = wrapper.items.map { it.copy(isSynced = true) }
                items.addAll(restored)
                saveLocal()
            }

            Result.success(wrapper.items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pulls latest vault from the cloud and merges with local channels.
     * Preserves unpushed local changes.
     */
    suspend fun syncFromCloud(password: String = currentSessionPassword ?: "", token: String = DataStore.githubToken): Result<Int> = withContext(Dispatchers.IO) {
        val pwd = password.trim()
        if (pwd.isEmpty()) return@withContext Result.failure(Exception("Password is required to decrypt vault."))
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
                return@withContext Result.failure(Exception("Cloud vault not found ($err)"))
            }

            val body = resp.body?.string() ?: ""
            resp.close()

            val jsonObj = org.json.JSONObject(body)
            val contentB64 = jsonObj.optString("content", "").filter { !it.isWhitespace() }
            val decodedJson = String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)

            val cloudVault = gson.fromJson(decodedJson, EncryptedCloudVault::class.java)
                ?: return@withContext Result.failure(Exception("Invalid Cloud Vault structure"))

            val decryptedJson = decryptPayload(
                ciphertextB64 = cloudVault.ciphertext,
                saltB64 = cloudVault.salt,
                ivB64 = cloudVault.iv,
                password = pwd
            ) ?: return@withContext Result.failure(Exception("Incorrect Password. Decryption failed."))

            val wrapper = gson.fromJson(decryptedJson, VaultPayloadWrapper::class.java)
            if (wrapper == null || wrapper.magic != "SHERLOCK_VAULT_PAYLOAD") {
                return@withContext Result.failure(Exception("Decrypted data corrupted or invalid signature."))
            }

            withContext(Dispatchers.Main) {
                val remoteItems = wrapper.items.map { it.copy(isSynced = true) }
                val mergedList = mutableListOf<VaultItem>()
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
     * Verifies the live connection status of specified channels in the Vault.
     * Deduplicates requests by server credentials (baseUrl, user, pass).
     */
    suspend fun recheckStatusForItems(targetItems: List<VaultItem>): Int = withContext(Dispatchers.IO) {
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
     * Verifies the live connection status of all saved channels in the Vault.
     */
    suspend fun recheckAllStatus(): Int = withContext(Dispatchers.IO) {
        recheckStatusForItems(items.toList())
    }

    suspend fun commitToCloud(password: String, token: String = DataStore.githubToken): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val authToken = token.trim()
            if (authToken.isEmpty()) return@withContext false

            isSyncing = true
            try {
                // 1. Fetch current remote SHA if it exists
                val getUrl = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_PATH"
                val getReq = Request.Builder()
                    .url(getUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("Authorization", "Bearer $authToken")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "SherlockStreams/1.0")
                    .build()

                var sha: String? = null
                val getResp = httpClient.newCall(getReq).execute()
                if (getResp.code == 200) {
                    val respBody = getResp.body?.string() ?: ""
                    val obj = org.json.JSONObject(respBody)
                    sha = obj.optString("sha", null)
                }
                getResp.close()

                // 2. Encrypt the items
                val payload = VaultPayloadWrapper(items = items.toList())
                val plaintextJson = gson.toJson(payload)
                val encrypted = encryptPayload(plaintextJson, password)

                val cloudVault = EncryptedCloudVault(
                    salt = encrypted.saltB64,
                    iv = encrypted.ivB64,
                    ciphertext = encrypted.ciphertextB64,
                    updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                )

                val cloudJson = gson.toJson(cloudVault)
                val encodedContent = android.util.Base64.encodeToString(cloudJson.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

                // 3. PUT commit to GitHub
                val commitObj = org.json.JSONObject().apply {
                    put("message", "vault: encrypted update [${items.size} channels]")
                    put("content", encodedContent)
                    if (sha != null) {
                        put("sha", sha)
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

    private suspend fun deleteCloudVault(token: String): Boolean = withContext(Dispatchers.IO) {
        val authToken = token.trim()
        if (authToken.isEmpty()) return@withContext false
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_PATH"
            val getReq = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer $authToken")
                .build()

            val getResp = httpClient.newCall(getReq).execute()
            if (getResp.code != 200) {
                getResp.close()
                return@withContext true
            }

            val body = getResp.body?.string() ?: ""
            getResp.close()
            val sha = org.json.JSONObject(body).optString("sha", "")

            val delBody = org.json.JSONObject().apply {
                put("message", "vault: wipe encrypted cloud vault")
                put("sha", sha)
            }

            val delReq = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "Bearer $authToken")
                .delete(delBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val delResp = httpClient.newCall(delReq).execute()
            val success = delResp.isSuccessful
            delResp.close()
            success
        } catch (e: Exception) {
            false
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

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        return factory.generateSecret(spec).encoded
    }

    private fun generateRandomBytes(count: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(count)
        random.nextBytes(bytes)
        return bytes
    }
}
