package com.projectstrong.iptv.network

import com.projectstrong.iptv.data.DataStore
import java.util.regex.Pattern

data class ParsedCredential(
    val baseUrl: String,
    val user: String,
    val pass: String,
    val mac: String,
    val type: String,
    val status: String = "Pending Handshake",
    val details: String = "",
    val expires: String = "N/A",
    val daysLeft: String = "N/A",
    val activeConn: String = "N/A",
    val maxConn: String = "N/A",
    val channels: String = "N/A",
    val vods: String = "N/A",
    val serverTimezone: String = "N/A",
    val serverTime: String = "N/A",
    val provider: String = "Unknown",
    val isVerifying: Boolean = false,
    val sourceLink: String = "Direct Ingestion",
    val originLink: String = ""
)

object Parser {
    // Known non-IPTV domains that must never be captured as IPTV baseUrl
    private val BLACKLISTED_DOMAINS = setOf(
        "reddit.com", "www.reddit.com", "old.reddit.com", "redd.it", "v.redd.it", "i.redd.it",
        "pastebin.com", "www.pastebin.com", "paste.sh", "rentry.co", "rentry.org", "pastetext.net",
        "controlc.com", "justpaste.it", "ghostbin.com", "paste.ee", "hastebin.com",
        "t.me", "telegram.me", "telegram.org", "discord.gg", "discord.com", "discordapp.com",
        "github.com", "raw.githubusercontent.com", "gist.github.com", "gitlab.com",
        "twitter.com", "x.com", "facebook.com", "fb.com", "instagram.com", "tiktok.com",
        "youtube.com", "youtu.be", "google.com", "drive.google.com", "docs.google.com",
        "mega.nz", "mediafire.com", "dropbox.com", "t.co", "bit.ly", "tinyurl.com", "is.gd"
    )

