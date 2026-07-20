package com.projectstrong.iptv.network

import java.util.regex.Pattern

data class ParsedCredential(
    val baseUrl: String,
    val user: String,
    val pass: String,
    val mac: String,
    val type: String,
    val status: String = "Pending Handshake",
    val details: String = "",
    val isVerifying: Boolean = false
)

object Parser {
    fun parseCredentials(textBlock: String): List<ParsedCredential> {
        val extracted = mutableListOf<ParsedCredential>()
        
        // 1. Xtream Pattern
        val patternXtream = Pattern.compile("(https?://[^/:]+(?::\\d+)?)/player_api\\.php\\?username=([^&\\s]+)&password=([^&\\s]+)")
        val matcherXtream = patternXtream.matcher(textBlock)
        while (matcherXtream.find()) {
            val baseUrl = matcherXtream.group(1) ?: ""
            val user = matcherXtream.group(2) ?: ""
            val pass = matcherXtream.group(3) ?: ""
            extracted.add(ParsedCredential(baseUrl, user, pass, "", "Xtream"))
        }

        // 2. Xtream Fallback
        val patternXtreamFallback = Pattern.compile("(https?://[^/:]+(?::\\d+)?)/get\\.php\\?username=([^&\\s]+)&password=([^&\\s]+)")
        val matcherFallback = patternXtreamFallback.matcher(textBlock)
        while (matcherFallback.find()) {
            val baseUrl = matcherFallback.group(1) ?: ""
            val user = matcherFallback.group(2) ?: ""
            val pass = matcherFallback.group(3) ?: ""
            if (!extracted.any { it.baseUrl == baseUrl && it.user == user }) {
                extracted.add(ParsedCredential(baseUrl, user, pass, "", "Xtream"))
            }
        }

        // 3. Combo Pattern
        val patternCombo = Pattern.compile("((?:https?://)?(?:(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}|(?:\\d{1,3}\\.){3}\\d{1,3})(?::\\d{2,5})?(?:/[^:\\s]*)?)(?:\\s+|:)([^:\\s]+)(?:\\s+|:)([^:\\s]+)")
        val matcherCombo = patternCombo.matcher(textBlock)
        while (matcherCombo.find()) {
            var baseUrl = matcherCombo.group(1) ?: ""
            if (!baseUrl.startsWith("http")) {
                baseUrl = "http://$baseUrl"
            }
            
            val user = matcherCombo.group(2) ?: ""
            val password = matcherCombo.group(3) ?: ""
            
            // Skip if it's actually a MAC address
            if (user.matches(Regex("^[0-9a-fA-F]{2}$")) && password.matches(Regex("^(?:[0-9a-fA-F]{2}:){4}[0-9a-fA-F]{2}$"))) {
                continue
            }
            
            val skipKeywords = listOf("mac", "active", "expired", "http", "https")
            if (skipKeywords.contains(user.lowercase()) || skipKeywords.contains(password.lowercase())) {
                continue
            }
            
            if (!extracted.any { it.baseUrl == baseUrl && it.user == user }) {
                extracted.add(ParsedCredential(baseUrl, user, password, "", "Xtream"))
            }
        }
        }

        // 4. Stalker Pattern (Robust State-Machine Parser for Free Text)
        var currentUrl: String? = null
        var currentMac: String? = null
        
        val urlExtractPattern = Pattern.compile("(https?://[^/\\s]+(?:/[^/\\s]*)?)")
        val baseExtractPattern = Pattern.compile("(https?://[^/:]+(?::\\d+)?)")
        val macExtractPattern = Pattern.compile("([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})", Pattern.CASE_INSENSITIVE)
        
        for (line in textBlock.lines()) {
            if (line.trim().isEmpty() || line.contains(Regex("[-=_*#]{4,}|╰─|╭─|┌─|└─|\\|"))) {
                currentUrl = null
                currentMac = null
            }
            
            val urlMatch = urlExtractPattern.matcher(line)
            if (urlMatch.find() && !line.contains("player_api") && !line.contains("get.php")) {
                val baseMatch = baseExtractPattern.matcher(urlMatch.group(1) ?: "")
                if (baseMatch.find()) {
                    currentUrl = baseMatch.group(1)
                }
            }
            
            val macMatch = macExtractPattern.matcher(line)
            if (macMatch.find()) {
                currentMac = macMatch.group(1)?.uppercase()
            }
            
            if (currentUrl != null && currentMac != null) {
                if (!extracted.any { it.type == "Stalker" && it.baseUrl == currentUrl && it.mac == currentMac }) {
                    extracted.add(ParsedCredential(currentUrl, currentMac, "MAC", currentMac, "Stalker"))
                }
                currentMac = null
            }
        }

        return extracted
    }
}
