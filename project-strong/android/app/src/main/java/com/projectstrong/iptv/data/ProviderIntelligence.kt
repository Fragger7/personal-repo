package com.projectstrong.iptv.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ProviderProfile(
    @SerializedName("domain") val domain: String = "",
    @SerializedName("provider_name") val providerName: String = "",
    @SerializedName("server") val server: String? = null,
    @SerializedName("cloudflare") val cloudflare: String? = "No",
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("metadata_message") val metadataMessage: String? = null,
    @SerializedName("server_protocol") val serverProtocol: String? = null,
    @SerializedName("https_port") val httpsPort: String? = null,
    @SerializedName("rtmp_port") val rtmpPort: String? = null,
    @SerializedName("allowed_formats") val allowedFormats: String? = null,
    @SerializedName("community_link") val communityLink: String? = null,
    @SerializedName("confidence") val confidence: String? = null,
    @SerializedName("evidence") val evidence: String? = null,
    @SerializedName("first_seen") val firstSeen: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
) {
    val cleanBrand: String
        get() {
            if (providerName.startsWith("🎯 Identified: ")) {
                return providerName.removePrefix("🎯 Identified: ").trim()
            }
            if (providerName.startsWith("👤 Host: ")) {
                return "Unbranded Node"
            }
            if (providerName.isNotBlank() && !providerName.startsWith("Host:")) {
                return providerName
            }
            return "Unbranded Node"
        }

    val isIdentified: Boolean
        get() = providerName.startsWith("🎯") || (!confidence.isNullOrBlank() && confidence != "Generic")

    val safeServer get() = server ?: "Unknown"
    val safeCloudflare get() = cloudflare ?: "No"
    val safeTimezone get() = timezone ?: "UTC"
    val safeCommunityLink get() = communityLink ?: ""
    val safeConfidence get() = confidence ?: if (isIdentified) "High Confidence" else "Server Fingerprint"
    val safeEvidence get() = evidence ?: ""
}

object ProviderIntelligenceManager {
    val profiles = mutableStateMapOf<String, ProviderProfile>()
    private lateinit var file: File
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Known Upstream IPTV Provider Signatures
    private val KNOWN_PROVIDERS = listOf(
        Pair("Strong 8K", listOf("strong 8k", "strong8k", "strong-8k", "strong ott", "strong8k.vip")),
        Pair("T-Rex OTT", listOf("t-rex", "trex", "trex iptv", "trexiptv", "trextv")),
        Pair("Dream 4K", listOf("dream 4k", "dream4k", "dream ott", "dream-4k")),
        Pair("B1G OTT", listOf("b1g", "b1g ott", "b1g iptv", "b1g live")),
        Pair("Crystal OTT", listOf("crystal ott", "crystal iptv", "crystal-ott")),
        Pair("Cobra IPTV", listOf("cobra iptv", "cobra 4k", "cobra-iptv", "cobra ott")),
        Pair("Mega OTT", listOf("mega ott", "mega iptv", "mega-ott", "megaiptv")),
        Pair("Dino OTT", listOf("dino ott", "dino.ws", "dino iptv", "dino-ott")),
        Pair("4K OTT", listOf("4kott", "4k-ott", "4k ott live", "tx-4kott")),
        Pair("Apollo Group TV", listOf("apollo group", "apollogroup", "apollo iptv")),
        Pair("Xtreme HD", listOf("xtreme hd", "xtremehd", "xtreme-hd")),
        Pair("King 4K", listOf("king 4k", "king4k", "king-4k", "king ott")),
        Pair("Nanotech", listOf("nanotech", "nanotech copyright")),
        Pair("Pluto TV Gateway", listOf("pluto.png", "italy/pluto")),
        Pair("Lion OTT", listOf("lion-ott", "lion ott", "tvlion")),
        Pair("Exclusive OTT", listOf("welcome to exclusive", "exclusive iptv"))
    )

    private val GENERIC_BLACKLIST = setOf(
        "vip", "vod", "series", "movies", "live", "channels", "channel",
        "sport", "sports", "kids", "news", "catchup", "all", "xxx", "adult",
        "4k", "fhd", "hd", "hevc", "sd", "h265", "raw", "premium", "ultra",
        "usa", "uk", "latino", "arabic", "france", "italy", "germany",
        "spain", "turkey", "canada", "brazil", "world", "general", "tv",
        "cinema", "documentary", "music", "radio", "entertainment"
    )