    fun isBlacklistedHost(url: String): Boolean {
        return try {
            val uri = java.net.URI(if (!url.startsWith("http")) "http://$url" else url)
            val host = (uri.host ?: "").lowercase()
            if (host.isEmpty()) return true
            BLACKLISTED_DOMAINS.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun parseCredentials(
        textBlock: String,
        sourceLink: String = "Direct Ingestion",
        originLink: String = ""
    ): List<ParsedCredential> {
        if (textBlock.isBlank()) return emptyList()
        return try {
            val extracted = mutableListOf<ParsedCredential>()

            // Auto-discover Origin Link (Reddit/Forum/Social) if not explicitly set
            val originUrlPattern = Pattern.compile(
                "https?://(?:www\\.|old\\.|new\\.|np\\.)?(?:reddit\\.com/(?:r/[^\\s\"'<>]+|user/[^\\s\"'<>]+|comments/[^\\s\"'<>]+)|redd\\.it/[^\\s\"'<>]+|t\\.me/[^\\s\"'<>]+|telegram\\.me/[^\\s\"'<>]+|discord\\.gg/[^\\s\"'<>]+)",
                Pattern.CASE_INSENSITIVE
            )
            val effectiveOrigin = if (originLink.isNotBlank()) {
                originLink
            } else if (DataStore.scannerOriginLink.isNotBlank()) {
                DataStore.scannerOriginLink
            } else {
                val m = originUrlPattern.matcher(textBlock)
                if (m.find()) {
                    val found = m.group(0) ?: ""
                    if (found.isNotBlank() && DataStore.scannerOriginLink.isBlank()) {
                        DataStore.scannerOriginLink = found
                    }
                    found
                } else ""
            }

            // Auto-discover Source Link (Pastebin / Rentry / Paste.sh / etc) if not explicitly set
            val pastebinUrlPattern = Pattern.compile(
                "https?://(?:www\\.)?(?:pastebin\\.com/(?:raw/)?[a-zA-Z0-9]+|paste\\.sh/[a-zA-Z0-9#]+|rentry\\.(?:co|org)/(?:raw/)?[a-zA-Z0-9]+|pastetext\\.net/[a-zA-Z0-9]+|controlc\\.com/[a-zA-Z0-9]+|dpaste\\.(?:org|com)/[a-zA-Z0-9]+(?:\\.txt)?|paste\\.ee/(?:p|r)/[a-zA-Z0-9]+|gist\\.github\\.com/[^\\s\"'<>]+)",
                Pattern.CASE_INSENSITIVE
            )
            val effectiveSource = if (sourceLink.isNotBlank() && sourceLink != "Direct Ingestion") {
                sourceLink
            } else if (DataStore.scannerSourceLink.isNotBlank() && DataStore.scannerSourceLink != "Direct Ingestion") {
                DataStore.scannerSourceLink
            } else {
                val m = pastebinUrlPattern.matcher(textBlock)
                if (m.find()) {
                    val found = m.group(0) ?: "Direct Ingestion"
                    if (found != "Direct Ingestion" && (DataStore.scannerSourceLink.isBlank() || DataStore.scannerSourceLink == "Direct Ingestion")) {
                        DataStore.scannerSourceLink = found
                    }
                    found
                } else "Direct Ingestion"
            }
            
            // 1. Standard Xtream API patterns
            try {
                val patternXtream = Pattern.compile("(https?://[^/:]+(?::\\d+)?)/(?:player_api|get)\\.php\\?username=([^&\\s]+)&password=([^&\\s]+)")
                val matcherXtream = patternXtream.matcher(textBlock)
                while (matcherXtream.find()) {
                    val baseUrl = matcherXtream.group(1) ?: ""
                    val user = matcherXtream.group(2) ?: ""
                    val pass = matcherXtream.group(3) ?: ""
                    if (baseUrl.isNotEmpty() && !isBlacklistedHost(baseUrl) && user.isNotEmpty() && !extracted.any { it.baseUrl == baseUrl && it.user == user }) {
                        extracted.add(ParsedCredential(baseUrl, user, pass, "", "Xtream", sourceLink = effectiveSource, originLink = effectiveOrigin))
                    }
                }
            } catch (e: Throwable) {}

            // 2. Line-by-line Tabular & Formatted Combos
            val tabPattern = Pattern.compile("^((?:https?://)?[^\\s/:]+(?::\\d+)?(?:/[^\\s:]*)?)\\s+([^\\s:]+)\\s*:\\s*([^\\s]+)(?:\\s+(.*))?$")
            val comboPattern = Pattern.compile("^((?:https?://)?[^\\s/:]+(?::\\d+)?(?:/[^\\s:]*)?)[\\s:]([^\\s:]+)[\\s:]([^\\s:]+)$")
            val macRegex = Regex("^[0-9a-fA-F]{2}$")
            val macFullRegex = Regex("^(?:[0-9a-fA-F]{2}:){4}[0-9a-fA-F]{2}$")
            val skipKeywords = setOf("mac", "active", "activa", "expired", "http", "https", "user", "pass", "username", "password", "ᴜꜱᴇʀ", "ᴩᴀꜱꜱ")
            
            val tzPattern = Pattern.compile("\\b([A-Z]{3,4}|GMT[+-]\\d+)\\b")
            val connPattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")
            val datePattern = Pattern.compile("(\\d{1,2}\\s+[a-zA-Z]{3,}\\s+(?:de\\s+)?\\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})")

            for (line in textBlock.lines()) {
                val lineClean = line.trim()
                if (lineClean.isEmpty() || lineClean.length > 800 || lineClean.contains("player_api.php") || lineClean.contains("get.php")) {
                    continue
                }

                try {
                    // Tabular match (e.g. Host \t User : Pass \t ...)
                    val tabMatcher = tabPattern.matcher(lineClean)
                    if (tabMatcher.matches()) {
                        var baseUrl = tabMatcher.group(1) ?: ""
                        if (!baseUrl.startsWith("http")) {
                            baseUrl = "http://$baseUrl"
                        }
                        if (isBlacklistedHost(baseUrl)) continue

                        val user = tabMatcher.group(2) ?: ""
                        val pass = tabMatcher.group(3) ?: ""
                        val rest = tabMatcher.group(4) ?: ""

                        if (user.matches(macRegex) && pass.matches(macFullRegex)) {
                            continue
                        }
                        if (skipKeywords.contains(user.lowercase()) || skipKeywords.contains(pass.lowercase())) {
                            continue
                        }

                        if (!extracted.any { it.baseUrl == baseUrl && it.user == user }) {
                            var tz = "N/A"
                            var act = "N/A"
                            var max = "N/A"
                            var exp = "N/A"

                            val tzMatcher = tzPattern.matcher(rest)
                            if (tzMatcher.find()) {
                                tz = tzMatcher.group(1) ?: "N/A"
                            }

                            val connMatcher = connPattern.matcher(rest)
                            if (connMatcher.find()) {
                                act = connMatcher.group(1) ?: "N/A"
                                max = connMatcher.group(2) ?: "N/A"
                            }

                            val dateMatcher = datePattern.matcher(rest)
                            var lastDate: String? = null
                            while (dateMatcher.find()) {
                                lastDate = dateMatcher.group(1)
                            }
                            if (lastDate != null) {
                                exp = lastDate
                            }

                            extracted.add(
                                ParsedCredential(
                                    baseUrl = baseUrl,
                                    user = user,
                                    pass = pass,
                                    mac = "",
                                    type = "Xtream",
                                    serverTimezone = tz,
                                    activeConn = act,
                                    maxConn = max,
                                    expires = exp,
                                    sourceLink = effectiveSource,
                                    originLink = effectiveOrigin
                                )
                            )
                            continue
                        }
                    }

                    // Standard 4-part Colon or Space combo
                    val comboMatcher = comboPattern.matcher(lineClean)
                    if (comboMatcher.matches()) {
                        var baseUrl = comboMatcher.group(1) ?: ""
                        if (!baseUrl.startsWith("http")) {
                            baseUrl = "http://$baseUrl"
                        }
                        if (isBlacklistedHost(baseUrl)) continue

                        val user = comboMatcher.group(2) ?: ""
                        val pass = comboMatcher.group(3) ?: ""

                        if (user.matches(macRegex) && pass.matches(macFullRegex)) {
                            continue
                        }
                        if (skipKeywords.contains(user.lowercase()) || skipKeywords.contains(pass.lowercase())) {
                            continue
                        }

                        if (!extracted.any { it.baseUrl == baseUrl && it.user == user }) {
                            extracted.add(ParsedCredential(baseUrl, user, pass, "", "Xtream", sourceLink = effectiveSource, originLink = effectiveOrigin))
                            continue
                        }
                    }
                } catch (e: Throwable) {}
            }

            // 3. Multi-line and Stalker Free-Text State Machine
            var currentUrl: String? = null
            var currentMac: String? = null
            var xtUser: String? = null
            var xtPass: String? = null
            
            val urlExtractPattern = Pattern.compile("(https?://[^/\\s]+(?:/[^/\\s]*)?)")
            val baseExtractPattern = Pattern.compile("(https?://[^/:]+(?::\\d+)?)")
            val macExtractPattern = Pattern.compile("([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})", Pattern.CASE_INSENSITIVE)
            val userExtractPattern = Pattern.compile("(?i)(?:user|usr|username|ᴜꜱᴇʀ)[\\s:=]+([^\\s]+)")
            val passExtractPattern = Pattern.compile("(?i)(?:pass|password|ᴩᴀꜱꜱ)[\\s:=]+([^\\s]+)")
            val resetSeparatorRegex = Regex("[-=_*#]{4,}|╰─|╭─|┌─|└─|\\|")

            for (line in textBlock.lines()) {
                val lineTrim = line.trim()
                if (lineTrim.isEmpty() || lineTrim.length > 800 || lineTrim.contains(resetSeparatorRegex) || lineTrim.contains("player_api.php") || lineTrim.contains("get.php")) {
                    currentUrl = null
                    currentMac = null
                    xtUser = null
                    xtPass = null
                    continue
                }
                
                try {
                    val urlMatch = urlExtractPattern.matcher(lineTrim)
                    if (urlMatch.find()) {
                        val baseMatch = baseExtractPattern.matcher(urlMatch.group(1) ?: "")
                        if (baseMatch.find()) {
                            val candidate = baseMatch.group(1) ?: ""
                            if (candidate.isNotEmpty() && !isBlacklistedHost(candidate)) {
                                currentUrl = candidate
                            }
                        }
                    }
                    
                    val macMatch = macExtractPattern.matcher(lineTrim)
                    if (macMatch.find()) {
                        currentMac = macMatch.group(1)?.uppercase()
                    }
                    
                    val userMatch = userExtractPattern.matcher(lineTrim)
                    if (userMatch.find()) {
                        xtUser = userMatch.group(1)
                    }
                    
                    val passMatch = passExtractPattern.matcher(lineTrim)
                    if (passMatch.find()) {
                        xtPass = passMatch.group(1)
                    }
                    
                    if (currentUrl != null && currentMac != null && !isBlacklistedHost(currentUrl)) {
                        if (!extracted.any { it.type == "Stalker" && it.baseUrl == currentUrl && it.mac == currentMac }) {
                            extracted.add(ParsedCredential(currentUrl, currentMac, "MAC", currentMac, "Stalker", sourceLink = effectiveSource, originLink = effectiveOrigin))
                        }
                        currentMac = null
                    }
                    
                    if (currentUrl != null && xtUser != null && xtPass != null && !isBlacklistedHost(currentUrl)) {
                        if (!(xtUser.matches(macRegex) && xtPass.matches(macFullRegex))) {
                            if (!extracted.any { it.type == "Xtream" && it.baseUrl == currentUrl && it.user == xtUser }) {
                                extracted.add(ParsedCredential(currentUrl, xtUser, xtPass, "", "Xtream", sourceLink = effectiveSource, originLink = effectiveOrigin))
                            }
                        }
                        xtUser = null
                        xtPass = null
                    }
                } catch (e: Throwable) {}
            }
            extracted
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
