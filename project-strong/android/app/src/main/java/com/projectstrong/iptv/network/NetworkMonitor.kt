package com.projectstrong.iptv.network

import com.projectstrong.iptv.data.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkMonitor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun refreshNetworkState(): Boolean = withContext(Dispatchers.IO) {
        DataStore.isCheckingNetwork = true
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json/")
                .header("User-Agent", "Mozilla/5.0 (Android; SherlockStreams)")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)

            val status = json.optString("status", "")
            if (status == "success") {
                val ip = json.optString("query", "Unknown IP")
                val isp = json.optString("isp", "Direct ISP")
                val org = json.optString("org", "")
                val country = json.optString("country", "")

                DataStore.detectedIp = ip
                DataStore.detectedIsp = isp
                DataStore.detectedOrg = org
                DataStore.detectedCountry = country
                DataStore.ipInfo = "Connected via $isp"

                val clouds = listOf("amazon", "aws", "google", "azure", "cloudflare", "digitalocean", "linode", "hetzner", "ovh", "vultr", "leaseweb", "choopa", "kamatera")
                val isCloud = clouds.any { isp.lowercase().contains(it) || org.lowercase().contains(it) }
                DataStore.isCloudHosting = isCloud
                DataStore.showVpnWarning = isCloud
                DataStore.isCheckingNetwork = false
                return@withContext true
            } else {
                DataStore.ipInfo = "DISCONNECTED / UNKNOWN"
                DataStore.detectedIp = ""
                DataStore.detectedIsp = "Offline"
                DataStore.isCloudHosting = false
                DataStore.isCheckingNetwork = false
                return@withContext false
            }
        } catch (e: Exception) {
            DataStore.ipInfo = "DISCONNECTED / UNKNOWN"
            DataStore.detectedIp = ""
            DataStore.detectedIsp = "Offline"
            DataStore.isCloudHosting = false
            DataStore.isCheckingNetwork = false
            return@withContext false
        }
    }
}
