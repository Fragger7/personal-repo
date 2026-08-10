package com.projectstrong.iptv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun verifyXtream(baseUrl: String, user: String, pass: String): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val encodedUser = URLEncoder.encode(user, "UTF-8")
            val encodedPass = URLEncoder.encode(pass, "UTF-8")
            var url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass"
            var request = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVSmartersPro")
                .build()

            var response = client.newCall(request).execute()
            if (response.code == 404) {
                response.close()
                url = "${baseUrl.trimEnd('/')}/server/load.php?type=stb&action=handshake&type=itv"
                request = request.newBuilder().url(url).build()
                response = client.newCall(request).execute()
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

            var response = client.newCall(request).execute()
            if (response.code == 404) {
                response.close()
                url = "${baseUrl.trimEnd('/')}/server/load.php?type=stb&action=handshake&type=itv"
                request = request.newBuilder().url(url).build()
                response = client.newCall(request).execute()
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
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val body = response.body?.string() ?: ""
                return@withContext org.json.JSONArray(body)
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
            val url = "${baseUrl.trimEnd('/')}/player_api.php?username=$encodedUser&password=$encodedPass&action=get_live_streams&category_id=$categoryId"
            val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val body = response.body?.string() ?: ""
                return@withContext org.json.JSONArray(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
