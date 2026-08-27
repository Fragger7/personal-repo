package com.projectstrong.iptv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.net.URLEncoder

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
    private val baseClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(30, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(false)
        .build()

    private fun getClient(): OkHttpClient {
        val timeout = com.projectstrong.iptv.data.SettingsManager.httpTimeoutSeconds.toLong()
        return baseClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    suspend fun verifyXtream(baseUrl: String, user: String, pass: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            var url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass"
            var request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVSmartersPro")
                .build()

            var response = getClient().newCall(request).execute()
            if (response.code == 404) {
                response.close()
                url = "${baseUrl.trimEnd('/')}/server/load.php?type=stb&action=handshake&type=itv"
                request = request.newBuilder().url(url).build()
                response = getClient().newCall(request).execute()
            }
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 200) {
                if (body.contains("\"user_info\"")) {
                    try {
                        val json = JSONObject(body)
                        val userInfo = json.optJSONObject("user_info")
                        val serverInfo = json.optJSONObject("server_info")
                        val active = userInfo?.optString("status", "") == "Active"
                        val maxConns = userInfo?.optString("max_connections", "1")
                        val activeConns = userInfo?.optString("active_cons", "0")
                        var expDate = "N/A"
                        var daysLeft = "N/A"
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
                        return@withContext VerificationResult.Failed("Parse Error: Invalid JSON Format")
                    }
                } else {
                    return@withContext VerificationResult.Failed("Invalid Credentials / No User Info")
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

    suspend fun verifyStalker(baseUrl: String, mac: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            var url = "${baseUrl.trimEnd('/')}/c/server/load.php?type=stb&action=handshake&type=itv"
            val encodedMac = URLEncoder.encode(mac, "UTF-8")
            var request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                .header("Cookie", "mac=$encodedMac; stb_lang=en; timezone=Europe/Kiev;")
                .build()

            var response = getClient().newCall(request).execute()
            if (response.code == 404) {
                response.close()
                url = "${baseUrl.trimEnd('/')}/server/load.php?type=stb&action=handshake&type=itv"
                request = request.newBuilder().url(url).build()
                response = getClient().newCall(request).execute()
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

        suspend fun getLiveCategories(baseUrl: String, user: String, pass: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_categories"
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            getClient().newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val result = org.json.JSONArray()
                        val reader = android.util.JsonReader(java.io.BufferedReader(java.io.InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                    val obj = org.json.JSONObject()
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val name = reader.nextName()
                                        val token = reader.peek()
                                        when (token) {
                                            android.util.JsonToken.STRING -> obj.put(name, reader.nextString())
                                            android.util.JsonToken.NUMBER -> {
                                                try {
                                                    obj.put(name, reader.nextInt())
                                                } catch(e:Exception) {
                                                    obj.put(name, reader.nextDouble())
                                                }
                                            }
                                            android.util.JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                                            android.util.JsonToken.NULL -> { reader.nextNull(); obj.put(name, org.json.JSONObject.NULL) }
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getLiveStreams(baseUrl: String, user: String, pass: String, categoryId: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            val response = getClient().newCall(request).execute()
            if (response.code == 200) {
                val body = response.body?.string() ?: ""
                val allStreams = org.json.JSONArray(body)
                if (categoryId.isEmpty() || categoryId == "all") {
                    return@withContext allStreams
                }
                val filtered = org.json.JSONArray()
                for (i in 0 until allStreams.length()) {
                    val stream = allStreams.optJSONObject(i)
                    if (stream != null && stream.optString("category_id") == categoryId) {
                        filtered.put(stream)
                    }
                }
                return@withContext filtered
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getVodStreams(baseUrl: String, user: String, pass: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_vod_streams"
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            val response = getClient().newCall(request).execute()
            if (response.code == 200) {
                val body = response.body?.string() ?: ""
                return@withContext org.json.JSONArray(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getStreamCount(url: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            getClient().newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val reader = android.util.JsonReader(java.io.BufferedReader(java.io.InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.skipValue()
                                count++
                            }
                            reader.endArray()
                        }
                        reader.close()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore stream read errors
        }
        return@withContext count
    }

    suspend fun getLiveStreamCount(baseUrl: String, user: String, pass: String): Int {
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
        return getStreamCount(url)
    }

    suspend fun getVodStreamCount(baseUrl: String, user: String, pass: String): Int {
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_vod_streams"
        return getStreamCount(url)
    }

    suspend fun getAllLiveStreams(baseUrl: String, user: String, pass: String): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams"
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            getClient().newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body
                    if (body != null) {
                        val result = org.json.JSONArray()
                        val reader = android.util.JsonReader(java.io.BufferedReader(java.io.InputStreamReader(body.byteStream(), "UTF-8"), 32768))
                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                    val obj = org.json.JSONObject()
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val name = reader.nextName()
                                        val token = reader.peek()
                                        when (token) {
                                            android.util.JsonToken.STRING -> obj.put(name, reader.nextString())
                                            android.util.JsonToken.NUMBER -> {
                                                try {
                                                    obj.put(name, reader.nextInt())
                                                } catch(e:Exception) {
                                                    obj.put(name, reader.nextDouble())
                                                }
                                            }
                                            android.util.JsonToken.BOOLEAN -> obj.put(name, reader.nextBoolean())
                                            android.util.JsonToken.NULL -> { reader.nextNull(); obj.put(name, org.json.JSONObject.NULL) }
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
