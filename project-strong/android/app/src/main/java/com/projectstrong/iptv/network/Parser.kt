package com.projectstrong.iptv.network

import com.projectstrong.iptv.data.DataStore
import java.text.Normalizer
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
    val originLink: String = "",
    val egressStatus: String = "Unchecked",
    val egressDetails: String = ""
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

    private val SMALL_CAPS_MAP = mapOf(
        'ᴜ' to 'u', 'ꜱ' to 's', 'ᴇ' to 'e', 'ʀ' to 'r', 'ᴩ' to 'p', 'ᴀ' to 'a',
        'ʜ' to 'h', 'ᴏ' to 'o', 'ᴛ' to 't', 'ᴍ' to 'm', 'ᴄ' to 'c', 'ᴅ' to 'd',
        'ɪ' to 'i', 'ᴊ' to 'j', 'ᴋ' to 'k', 'ʟ' to 'l', 'ɴ' to 'n', 'ɢ' to 'g',
        'ᴠ' to 'v', 'ᴡ' to 'w', 'ʏ' to 'y', 'ᴢ' to 'z', 'ʙ' to 'b', 'ꜰ' to 'f'
    )

    private val originUrlPattern = Pattern.compile(
        "https?://(?:www\\.|old\\.|new\\.|np\\.)?(?:reddit\\.com/(?:r/[^\\s\"'<>]+|user/[^\\s\"'<>]+|comments/[^\\s\"'<>]+)|redd\\.it/[^\\s\"'<>]+|t\\.me/[^\\s\"'<>]+|telegram\\.me/[^\\s\"'<>]+|discord\\.gg/[^\\s\"'<>]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val pastebinUrlPattern = Pattern.compile(
        "https?://(?:www\\.)?(?:pastebin\\.com/(?:raw/)?[a-zA-Z0-9]+|paste\\.sh/[a-zA-Z0-9#]+|rentry\\.(?:co|org)/(?:raw/)?[a-zA-Z0-9]+|pastetext\\.net/[a-zA-Z0-9]+|controlc\\.com/[a-zA-Z0-9]+|dpaste\\.(?:org|com)/[a-zA-Z0-9]+(?:\\.txt)?|paste\\.ee/(?:p|r)/[a-zA-Z0-9]+|gist\\.github\\.com/[^\\s\"'<>]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val patternXtream = Pattern.compile("(https?://[^/:]+(?::\\d+)?)/(?:player_api|get)\\.php\\?username=([^&\\s]+)&password=([^&\\s]+)")
    private val tabPattern = Pattern.compile("^((?:https?://)?[^\\s/:]+(?::\\d+)?(?:/[^\\s:]*)?)\\s+([^\\s:]+)\\s*:\\s*([^\\s]+)(?:\\s+(.*))?$")
    private val comboPattern = Pattern.compile("^((?:https?://)?[^\\s/:]+(?::\\d+)?(?:/[^\\s:]*)?)[\\s:]([^\\s:]+)[\\s:]([^\\s:]+)$")
    
    private val macRegex = Regex("^[0-9a-fA-F]{2}$")
    private val macFullRegex = Regex("^(?:[0-9a-fA-F]{2}:){4}[0-9a-fA-F]{2}$")
    private val skipKeywords = setOf("mac", "active", "activa", "expired", "http", "https", "user", "pass", "username", "password")
    
    private val tzPattern = Pattern.compile("\\b([A-Z]{3,4}|GMT[+-]\\d+)\\b")
    private val connPattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")
    private val datePattern = Pattern.compile("(\\d{1,2}\\s+[a-zA-Z]{3,}\\s+(?:de\\s+)?\\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})")

    private val urlExtractPattern = Pattern.compile("(https?://[^/\\s]+(?:/[^/\\s]*)?)")
    private val baseExtractPattern = Pattern.compile("(https?://[^/:]+(?::\\d+)?)")
    private val portalHeaderPattern = Pattern.compile("(?i)^(?:portal|host|server|url|domain)[\\s:=]+(?:https?://|www\\.|[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")
    private val portExtractPattern = Pattern.compile("(?i)\\bport[\\s:=]+(\\d{2,5})\\b")
    private val macExtractPattern = Pattern.compile("([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})", Pattern.CASE_INSENSITIVE)
    private val userExtractPattern = Pattern.compile("(?i)(?:user|usr|username)[\\s:=]+([^\\s]+)")
    private val passExtractPattern = Pattern.compile("(?i)(?:pass|password)[\\s:=]+([^\\s]+)")
    private val expExtractPattern = Pattern.compile("(?i)(?:exp|expire|expires|expiry)[\\s:=]+([^\\s]+)")
    private val singleConnExtractPattern = Pattern.compile("(?i)\\b(?:conn|active)[\\s:=]+(\\d+)\\b")
    private val singleMaxConnExtractPattern = Pattern.compile("(?i)\\b(?:maxconn|max_conn|max)[\\s:=]+(\\d+)\\b")
    private val statusEndPattern = Pattern.compile("(?i)status[\\s:=]+.*(?:ok|active|valid|✅)")
    private val resetSeparatorRegex = Regex("[-=_*#]{4,}|[━─╭╰┌└\\|]{2,}")

    fun normalizeText(raw: String): String {
        if (raw.isEmpty()) return raw
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            val mapped = SMALL_CAPS_MAP[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

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

    fun parseCredentials(
        textBlock: String,
        sourceLink: String = "Direct Ingestion",
        originLink: String = ""
    ): List<ParsedCredential> {
        if (textBlock.isBlank()) return emptyList()
        return try {
            val cleanText = normalizeText(textBlock)
            val extracted = mutableListOf<ParsedCredential>()

            // Auto-discover Origin Link (Reddit/Forum/Social) if not explicitly set
            val effectiveOrigin = if (originLink.isNotBlank()) {
                originLink
            } else if (DataStore.scannerOriginLink.isNotBlank()) {
                DataStore.scannerOriginLink
            } else {
                val m = originUrlPattern.matcher(cleanText)
                if (m.find()) {
                    val found = m.group(0) ?: ""
                    if (found.isNotBlank() && DataStore.scannerOriginLink.isBlank()) {
                        DataStore.scannerOriginLink = found
                    }
                    found
                } else ""
            }

            // Auto-discover Source Link (Pastebin / Rentry / Paste.sh / etc) if not explicitly set
            val effectiveSource = if (sourceLink.isNotBlank() && sourceLink != "Direct Ingestion") {
                sourceLink
            } else if (DataStore.scannerSourceLink.isNotBlank() && DataStore.scannerSourceLink != "Direct Ingestion") {
                DataStore.scannerSourceLink
            } else {
                val m = pastebinUrlPattern.matcher(cleanText)
                if (m.find()) {
                    val found = m.group(0) ?: "Direct Ingestion"
                    if (found != "Direct Ingestion" && (DataStore.scannerSourceLink.isBlank() || DataStore.scannerSourceLink == "Direct Ingestion")) {
                        DataStore.scannerSourceLink = found
                    }
                    found
                } else "Direct Ingestion"
            }
            
            // 1. Standard Xtream API player_api URL patterns
            try {
                val matcherXtream = patternXtream.matcher(cleanText)
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
            for (line in cleanText.lines()) {
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

            // 3. Multi-line Block & Stalker/Xtream State Machine (with Port & Unicode Support)
            var currUrl: String? = null
            var currPort: String? = null
            var currUser: String? = null
            var currPass: String? = null
            var currMac: String? = null
            var currExp: String? = null
            var currAct: String? = null
            var currMax: String? = null

            fun flushCurrentBlock() {
                val u = currUrl?.trim() ?: return
                var base = if (!u.startsWith("http")) "http://$u" else u
                
                // If a separate port was declared and base doesn't already have one
                val port = currPort?.trim()
                if (!port.isNullOrEmpty()) {
                    val scheme = if (base.startsWith("https://")) "https://" else "http://"
                    val afterScheme = base.removePrefix(scheme)
                    val hostPart = afterScheme.substringBefore("/")
                    if (!hostPart.contains(":")) {
                        val path = if (afterScheme.contains("/")) "/" + afterScheme.substringAfter("/") else ""
                        base = "$scheme$hostPart:$port$path"
                    }
                }
                
                base = base.trimEnd('/')
                if (isBlacklistedHost(base)) {
                    currUrl = null
                    currPort = null
                    currUser = null
                    currPass = null
                    currMac = null
                    currExp = null
                    currAct = null
                    currMax = null
                    return
                }

                val mac = currMac
                val user = currUser
                val pass = currPass

                if (!mac.isNullOrEmpty()) {
                    if (!extracted.any { it.type == "Stalker" && it.baseUrl == base && it.mac == mac }) {
                        extracted.add(
                            ParsedCredential(
                                baseUrl = base,
                                user = mac,
                                pass = "MAC",
                                mac = mac,
                                type = "Stalker",
                                expires = currExp ?: "N/A",
                                activeConn = currAct ?: "N/A",
                                maxConn = currMax ?: "N/A",
                                sourceLink = effectiveSource,
                                originLink = effectiveOrigin
                            )
                        )
                    }
                } else if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                    if (!(user.matches(macRegex) && pass.matches(macFullRegex))) {
                        if (!extracted.any { it.type == "Xtream" && it.baseUrl == base && it.user == user }) {
                            extracted.add(
                                ParsedCredential(
                                    baseUrl = base,
                                    user = user,
                                    pass = pass,
                                    mac = "",
                                    type = "Xtream",
                                    expires = currExp ?: "N/A",
                                    activeConn = currAct ?: "N/A",
                                    maxConn = currMax ?: "N/A",
                                    sourceLink = effectiveSource,
                                    originLink = effectiveOrigin
                                )
                            )
                        }
                    }
                }

                currUrl = null
                currPort = null
                currUser = null
                currPass = null
                currMac = null
                currExp = null
                currAct = null
                currMax = null
            }

            for (line in cleanText.lines()) {
                val lineTrim = line.trim()
                if (lineTrim.isEmpty() || lineTrim.length > 800 || lineTrim.contains(resetSeparatorRegex) || lineTrim.contains("player_api.php") || lineTrim.contains("get.php")) {
                    flushCurrentBlock()
                    continue
                }

                try {
                    // Check if this line starts a new Portal / Host block
                    val isNewPortalLine = portalHeaderPattern.matcher(lineTrim).find()
                    if (isNewPortalLine && currUrl != null && (currUser != null || currMac != null)) {
                        flushCurrentBlock()
                    }

                    // Extract URL
                    val urlMatch = urlExtractPattern.matcher(lineTrim)
                    if (urlMatch.find()) {
                        val baseMatch = baseExtractPattern.matcher(urlMatch.group(1) ?: "")
                        if (baseMatch.find()) {
                            val candidate = baseMatch.group(1) ?: ""
                            if (candidate.isNotEmpty() && !isBlacklistedHost(candidate)) {
                                if (currUrl == null || isNewPortalLine) {
                                    currUrl = candidate
                                }
                            }
                        }
                    } else if (isNewPortalLine) {
                        // Portal without http prefix (e.g. Portal : fx2727.com)
                        val hostPart = lineTrim.substringAfter(":").trim()
                        if (hostPart.isNotEmpty() && !isBlacklistedHost(hostPart)) {
                            currUrl = "http://$hostPart"
                        }
                    }

                    // Extract Port
                    val portMatch = portExtractPattern.matcher(lineTrim)
                    if (portMatch.find()) {
                        currPort = portMatch.group(1)
                    }

                    // Extract MAC
                    val macMatch = macExtractPattern.matcher(lineTrim)
                    if (macMatch.find()) {
                        currMac = macMatch.group(1)?.uppercase()
                    }

                    // Extract User
                    val userMatch = userExtractPattern.matcher(lineTrim)
                    if (userMatch.find()) {
                        val uVal = userMatch.group(1)?.trim() ?: ""
                        if (!skipKeywords.contains(uVal.lowercase())) {
                            currUser = uVal
                        }
                    }

                    // Extract Pass
                    val passMatch = passExtractPattern.matcher(lineTrim)
                    if (passMatch.find()) {
                        val pVal = passMatch.group(1)?.trim() ?: ""
                        if (!skipKeywords.contains(pVal.lowercase())) {
                            currPass = pVal
                        }
                    }

                    // Extract Exp
                    val expMatch = expExtractPattern.matcher(lineTrim)
                    if (expMatch.find()) {
                        currExp = expMatch.group(1)?.trim()
                    }

                    // Extract Conn
                    val connMatch = singleConnExtractPattern.matcher(lineTrim)
                    if (connMatch.find()) {
                        currAct = connMatch.group(1)
                    }

                    // Extract MaxConn
                    val maxConnMatch = singleMaxConnExtractPattern.matcher(lineTrim)
                    if (maxConnMatch.find()) {
                        currMax = maxConnMatch.group(1)
                    }

                    // Check if line indicates status / block completion
                    if (statusEndPattern.matcher(lineTrim).find()) {
                        flushCurrentBlock()
                    }
                } catch (e: Throwable) {}
            }

            // Final flush for remaining block
            flushCurrentBlock()

            extracted
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

