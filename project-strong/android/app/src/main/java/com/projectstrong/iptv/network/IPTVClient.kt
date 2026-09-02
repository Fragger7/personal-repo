package com.projectstrong.iptv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

sealed class VerificationResult {
    data class Success(
        val status: String, 
        val details: String,
        val expires: String = "N/A",
        val daysLeft: String = "N/A",
        val activeConn: String = "N/A",
        val maxConn: String = "N/A",
        val serverTimezone: String = "N/A",
        val serverTime: String = "N/A"
    ) : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
}

sealed class StreamEgressResult {
    data class Verified(
        val streamId: String,
        val contentType: String?,
        val latencyMs: Long,
        val sampleCount: Int = 1
    ) : StreamEgressResult()

    data class GhostBlocked(
        val code: Int,
        val description: String,
        val technicalDetails: String,
        val testedSamples: Int = 1
    ) : StreamEgressResult()

    data class Inconclusive(
        val reason: String
    ) : StreamEgressResult()
}

object IPTVClient {
    private val USER_AGENTS = listOf(
        "IPTVSmartersPro/3.1.5.1 (Linux; Android 12; Build/SQ1D.220205.004)",
        "TiviMate/4.7.0 (Android TV; Linux 4.9.180)",
        "VLC/3.0.18 LibVLC/3.0.18",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "IPTVSmartersPro"
    )