    fun init(context: Context) {
        file = File(context.filesDir, "provider_intelligence.json")
        loadLocal()
        syncFromCloudAsync()
    }

    fun extractDomain(rawUrl: String): String {
        return try {
            val clean = rawUrl.trim().trimEnd('/')
            val uri = if (clean.startsWith("http://") || clean.startsWith("https://")) {
                URI(clean)
            } else {
                URI("http://$clean")
            }
            val host = uri.host ?: clean
            val port = uri.port
            if (port != -1 && port != 80 && port != 443) {
                "$host:$port"
            } else {
                host
            }
        } catch (e: Exception) {
            rawUrl.replace("http://", "").replace("https://", "").trimEnd('/')
        }
    }

    private fun loadLocal() {
        if (!file.exists()) return
        try {
            val json = file.readText()
            if (json.isNotBlank()) {
                val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
                val map: Map<String, Map<String, Any?>> = gson.fromJson(json, type)
                profiles.clear()
                map.forEach { (domain, values) ->
                    val profile = ProviderProfile(
                        domain = domain,
                        providerName = values["provider_name"]?.toString() ?: "👤 Host: $domain",
                        server = values["server"]?.toString(),
                        cloudflare = values["cloudflare"]?.toString() ?: "No",
                        timezone = values["timezone"]?.toString(),
                        metadataMessage = values["metadata_message"]?.toString(),
                        serverProtocol = values["server_protocol"]?.toString(),
                        httpsPort = values["https_port"]?.toString(),
                        rtmpPort = values["rtmp_port"]?.toString(),
                        allowedFormats = values["allowed_formats"]?.toString(),
                        communityLink = values["community_link"]?.toString(),
                        confidence = values["confidence"]?.toString(),
                        evidence = values["evidence"]?.toString(),
                        firstSeen = values["first_seen"]?.toString(),
                        lastSeen = values["last_seen"]?.toString()
                    )
                    profiles[domain] = profile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLocal() {
        try {
            val exportMap = mutableMapOf<String, Any>()
            profiles.forEach { (domain, p) ->
                val item = mutableMapOf<String, Any?>()
                item["provider_name"] = p.providerName
                if (p.server != null) item["server"] = p.server
                if (p.cloudflare != null) item["cloudflare"] = p.cloudflare
                if (p.timezone != null) item["timezone"] = p.timezone
                if (p.metadataMessage != null) item["metadata_message"] = p.metadataMessage
                if (p.serverProtocol != null) item["server_protocol"] = p.serverProtocol
                if (p.httpsPort != null) item["https_port"] = p.httpsPort
                if (p.rtmpPort != null) item["rtmp_port"] = p.rtmpPort
                if (p.allowedFormats != null) item["allowed_formats"] = p.allowedFormats
                if (p.communityLink != null) item["community_link"] = p.communityLink
                if (p.confidence != null) item["confidence"] = p.confidence
                if (p.evidence != null) item["evidence"] = p.evidence
                if (p.firstSeen != null) item["first_seen"] = p.firstSeen
                if (p.lastSeen != null) item["last_seen"] = p.lastSeen
                exportMap[domain] = item
            }
            val json = gson.toJson(exportMap)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProfile(baseUrlOrDomain: String): ProviderProfile? {
        val domain = extractDomain(baseUrlOrDomain)
        return profiles[domain] ?: profiles[domain.substringBefore(':')]
    }

    /**
     * Updates profile based on HTTP Handshake headers and server_info JSON
     */
    fun updateFromFingerprint(
        baseUrl: String,
        serverHeader: String? = null,
        isCloudflare: Boolean = false,
        serverInfo: JSONObject? = null
    ): ProviderProfile {
        val domain = extractDomain(baseUrl)
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val existing = profiles[domain] ?: ProviderProfile(
            domain = domain,
            providerName = "👤 Host: $domain",
            firstSeen = nowStr
        )

        val serverSoftware = serverHeader ?: serverInfo?.optString("server_name") ?: existing.server
        val cfStatus = if (isCloudflare) "Yes" else existing.cloudflare ?: "No"
        val tz = serverInfo?.optString("timezone") ?: existing.timezone
        val msg = serverInfo?.optString("message") ?: existing.metadataMessage
        val proto = serverInfo?.optString("server_protocol") ?: existing.serverProtocol
        val httpsP = serverInfo?.optString("https_port") ?: existing.httpsPort
        val rtmpP = serverInfo?.optString("rtmp_port") ?: existing.rtmpPort
        val formats = serverInfo?.optJSONArray("allowed_output_formats")?.toString() ?: existing.allowedFormats

        var newProviderName = existing.providerName
        var confidence = existing.confidence ?: "Server Fingerprint"
        var evidence = existing.evidence

        // Check for recognized server signature
        if (!newProviderName.startsWith("🎯") && serverSoftware != null && serverSoftware.isNotBlank() &&
            serverSoftware != "Unknown" && serverSoftware != "nginx" && serverSoftware != "Apache"
        ) {
            newProviderName = "🎯 Identified: $serverSoftware"
            confidence = "Server Software Signature"
            evidence = "Identified via HTTP Server Header: $serverSoftware"
        }

        // Check if welcome message contains provider identity
        if (!newProviderName.startsWith("🎯") && !msg.isNullOrBlank() && msg.length > 5 && !msg.equals("Welcome", ignoreCase = true)) {
            KNOWN_PROVIDERS.forEach { (brandName, triggers) ->
                if (triggers.any { msg.contains(it, ignoreCase = true) }) {
                    newProviderName = "🎯 Identified: $brandName"
                    confidence = "High Confidence (95%)"
                    evidence = "Matched Server Metadata Message: \"$msg\""
                }
            }
        }

        val updated = existing.copy(
            server = serverSoftware,
            cloudflare = cfStatus,
            timezone = tz,
            metadataMessage = msg,
            serverProtocol = proto,
            httpsPort = httpsP,
            rtmpPort = rtmpP,
            allowedFormats = formats,
            providerName = newProviderName,
            confidence = confidence,
            evidence = evidence,
            lastSeen = nowStr
        )

        profiles[domain] = updated
        scope.launch { saveLocal() }
        return updated
    }

    /**
     * Deep-mines channel names and categories for community links (Telegram, Discord) and banner watermarks.
     */
    fun mineFromStreams(
        baseUrl: String,
        categoriesData: List<com.projectstrong.iptv.ui.components.CategoryItem>?,
        streamsData: List<com.projectstrong.iptv.ui.components.ChannelItem>?
    ): ProviderProfile? {
        val domain = extractDomain(baseUrl)
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val existing = profiles[domain] ?: ProviderProfile(
            domain = domain,
            providerName = "👤 Host: $domain",
            firstSeen = nowStr
        )

        val brandScores = mutableMapOf<String, Int>()
        var foundCommunityLink: String? = existing.communityLink
        var detectedEvidence: String? = existing.evidence

        val contactRegex = Pattern.compile("(t\\.me/[a-zA-Z0-9_]+|discord\\.gg/[a-zA-Z0-9_]+|wa\\.me/\\d+|https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/[a-zA-Z0-9_.-]*)", Pattern.CASE_INSENSITIVE)
        val bannerRegex = Pattern.compile("^[#=~\\|\\*\\-\\+]{2,}\\s*(.+?)\\s*[#=~\\|\\*\\-\\+]{2,}$")
        val prefixRegex = Pattern.compile("^\\[([a-zA-Z0-9\\s\\-_.]+)\\]")

        fun analyzeText(text: String?) {
            if (text.isNullOrBlank()) return
            val clean = text.trim()

            // 1. Community Links
            val contactMatcher = contactRegex.matcher(clean)
            while (contactMatcher.find()) {
                val link = contactMatcher.group(1) ?: ""
                if (link.isNotBlank() && !link.contains("w3.org", ignoreCase = true) && !link.contains("schema.org", ignoreCase = true)) {
                    foundCommunityLink = link
                    detectedEvidence = "Found official link: $link in \"$clean\""
                }
            }

            // 2. Known Providers Fast-Match
            KNOWN_PROVIDERS.forEach { (brandName, triggers) ->
                if (triggers.any { clean.contains(it, ignoreCase = true) }) {
                    brandScores[brandName] = (brandScores[brandName] ?: 0) + 20
                    if (detectedEvidence.isNullOrBlank()) {
                        detectedEvidence = "Detected brand pattern \"$brandName\" in \"$clean\""
                    }
                }
            }

            // 3. Decorated Dummy Channel Watermarks (e.g. ### Strong 8K VIP ###)
            val bannerMatcher = bannerRegex.matcher(clean)
            if (bannerMatcher.find()) {
                val candidate = bannerMatcher.group(1)?.trim() ?: ""
                val lowerCandidate = candidate.lowercase(Locale.ROOT)
                if (candidate.length in 4..35 && !GENERIC_BLACKLIST.contains(lowerCandidate) &&
                    !candidate.all { !it.isLetterOrDigit() }
                ) {
                    brandScores[candidate] = (brandScores[candidate] ?: 0) + 12
                    detectedEvidence = "Found watermark banner: \"$clean\""
                }
            }

            // 4. Bracketed Prefix (e.g. [Strong 8K] US | HBO)
            val prefixMatcher = prefixRegex.matcher(clean)
            if (prefixMatcher.find()) {
                val p = prefixMatcher.group(1)?.trim() ?: ""
                val lowerP = p.lowercase(Locale.ROOT)
                if (p.length in 3..25 && !GENERIC_BLACKLIST.contains(lowerP) && !p.all { !it.isLetterOrDigit() }) {
                    brandScores[p] = (brandScores[p] ?: 0) + 3
                }
            }
        }

        categoriesData?.forEach { analyzeText(it.name) }
        streamsData?.take(300)?.forEach { analyzeText(it.name) }

        if (brandScores.isNotEmpty()) {
            val bestCandidate = brandScores.maxByOrNull { it.value }
            if (bestCandidate != null && bestCandidate.value >= 6) {
                val bestBrand = bestCandidate.key
                val confidence = if (bestCandidate.value >= 20 || foundCommunityLink != null) {
                    "Verified Brand (95%)"
                } else {
                    "Probable Match (75%)"
                }

                val updated = existing.copy(
                    providerName = "🎯 Identified: $bestBrand",
                    communityLink = foundCommunityLink,
                    confidence = confidence,
                    evidence = detectedEvidence ?: "Discovered via recurring stream watermarks",
                    lastSeen = nowStr
                )
                profiles[domain] = updated
                scope.launch { saveLocal() }
                return updated
            }
        }

        if (foundCommunityLink != null && foundCommunityLink != existing.communityLink) {
            val updated = existing.copy(
                communityLink = foundCommunityLink,
                evidence = detectedEvidence,
                lastSeen = nowStr
            )
            profiles[domain] = updated
            scope.launch { saveLocal() }
            return updated
        }

        return profiles[domain]
    }

    private fun syncFromCloudAsync() {
        scope.launch {
            try {
                val url = java.net.URL("https://api.github.com/repos/Fragger7/personal-repo/contents/project-strong/provider_intelligence.json")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (DataStore.githubToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "token ${DataStore.githubToken}")
                }
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                if (conn.responseCode == 200) {
                    val jsonResponse = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = org.json.JSONObject(jsonResponse)
                    val contentB64 = jsonObj.optString("content", "").replace("\n", "")
                    if (contentB64.isNotEmpty()) {
                        val decoded = android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
                        val json = String(decoded, Charsets.UTF_8)
                        val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
                        val remoteMap: Map<String, Map<String, Any?>> = gson.fromJson(json, type)

                        withContext(Dispatchers.Main) {
                            remoteMap.forEach { (domain, values) ->
                                if (!profiles.containsKey(domain) || !profiles[domain]!!.isIdentified) {
                                    val profile = ProviderProfile(
                                        domain = domain,
                                        providerName = values["provider_name"]?.toString() ?: "👤 Host: $domain",
                                        server = values["server"]?.toString(),
                                        cloudflare = values["cloudflare"]?.toString() ?: "No",
                                        timezone = values["timezone"]?.toString(),
                                        metadataMessage = values["metadata_message"]?.toString(),
                                        serverProtocol = values["server_protocol"]?.toString(),
                                        httpsPort = values["https_port"]?.toString(),
                                        rtmpPort = values["rtmp_port"]?.toString(),
                                        allowedFormats = values["allowed_formats"]?.toString(),
                                        communityLink = values["community_link"]?.toString(),
                                        confidence = values["confidence"]?.toString(),
                                        evidence = values["evidence"]?.toString(),
                                        firstSeen = values["first_seen"]?.toString(),
                                        lastSeen = values["last_seen"]?.toString()
                                    )
                                    profiles[domain] = profile
                                }
                            }
                        }
                        saveLocal()
                    }
                }
            } catch (e: Exception) {
                // Non-critical background sync
            }
        }
    }
}
