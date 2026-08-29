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

object IPTVClient {
    private val USER_AGENTS = listOf(
        "IPTVSmartersPro/3.1.5.1 (Linux; Android 12; Build/SQ1D.220205.004)",
        "TiviMate/4.7.0 (Android TV; Linux 4.9.180)",
        "VLC/3.0.18 LibVLC/3.0.18",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "IPTVSmartersPro"
    )

    private val baseClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(30, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun getClient(): OkHttpClient {
        val timeout = com.projectstrong.iptv.data.SettingsManager.httpTimeoutSeconds.toLong()
        return baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    private fun getDeepQueryClient(): OkHttpClient {
        val timeout = maxOf(com.projectstrong.iptv.data.SettingsManager.httpTimeoutSeconds.toLong(), 30L)
        return baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
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
     */
    private fun executeWithAdaptiveHeaders(
        client: OkHttpClient, 
        targetUrl: String, 
        extraHeaders: Map<String, String> = emptyMap()
    ): Response {
        var lastResponse: Response? = null
        for (userAgent in USER_AGENTS) {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate")
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
                // Try next user-agent on socket/reset error
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
                        serverInfo = serverInfo
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
                val body = response.body?.string() ?: ""
                return@withContext JSONArray(body)
            }
            response.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getLiveStreamCount(baseUrl: String, user: String, pass: String): Int {
        val streams = getAllLiveStreams(baseUrl, user, pass)
        return streams?.length() ?: 0
    }

    suspend fun getVodStreamCount(baseUrl: String, user: String, pass: String): Int {
        val vods = getVodStreams(baseUrl, user, pass)
        return vods?.length() ?: 0
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

    suspend fun fetchRemoteText(rawUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = rawUrl.trim()
            // Transform standard pastebin/github web links to raw text links if applicable
            if (targetUrl.contains("pastebin.com/") && !targetUrl.contains("pastebin.com/raw/")) {
                targetUrl = targetUrl.replace("pastebin.com/", "pastebin.com/raw/")
            } else if (targetUrl.contains("gist.github.com/") && !targetUrl.contains("/raw")) {
                targetUrl = "$targetUrl/raw"
            } else if (targetUrl.contains("rentry.co/") && !targetUrl.contains("rentry.co/raw/")) {
                targetUrl = targetUrl.replace("rentry.co/", "rentry.co/raw/")
            } else if (targetUrl.contains("controlc.com/") && !targetUrl.contains("/index.php?act=submit&mode=ajax")) {
                // controlc paste raw format or direct page fetch
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/plain, text/html, */*")
                .build()

            getClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
