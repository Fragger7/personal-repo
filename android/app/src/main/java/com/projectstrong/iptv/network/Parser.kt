package com.projectstrong.iptv.network

import java.util.regex.Pattern

data class ParsedCredential(
    val baseUrl: String,
    val user: String,
    val pass: String,
    val mac: String,
    val type: String
)

object Parser {
    fun parseCredentials(textBlock: String): List<ParsedCredential> {
        val results = mutableListOf<ParsedCredential>()
        
        // Basic Xtream Codes regex
        val xtreamPattern = Pattern.compile("http[s]?://[^/]+/.*?username=([^&]+)&password=([^&\\s]+)")
        val xtreamMatcher = xtreamPattern.matcher(textBlock)
        while (xtreamMatcher.find()) {
            val url = xtreamMatcher.group(0) ?: ""
            val baseUrl = url.substringBefore("/get.php").substringBefore("/player_api.php")
            val user = xtreamMatcher.group(1) ?: ""
            val pass = xtreamMatcher.group(2) ?: ""
            results.add(ParsedCredential(baseUrl, user, pass, "", "Xtream"))
        }

        // Basic Stalker Pattern
        val stalkerPattern = Pattern.compile("(http[s]?://[^\\s]+/c/).*?([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})")
        val stalkerMatcher = stalkerPattern.matcher(textBlock)
        while (stalkerMatcher.find()) {
            val baseUrl = stalkerMatcher.group(1) ?: ""
            val mac = stalkerMatcher.group(2) ?: ""
            results.add(ParsedCredential(baseUrl.trimEnd('/'), "", "", mac, "Stalker"))
        }

        return results
    }
}