    private val baseClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        })
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun getClient(): OkHttpClient {
        val timeout = com.projectstrong.iptv.data.SettingsManager.httpTimeoutSeconds.toLong()
        val connectTimeout = if (com.projectstrong.iptv.data.SettingsManager.fastFailHedgingEnabled) {
            minOf(timeout, 4L)
        } else {
            timeout
        }
        return baseClient.newBuilder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(minOf(timeout, 5L), TimeUnit.SECONDS)
            .build()
    }

    private fun getDeepQueryClient(): OkHttpClient {
        val timeout = maxOf(com.projectstrong.iptv.data.SettingsManager.httpTimeoutSeconds.toLong(), 20L)
        return baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(20L, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Normalizes base URL, stripping redundant standard ports (e.g. :80 on http, :443 on https).
     */
    private fun normalizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (clean.startsWith("http://") && clean.endsWith(":80")) {
            clean = clean.removeSuffix(":80")
        } else if (clean.startsWith("https://") && clean.endsWith(":443")) {
            clean = clean.removeSuffix(":443")
        }
        return clean
    }

    /**
     * Executes an HTTP request with evasion headers and automatic user-agent fallback on 403 / 401 blocks.
     * Note: Does NOT manually set Accept-Encoding so OkHttp handles transparent GZIP compression and decompression automatically.
     */
    private fun executeWithAdaptiveHeaders(
        client: OkHttpClient, 
        targetUrl: String, 
        extraHeaders: Map<String, String> = emptyMap()
    ): Response {
        var lastResponse: Response? = null
        for ((index, userAgent) in USER_AGENTS.withIndex()) {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")

            extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
            val request = reqBuilder.build()

            try {
                val response = client.newCall(request).execute()
                if (response.code in 200..299) {
                    return response
                }
                // If 404, stopping immediately since path is wrong, not user-agent
                if (response.code == 404) {
                    return response
                }
                lastResponse?.close()
                lastResponse = response
            } catch (e: Throwable) {
                // If the very first request failed due to an unreachable host (DNS or Connect failure),
                // do not waste time retrying 5 different user agents on a non-existent/dead host.
                if (index == 0 && (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.NoRouteToHostException)) {
                    throw e
                }
            }
        }
        return lastResponse ?: client.newCall(
            Request.Builder().url(targetUrl).header("User-Agent", USER_AGENTS[0]).build()
        ).execute()
    }

    suspend fun verifyXtream(baseUrl: String, user: String, pass: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeBaseUrl(baseUrl)
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            
            // Tier 1A: Standard JSON player_api.php Handshake
            var apiUrl = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass"
            var response = executeWithAdaptiveHeaders(getClient(), apiUrl)
            
            if (response.code == 404) {
                response.close()
                // Stalker Fallback endpoint check
                apiUrl = "$normalizedUrl/server/load.php?type=stb&action=handshake&type=itv"
                response = executeWithAdaptiveHeaders(getClient(), apiUrl)
            }

            val code = response.code
            val serverHeader = response.header("Server")
            val isCloudflare = response.header("CF-RAY") != null || serverHeader?.contains("cloudflare", ignoreCase = true) == true
            val body = response.body?.string() ?: ""

            if (code == 200 && body.contains("\"user_info\"")) {
                try {
                    val json = JSONObject(body)
                    val userInfo = json.optJSONObject("user_info")
                    val serverInfo = json.optJSONObject("server_info")

                    // Update Provider Intelligence Forensics
                    com.projectstrong.iptv.data.ProviderIntelligenceManager.updateFromFingerprint(
                        baseUrl = baseUrl,
                        serverHeader = serverHeader,
                        isCloudflare = isCloudflare,
                        serverInfo = serverInfo,
                        userInfo = userInfo
                    )
                    val auth = userInfo?.optInt("auth", 1) ?: 1
                    val status = userInfo?.optString("status", "")
                    
                    if (auth == 0 || status.equals("Expired", ignoreCase = true) || status.equals("Disabled", ignoreCase = true)) {
                        return@withContext VerificationResult.Failed("Expired / Inactive Account")
                    }

                    val active = status.equals("Active", ignoreCase = true) || auth == 1
                    val maxConns = userInfo?.optString("max_connections", "1")
                    val activeConns = userInfo?.optString("active_cons", "0")
                    var expDate = "Unlimited"
                    var daysLeft = "9999"
                    val expTs = userInfo?.optString("exp_date", "")
                    
                    if (!expTs.isNullOrEmpty() && expTs != "null") {
                        try {
                            val ts = expTs.toLong() * 1000
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            expDate = sdf.format(java.util.Date(ts))
                            val diffMillis = ts - System.currentTimeMillis()
                            val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                            daysLeft = if (days < 0) "0" else days.toString()
                        } catch (e: Exception) { }
                    }
                    val sTz = serverInfo?.optString("timezone", "N/A")
                    val sTime = serverInfo?.optString("time_now", "N/A")
                    val statusStr = if (active) "Active" else "Expired/Inactive"
                    return@withContext VerificationResult.Success(statusStr, "Verified", expDate, daysLeft, activeConns ?: "0", maxConns ?: "1", sTz ?: "N/A", sTime ?: "N/A")
                } catch (e: Exception) {
                    // Fall through to M3U get.php verification
                }
            }

            // Tier 1B: Fallback to /get.php verification (when player_api.php is disabled or returns 403/HTML)
            val m3uUrls = listOf(
                "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=ts",
                "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus",
                "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass",
                "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u&output=ts",
                "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=m3u8"
            )

            for (m3uUrl in m3uUrls) {
                try {
                    val m3uResponse = executeWithAdaptiveHeaders(getClient(), m3uUrl)
                    if (m3uResponse.code == 200) {
                        val m3uBody = m3uResponse.body?.string() ?: ""
                        if (m3uBody.startsWith("#EXTM3U") || m3uBody.contains("#EXTINF")) {
                            var expDate = "Unknown"
                            var daysLeft = "Unknown"
                            // Search for embedded expiration attributes inside #EXTM3U
                            val expMatch = Pattern.compile("(?i)exp_date=\"?([0-9]{10})\"?").matcher(m3uBody)
                            if (expMatch.find()) {
                                try {
                                    val ts = expMatch.group(1).toLong() * 1000
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    expDate = sdf.format(java.util.Date(ts))
                                    val diffMillis = ts - System.currentTimeMillis()
                                    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                                    daysLeft = if (days < 0) "0" else days.toString()
                                } catch (e: Exception) {}
                            }
                            return@withContext VerificationResult.Success(
                                status = "Active",
                                details = "M3U Verified (get.php)",
                                expires = expDate,
                                daysLeft = daysLeft,
                                activeConn = "0",
                                maxConn = "1"
                            )
                        }
                    }
                    m3uResponse.close()
                } catch (e: Exception) {
                    // Try next fallback URL
                }
            }

            if (code == 403) {
                return@withContext VerificationResult.Failed("Cloud Blocked (HTTP 403)")
            } else if (code == 521) {
                return@withContext VerificationResult.Failed("Offline (Server Dead 521)")
            } else if (code == 200 && body.contains("Unauthorized", ignoreCase = true)) {
                return@withContext VerificationResult.Failed("Invalid Credentials / Unauthorized")
            } else {
                return@withContext VerificationResult.Failed("Firewalled / Blocked (HTTP $code)")
            }
        } catch (e: Exception) {
            return@withContext VerificationResult.Failed("Network Error: ${e.localizedMessage}")
        }
    }

    suspend fun verifyStalker(baseUrl: String, mac: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeBaseUrl(baseUrl)
            val encodedMac = URLEncoder.encode(mac, "UTF-8")
            var url = "$normalizedUrl/c/server/load.php?type=stb&action=handshake&type=itv"
            
            val stalkerHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                "Cookie" to "mac=$encodedMac; stb_lang=en; timezone=Europe/Kiev;"
            )

            var response = executeWithAdaptiveHeaders(getClient(), url, stalkerHeaders)
            if (response.code == 404) {
                response.close()
                url = "$normalizedUrl/server/load.php?type=stb&action=handshake&type=itv"
                response = executeWithAdaptiveHeaders(getClient(), url, stalkerHeaders)
            }
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 200) {
                if (body.contains("\"js\"")) {
                    return@withContext VerificationResult.Success(
                        status = "Active",
                        details = "Token Handshake OK"
                    )
                } else {
                    return@withContext VerificationResult.Failed("Invalid MAC / Unauthorized")
                }
            } else if (code == 403) {
                return@withContext VerificationResult.Failed("Cloud Blocked (HTTP 403)")
            } else if (code == 521) {
                return@withContext VerificationResult.Failed("Offline (Server Dead 521)")
            } else {
                return@withContext VerificationResult.Failed("Firewalled / Blocked (HTTP $code)")
            }
        } catch (e: Exception) {
            return@withContext VerificationResult.Failed("Network Error: ${e.localizedMessage}")
        }
    }

    suspend fun getLiveCategories(baseUrl: String, user: String, pass: String): JSONArray? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        
        // Tier 1: Try JSON player_api.php get_live_categories
        try {
            val url = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_categories"
            val response = executeWithAdaptiveHeaders(getDeepQueryClient(), url)
            if (response.code == 200) {
                val body = response.body
                if (body != null) {
                    val result = JSONArray()
                    val reader = android.util.JsonReader(BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                    if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                val obj = JSONObject()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    val token = reader.peek()
                                    when (token) {
                                        android.util.JsonToken.STRING -> obj.put(name, reader.nextString())
                                        android.util.JsonToken.NUMBER -> {
                                            try {
                                                obj.put(name, reader.nextInt())
                                            } catch(e: Exception) {
                                                obj.put(name, reader.nextDouble())
                                            }
                                        }
                                        android.util.JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                                        android.util.JsonToken.NULL -> { reader.nextNull(); obj.put(name, JSONObject.NULL) }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                result.put(obj)
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    }
                    reader.close()
                    if (result.length() > 0) {
                        try {
                            val parsedCats = (0 until minOf(result.length(), 200)).mapNotNull { i ->
                                result.optJSONObject(i)?.let { obj ->
                                    com.projectstrong.iptv.ui.components.CategoryItem(
                                        id = obj.optString("category_id", ""),
                                        name = obj.optString("category_name", ""),
                                        count = 0
                                    )
                                }
                            }
                            com.projectstrong.iptv.data.ProviderIntelligenceManager.mineFromStreams(baseUrl, parsedCats, null)
                        } catch (e: Exception) { }
                        return@withContext result
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            // Fall through to M3U parsing
        }

        // Tier 2: Fallback parse categories directly from M3U playlist (get.php)
        try {
            val m3uCategories = fetchCategoriesFromM3U(normalizedUrl, encodedUser, encodedPass)
            if (m3uCategories != null && m3uCategories.length() > 0) {
                try {
                    val parsedCats = (0 until minOf(m3uCategories.length(), 200)).mapNotNull { i ->
                        m3uCategories.optJSONObject(i)?.let { obj ->
                            com.projectstrong.iptv.ui.components.CategoryItem(
                                id = obj.optString("category_id", ""),
                                name = obj.optString("category_name", ""),
                                count = 0
                            )
                        }
                    }
                    com.projectstrong.iptv.data.ProviderIntelligenceManager.mineFromStreams(baseUrl, parsedCats, null)
                } catch (e: Exception) { }
                return@withContext m3uCategories
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getAllLiveStreams(baseUrl: String, user: String, pass: String): JSONArray? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        
        // Tier 1: Try JSON player_api.php get_live_streams
        try {
            val url = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
            val response = executeWithAdaptiveHeaders(getDeepQueryClient(), url)
            if (response.code == 200) {
                val body = response.body
                if (body != null) {
                    val result = JSONArray()
                    val reader = android.util.JsonReader(BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                    if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                val obj = JSONObject()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    val token = reader.peek()
                                    when (token) {
                                        android.util.JsonToken.STRING -> obj.put(name, reader.nextString())
                                        android.util.JsonToken.NUMBER -> {
                                            try {
                                                obj.put(name, reader.nextInt())
                                            } catch(e: Exception) {
                                                obj.put(name, reader.nextDouble())
                                            }
                                        }
                                        android.util.JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                                        android.util.JsonToken.NULL -> { reader.nextNull(); obj.put(name, JSONObject.NULL) }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                result.put(obj)
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    }
                    reader.close()
                    if (result.length() > 0) {
                        try {
                            val parsedChannels = (0 until minOf(result.length(), 200)).mapNotNull { i ->
                                result.optJSONObject(i)?.let { obj ->
                                    com.projectstrong.iptv.ui.components.ChannelItem(
                                        streamId = obj.optString("stream_id", ""),
                                        name = obj.optString("name", ""),
                                        categoryId = obj.optString("category_id", ""),
                                        iconUrl = obj.optString("stream_icon", ""),
                                        directUrl = ""
                                    )
                                }
                            }
                            com.projectstrong.iptv.data.ProviderIntelligenceManager.mineFromStreams(baseUrl, null, parsedChannels)
                        } catch (e: Exception) { }
                        return@withContext result
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            // Fall through to M3U
        }

        // Tier 2: Fallback parse streams directly from M3U playlist
        try {
            val m3uStreams = fetchStreamsFromM3U(normalizedUrl, encodedUser, encodedPass)
            if (m3uStreams != null && m3uStreams.length() > 0) {
                try {
                    val parsedChannels = (0 until minOf(m3uStreams.length(), 200)).mapNotNull { i ->
                        m3uStreams.optJSONObject(i)?.let { obj ->
                            com.projectstrong.iptv.ui.components.ChannelItem(
                                streamId = obj.optString("stream_id", ""),
                                name = obj.optString("name", ""),
                                categoryId = obj.optString("category_id", ""),
                                iconUrl = obj.optString("stream_icon", ""),
                                directUrl = ""
                            )
                        }
                    }
                    com.projectstrong.iptv.data.ProviderIntelligenceManager.mineFromStreams(baseUrl, null, parsedChannels)
                } catch (e: Exception) { }
                return@withContext m3uStreams
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getLiveStreams(baseUrl: String, user: String, pass: String, categoryId: String): JSONArray? = withContext(Dispatchers.IO) {
        val allStreams = getAllLiveStreams(baseUrl, user, pass) ?: return@withContext null
        if (categoryId.isEmpty() || categoryId == "all") {
            return@withContext allStreams
        }
        val filtered = JSONArray()
        for (i in 0 until allStreams.length()) {
            val stream = allStreams.optJSONObject(i)
            if (stream != null && stream.optString("category_id") == categoryId) {
                filtered.put(stream)
            }
        }
        return@withContext filtered
    }

    suspend fun getVodStreams(baseUrl: String, user: String, pass: String): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeBaseUrl(baseUrl)
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val url = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_vod_streams"
            val response = executeWithAdaptiveHeaders(getDeepQueryClient(), url)
            if (response.code == 200) {
                val body = response.body
                if (body != null) {
                    val result = JSONArray()
                    val reader = android.util.JsonReader(BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                    if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                val obj = JSONObject()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    val token = reader.peek()
                                    when (token) {
                                        android.util.JsonToken.STRING -> obj.put(name, reader.nextString())
                                        android.util.JsonToken.NUMBER -> {
                                            try {
                                                obj.put(name, reader.nextInt())
                                            } catch (e: Exception) {
                                                obj.put(name, reader.nextDouble())
                                            }
                                        }
                                        android.util.JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                                        android.util.JsonToken.NULL -> { reader.nextNull(); obj.put(name, JSONObject.NULL) }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                result.put(obj)
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    }
                    reader.close()
                    return@withContext result
                }
            }
            response.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Ultra-fast zero-allocation stream counter for JSON arrays.
     * Uses JsonReader.skipValue() directly from network byte stream to count 50k+ items in milliseconds with 0 RAM allocated.
     */
    private fun fastCountJsonArray(response: Response): Int {
        try {
            val body = response.body ?: return 0
            val reader = android.util.JsonReader(BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 65536))
            var count = 0
            if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.skipValue()
                    count++
                }
                reader.endArray()
            }
            reader.close()
            response.close()
            return count
        } catch (e: Exception) {
            try { response.close() } catch (_: Exception) {}
            return 0
        }
    }

    suspend fun getLiveStreamCount(baseUrl: String, user: String, pass: String): Int = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        
        // Tier 1: Fast direct JSON stream count (Zero RAM allocation)
        try {
            val url = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
            val response = executeWithAdaptiveHeaders(getDeepQueryClient(), url)
            if (response.code == 200) {
                val count = fastCountJsonArray(response)
                if (count > 0) return@withContext count
            } else {
                response.close()
            }
        } catch (e: Exception) {
            // Fall through to M3U count
        }

        // Tier 2: Fast M3U stream count fallback
        return@withContext fastCountM3UStreams(normalizedUrl, encodedUser, encodedPass)
    }

    suspend fun getVodStreamCount(baseUrl: String, user: String, pass: String): Int = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        
        // Fast direct JSON stream count (Zero RAM allocation)
        try {
            val url = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_vod_streams"
            val response = executeWithAdaptiveHeaders(getDeepQueryClient(), url)
            if (response.code == 200) {
                val count = fastCountJsonArray(response)
                return@withContext count
            } else {
                response.close()
            }
        } catch (e: Exception) { }
        return@withContext 0
    }

    /**
     * Fast M3U line stream counter that parses #EXTINF headers on-the-fly without allocating memory.
     */
    private fun fastCountM3UStreams(baseUrl: String, encodedUser: String, encodedPass: String): Int {
        val m3uUrls = listOf(
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=ts",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass"
        )
        for (m3uUrl in m3uUrls) {
            try {
                val response = executeWithAdaptiveHeaders(getDeepQueryClient(), m3uUrl)
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val reader = BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768)
                        var count = 0
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line?.startsWith("#EXTINF") == true) {
                                count++
                            }
                        }
                        reader.close()
                        response.close()
                        if (count > 0) return count
                    }
                }
                response.close()
            } catch (e: Exception) { }
        }
        return 0
    }

    /**
     * Memory-efficient stream parser for M3U playlists to extract unique Category groupings.
     */
    private fun fetchCategoriesFromM3U(baseUrl: String, encodedUser: String, encodedPass: String): JSONArray? {
        val m3uUrls = listOf(
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=ts",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u&output=ts",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass"
        )

        for (m3uUrl in m3uUrls) {
            try {
                val response = executeWithAdaptiveHeaders(getDeepQueryClient(), m3uUrl)
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val reader = BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768)
                        val categoriesSet = mutableSetOf<String>()
                        val result = JSONArray()

                        val groupTitlePattern = Pattern.compile("(?i)group-title=\"([^\"]+)\"")
                        var line: String?
                        var isM3u = false
                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line ?: break
                            if (currentLine.startsWith("#EXTM3U") || currentLine.startsWith("#EXTINF")) {
                                isM3u = true
                            }
                            if (currentLine.startsWith("#EXTINF")) {
                                val matcher = groupTitlePattern.matcher(currentLine)
                                if (matcher.find()) {
                                    val group = matcher.group(1).trim()
                                    if (group.isNotEmpty() && categoriesSet.add(group)) {
                                        val catObj = JSONObject().apply {
                                            put("category_id", group)
                                            put("category_name", group)
                                        }
                                        result.put(catObj)
                                    }
                                }
                            }
                        }
                        reader.close()
                        if (isM3u && result.length() > 0) {
                            return result
                        }
                    }
                }
                response.close()
            } catch (e: Exception) {
                // Try next M3U URL fallback
            }
        }
        return null
    }

    /**
     * Memory-efficient stream parser for M3U playlists to extract channel stream items.
     */
    private fun fetchStreamsFromM3U(baseUrl: String, encodedUser: String, encodedPass: String): JSONArray? {
        val m3uUrls = listOf(
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=ts",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u&output=ts",
            "$baseUrl/get.php?username=$encodedUser&password=$encodedPass"
        )

        for (m3uUrl in m3uUrls) {
            try {
                val response = executeWithAdaptiveHeaders(getDeepQueryClient(), m3uUrl)
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val reader = BufferedReader(InputStreamReader(body.byteStream(), "UTF-8"), 32768)
                        val result = JSONArray()

                        val groupTitlePattern = Pattern.compile("(?i)group-title=\"([^\"]+)\"")
                        val tvgLogoPattern = Pattern.compile("(?i)tvg-logo=\"([^\"]+)\"")
                        val tvgIdPattern = Pattern.compile("(?i)tvg-id=\"([^\"]+)\"")

                        var lastHeader: String? = null
                        var line: String?
                        var streamIdCounter = 1

                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line?.trim() ?: break
                            if (currentLine.startsWith("#EXTINF")) {
                                lastHeader = currentLine
                            } else if (currentLine.isNotEmpty() && !currentLine.startsWith("#") && lastHeader != null) {
                                val header = lastHeader
                                lastHeader = null

                                val channelName = header.substringAfterLast(",").trim()
                                val groupMatcher = groupTitlePattern.matcher(header)
                                val groupName = if (groupMatcher.find()) groupMatcher.group(1).trim() else "Uncategorized"

                                val logoMatcher = tvgLogoPattern.matcher(header)
                                val logoUrl = if (logoMatcher.find()) logoMatcher.group(1).trim() else ""

                                val idMatcher = tvgIdPattern.matcher(header)
                                val tvgId = if (idMatcher.find()) idMatcher.group(1).trim() else ""

                                val streamObj = JSONObject().apply {
                                    put("num", streamIdCounter)
                                    put("name", channelName.ifEmpty { "Channel $streamIdCounter" })
                                    put("stream_id", streamIdCounter)
                                    put("stream_icon", logoUrl)
                                    put("epg_channel_id", tvgId)
                                    put("category_id", groupName)
                                    put("category_name", groupName)
                                    put("direct_source", currentLine)
                                }
                                result.put(streamObj)
                                streamIdCounter++
                            }
                        }
                        reader.close()
                        if (result.length() > 0) {
                            return result
                        }
                    }
                }
                response.close()
            } catch (e: Exception) {
                // Try next M3U URL fallback
            }
        }
        return null
    }

    private fun extractPayloadFromHtmlIfPresent(html: String): String {
        try {
            // Check for textarea (e.g. Pastebin HTML, ControlC HTML, Rentry HTML)
            val textareaPattern = Pattern.compile("<textarea[^>]*>([\\s\\S]*?)</textarea>", Pattern.CASE_INSENSITIVE)
            val textareaMatcher = textareaPattern.matcher(html)
            if (textareaMatcher.find()) {
                val content = textareaMatcher.group(1)?.trim() ?: ""
                if (content.isNotBlank()) {
                    return unescapeHtml(content)
                }
            }

            // Check for pre or code or paste_container
            val prePattern = Pattern.compile("<pre[^>]*>([\\s\\S]*?)</pre>", Pattern.CASE_INSENSITIVE)
            val preMatcher = prePattern.matcher(html)
            if (preMatcher.find()) {
                val content = preMatcher.group(1)?.trim() ?: ""
                if (content.isNotBlank() && !content.contains("<html", ignoreCase = true)) {
                    return unescapeHtml(content)
                }
            }
        } catch (e: Exception) {
            // Ignore HTML parse errors and fallback
        }
        return html
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x2F;", "/")
            .replace("&nbsp;", " ")
    }

    private fun fetchAndDecryptPasteSh(rawUrl: String): String? {
        return try {
            val hashIndex = rawUrl.indexOf('#')
            val urlPart = if (hashIndex != -1) rawUrl.substring(0, hashIndex).trim() else rawUrl.trim()
            val clientKey = if (hashIndex != -1) rawUrl.substring(hashIndex + 1).trim() else ""
            val idVal = urlPart.trimEnd('/').substringAfterLast('/')
            val downloadUrl = if (urlPart.endsWith(".txt", ignoreCase = true)) urlPart else "$urlPart.txt"

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "curl/8.4.0")
                .header("Accept", "text/plain, text/vnd.paste.sh-v2, text/vnd.paste.sh-v3, */*")
                .build()

            var responseBody: String? = null
            getClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    responseBody = response.body?.string()
                }
            }

            if (responseBody.isNullOrBlank()) return null

            // If it is a public unencrypted paste, responseBody is direct plaintext
            if (clientKey.isBlank()) {
                return responseBody!!.trim()
            }

            val trimmedBody = responseBody!!.trim()
            val lines = trimmedBody.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return responseBody!!.trim()

            var serverKey = ""
            var cipherB64 = ""

            // Check if line 0 is already base64 ciphertext with Salted__ header
            var line0Bytes: ByteArray? = null
            try {
                line0Bytes = Base64.decode(lines[0], Base64.DEFAULT)
            } catch (e: Exception) {
                line0Bytes = null
            }

            if (line0Bytes != null && line0Bytes.size >= 16 && String(line0Bytes.copyOfRange(0, 8), Charsets.US_ASCII) == "Salted__") {
                serverKey = ""
                cipherB64 = lines.joinToString("")
            } else if (lines.size > 1) {
                serverKey = lines[0]
                cipherB64 = lines.drop(1).joinToString("")
            } else {
                cipherB64 = lines[0]
            }

            if (cipherB64.isBlank()) {
                return responseBody!!.trim()
            }

            val rawBytes = try {
                Base64.decode(cipherB64, Base64.DEFAULT)
            } catch (e: Exception) {
                return responseBody!!.trim()
            }

            // Verify "Salted__" header (8 bytes)
            if (rawBytes.size < 16) return responseBody!!.trim()
            val headerMagic = String(rawBytes, 0, 8, Charsets.US_ASCII)
            if (headerMagic != "Salted__") {
                return responseBody!!.trim()
            }

            val salt = rawBytes.copyOfRange(8, 16)
            val ciphertext = rawBytes.copyOfRange(16, rawBytes.size)

            val passphrase = "${idVal}${serverKey}${clientKey}https://paste.sh"

            // Key derivation: PBKDF2 with HMAC-SHA512, iter=1, dkLen=48 (32 bytes AES Key + 16 bytes IV)
            val mac = Mac.getInstance("HmacSHA512")
            val keySpec = SecretKeySpec(passphrase.toByteArray(Charsets.UTF_8), "HmacSHA512")
            mac.init(keySpec)
            mac.update(salt)
            mac.update(byteArrayOf(0x00, 0x00, 0x00, 0x01))
            val keyAndIv = mac.doFinal()

            val key = keyAndIv.copyOfRange(0, 32)
            val iv = keyAndIv.copyOfRange(32, 48)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            var decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            // Remove any leading metadata headers (Subject: ... / Content-Type: ...) if present
            if (decryptedStr.startsWith("Subject:", ignoreCase = true) || decryptedStr.startsWith("Content-Type:", ignoreCase = true)) {
                val splitIndex = decryptedStr.indexOf("\n\n")
                val splitIndexCr = decryptedStr.indexOf("\r\n\r\n")
                if (splitIndex != -1) {
                    decryptedStr = decryptedStr.substring(splitIndex + 2).trim()
                } else if (splitIndexCr != -1) {
                    decryptedStr = decryptedStr.substring(splitIndexCr + 4).trim()
                }
            }

            if (decryptedStr.isNotBlank()) decryptedStr else responseBody
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchRemoteText(rawUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = rawUrl.trim()
            if (targetUrl.isEmpty()) return@withContext null

            // Prepend https:// if protocol is missing
            if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
                targetUrl = "https://$targetUrl"
            }

            // Special decryption handler for paste.sh links
            if (targetUrl.contains("paste.sh/", ignoreCase = true)) {
                val decrypted = fetchAndDecryptPasteSh(targetUrl)
                if (!decrypted.isNullOrBlank()) {
                    return@withContext decrypted
                }
            }

            // Transform standard pastebin/github/rentry web links to direct raw text links
            if (targetUrl.contains("pastebin.com/", ignoreCase = true) && !targetUrl.contains("pastebin.com/raw/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("pastebin.com/", "pastebin.com/raw/", ignoreCase = true)
            } else if (targetUrl.contains("gist.github.com/", ignoreCase = true) && !targetUrl.endsWith("/raw", ignoreCase = true)) {
                targetUrl = if (targetUrl.contains("/raw/")) targetUrl else "$targetUrl/raw"
            } else if (targetUrl.contains("rentry.co/", ignoreCase = true) && !targetUrl.contains("rentry.co/raw/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("rentry.co/", "rentry.co/raw/", ignoreCase = true)
            } else if (targetUrl.contains("rentry.org/", ignoreCase = true) && !targetUrl.contains("rentry.org/raw/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("rentry.org/", "rentry.org/raw/", ignoreCase = true)
            } else if (targetUrl.contains("dpaste.org/", ignoreCase = true) && !targetUrl.endsWith(".txt", ignoreCase = true) && !targetUrl.endsWith("/raw", ignoreCase = true)) {
                targetUrl = "$targetUrl.txt"
            } else if (targetUrl.contains("dpaste.com/", ignoreCase = true) && !targetUrl.endsWith(".txt", ignoreCase = true)) {
                targetUrl = "$targetUrl.txt"
            } else if (targetUrl.contains("paste.ee/p/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("paste.ee/p/", "paste.ee/r/", ignoreCase = true)
            } else if (targetUrl.contains("pastery.net/", ignoreCase = true) && !targetUrl.contains("/raw", ignoreCase = true)) {
                targetUrl = "$targetUrl/raw"
            } else if (targetUrl.contains("paste.debian.net/", ignoreCase = true) && !targetUrl.contains("/plain/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("paste.debian.net/", "paste.debian.net/plain/", ignoreCase = true)
            } else if (targetUrl.contains("hastebin.com/", ignoreCase = true) && !targetUrl.contains("hastebin.com/raw/", ignoreCase = true)) {
                targetUrl = targetUrl.replace("hastebin.com/", "hastebin.com/raw/", ignoreCase = true)
            }

            val candidateUserAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "IPTVSmartersPro/3.1.5.1 (Linux; Android 12; Build/SQ1D.220205.004)",
                "curl/8.4.0"
            )

            var rawBody: String? = null
            for (ua in candidateUserAgents) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", ua)
                        .header("Accept", "text/plain, text/html, application/xhtml+xml, */*")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    getClient().newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyText = response.body?.string()
                            if (!bodyText.isNullOrBlank()) {
                                rawBody = bodyText
                                return@use
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Try next user agent
                }
                if (rawBody != null) break
            }

            if (rawBody.isNullOrBlank()) return@withContext null

            val cleanedBody = extractPayloadFromHtmlIfPresent(rawBody!!)
            if (cleanedBody.isNotBlank()) cleanedBody else rawBody
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Non-blocking, low-latency stream egress probe (Ghost Line & Anti-Dump detector).
     *
     * Strict Guardrails:
     * 1. Multi-stream consensus: Samples up to 2-3 distinct streams.
     * 2. Dual container testing: Tests both .ts and .m3u8 endpoints.
     * 3. Zero False Negatives: Timeouts or temporary 5xx errors return Inconclusive without penalizing active status.
     * 4. Ghost Line identification: Flags HTTP 456 (Stream Egress Disabled) and HTTP 884 (Anti-Dump Lockout).
     */
    suspend fun probeStreamEgress(baseUrl: String, user: String, pass: String): StreamEgressResult = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeBaseUrl(baseUrl)
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val client = getClient()

            // 1. Sample up to 3 stream IDs from player_api.php using streaming JsonReader (zero OOM memory overhead)
            val streamsApiUrl = "$normalizedUrl/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
            val sampledStreamIds = mutableListOf<String>()

            try {
                val req = Request.Builder()
                    .url(streamsApiUrl)
                    .header("User-Agent", USER_AGENTS[0])
                    .header("Accept", "application/json, */*")
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful && res.body != null) {
                        val reader = android.util.JsonReader(BufferedReader(InputStreamReader(res.body!!.byteStream(), "UTF-8"), 8192))
                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext() && sampledStreamIds.size < 3) {
                                if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    var currentStreamId = ""
                                    while (reader.hasNext()) {
                                        val name = reader.nextName()
                                        if (name == "stream_id") {
                                            val token = reader.peek()
                                            currentStreamId = when (token) {
                                                android.util.JsonToken.STRING -> reader.nextString()
                                                android.util.JsonToken.NUMBER -> reader.nextInt().toString()
                                                else -> { reader.skipValue(); "" }
                                            }
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (currentStreamId.isNotBlank()) {
                                        sampledStreamIds.add(currentStreamId)
                                    }
                                } else {
                                    reader.skipValue()
                                }
                            }
                        }
                        reader.close()
                    }
                }
            } catch (_: Exception) {}

            // 2. Fallback: If no stream IDs extracted via JSON, check get.php M3U endpoint
            val directStreamUrls = mutableListOf<String>()
            if (sampledStreamIds.isEmpty()) {
                val m3uUrl = "$normalizedUrl/get.php?username=$encodedUser&password=$encodedPass&type=m3u_plus&output=ts"
                try {
                    val m3uReq = Request.Builder()
                        .url(m3uUrl)
                        .header("User-Agent", USER_AGENTS[0])
                        .build()
                    client.newCall(m3uReq).execute().use { m3uRes ->
                        val code = m3uRes.code
                        if (code == 884) {
                            return@withContext StreamEgressResult.GhostBlocked(
                                code = 884,
                                description = "Anti-Dump Lockout (HTTP 884)",
                                technicalDetails = "Provider active, but playlist and stream dumping are blocked by server security (HTTP 884).",
                                testedSamples = 1
                            )
                        } else if (code == 456) {
                            return@withContext StreamEgressResult.GhostBlocked(
                                code = 456,
                                description = "Ghost Line (Stream Blocked 456)",
                                technicalDetails = "Provider authenticated credentials, but stream delivery is disabled with HTTP 456.",
                                testedSamples = 1
                            )
                        } else if (code == 200) {
                            val body = m3uRes.body?.string() ?: ""
                            val lines = body.lines()
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                    directStreamUrls.add(trimmed)
                                    if (directStreamUrls.size >= 2) break
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // If still no samples available to test, return Inconclusive safely
            if (sampledStreamIds.isEmpty() && directStreamUrls.isEmpty()) {
                return@withContext StreamEgressResult.Inconclusive("No media channels available to sample.")
            }

            // 3. Multi-sample Stream Testing
            val sampleCodes = mutableListOf<Int>()
            var anyTimeoutOrNetworkError = false

            // Test sampled stream IDs or direct URLs
            val candidateUrls = if (sampledStreamIds.isNotEmpty()) {
                sampledStreamIds.map { sId ->
                    Pair(sId, "$normalizedUrl/live/$user/$pass/$sId.ts")
                }
            } else {
                directStreamUrls.mapIndexed { idx, url ->
                    Pair("M3U-Line-${idx + 1}", url)
                }
            }

            for ((streamId, url) in candidateUrls) {
                var streamSuccess = false
                val startTime = System.currentTimeMillis()

                // Try .ts first
                try {
                    val streamReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENTS[0])
                        .header("Range", "bytes=0-2048")
                        .build()
                    client.newCall(streamReq).execute().use { streamRes ->
                        val code = streamRes.code
                        val latency = System.currentTimeMillis() - startTime
                        val contentType = streamRes.header("Content-Type")

                        if (code in 200..206) {
                            val bytes = streamRes.body?.byteStream()?.readBytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                return@withContext StreamEgressResult.Verified(
                                    streamId = streamId,
                                    contentType = contentType,
                                    latencyMs = latency,
                                    sampleCount = sampleCodes.size + 1
                                )
                            }
                        } else {
                            sampleCodes.add(code)
                        }
                    }
                } catch (e: Exception) {
                    anyTimeoutOrNetworkError = true
                }

                // If .ts returned 456 or failed, test .m3u8 alternative
                if (!streamSuccess && sampledStreamIds.isNotEmpty()) {
                    try {
                        val m3u8Url = "$normalizedUrl/live/$user/$pass/$streamId.m3u8"
                        val m3u8Req = Request.Builder()
                            .url(m3u8Url)
                            .header("User-Agent", USER_AGENTS[0])
                            .header("Range", "bytes=0-2048")
                            .build()
                        client.newCall(m3u8Req).execute().use { m3u8Res ->
                            val code = m3u8Res.code
                            val latency = System.currentTimeMillis() - startTime
                            val contentType = m3u8Res.header("Content-Type")

                            if (code in 200..206) {
                                return@withContext StreamEgressResult.Verified(
                                    streamId = streamId,
                                    contentType = contentType,
                                    latencyMs = latency,
                                    sampleCount = sampleCodes.size + 1
                                )
                            } else {
                                if (!sampleCodes.contains(code)) {
                                    sampleCodes.add(code)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        anyTimeoutOrNetworkError = true
                    }
                }
            }

            // 4. Consensus Decision with Guardrails
            if (sampleCodes.isNotEmpty() && sampleCodes.all { it == 456 }) {
                return@withContext StreamEgressResult.GhostBlocked(
                    code = 456,
                    description = "Ghost Line (Stream Blocked 456)",
                    technicalDetails = "Account passes API handshake, but streaming egress is disabled (HTTP 456).",
                    testedSamples = sampleCodes.size
                )
            } else if (sampleCodes.isNotEmpty() && sampleCodes.all { it == 884 }) {
                return@withContext StreamEgressResult.GhostBlocked(
                    code = 884,
                    description = "Anti-Dump Lockout (HTTP 884)",
                    technicalDetails = "Provider blocks playlist exports and stream egress with HTTP 884.",
                    testedSamples = sampleCodes.size
                )
            } else if (sampleCodes.isNotEmpty() && sampleCodes.all { it == 403 }) {
                return@withContext StreamEgressResult.GhostBlocked(
                    code = 403,
                    description = "Stream Delivery Forbidden (HTTP 403)",
                    technicalDetails = "API handshake valid, but media delivery server rejects requests with HTTP 403.",
                    testedSamples = sampleCodes.size
                )
            } else if (anyTimeoutOrNetworkError) {
                return@withContext StreamEgressResult.Inconclusive("Network latency or timeout during stream probe (Retaining handshake status)")
            } else {
                return@withContext StreamEgressResult.Inconclusive("HTTP ${sampleCodes.joinToString(", ")} returned on sample channels.")
            }
        } catch (e: Exception) {
            return@withContext StreamEgressResult.Inconclusive("Probe error: ${e.localizedMessage ?: "Unknown"}")
        }
    }
}
