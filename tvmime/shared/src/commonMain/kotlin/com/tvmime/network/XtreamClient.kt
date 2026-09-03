package com.tvmime.network

import com.tvmime.model.Category
import com.tvmime.model.PortalConfig
import com.tvmime.model.StreamType
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * High-performance Xtream Codes client with anti-bot network evasion
 * and bounded fast-fail hedging.
 */
class XtreamClient(
    val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        const val EVASION_USER_AGENT = "IPTVSmartersPro/1.1.1"
        const val CONNECT_TIMEOUT_MS = 6000L
        const val SOCKET_TIMEOUT_MS = 8000L

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    })
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                    requestTimeoutMillis = 10000L
                }
                defaultRequest {
                    header(HttpHeaders.UserAgent, EVASION_USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                }
            }
        }
    }

    /**
     * Validates Xtream credentials via player_api.php
     */
    suspend fun authenticate(portal: PortalConfig): AuthResult {
        return try {
            val cleanServer = portal.serverUrl.trim().removeSuffix("/")
            val url = "$cleanServer/player_api.php"
            
            val response: HttpResponse = httpClient.get(url) {
                parameter("username", portal.username)
                parameter("password", portal.password)
            }

            if (response.status != HttpStatusCode.OK) {
                return AuthResult.Error("Server returned HTTP ${response.status.value}")
            }

            val rawText = response.bodyAsText()
            val json = Json.parseToJsonElement(rawText).jsonObject
            val userInfo = json["user_info"]?.jsonObject

            if (userInfo == null) {
                return AuthResult.Error("Invalid server response format")
            }

            val authStatus = userInfo["auth"]?.jsonPrimitive?.intOrNull
            val userStatus = userInfo["status"]?.jsonPrimitive?.content ?: "Inactive"

            if (authStatus == 1 && userStatus.equals("Active", ignoreCase = true)) {
                val expEpoch = userInfo["exp_date"]?.jsonPrimitive?.content?.toLongOrNull()
                val activeCons = userInfo["active_cons"]?.jsonPrimitive?.content ?: "0"
                val maxCons = userInfo["max_connections"]?.jsonPrimitive?.content ?: "1"
                
                AuthResult.Success(
                    status = userStatus,
                    expEpoch = expEpoch,
                    activeConnections = activeCons,
                    maxConnections = maxCons
                )
            } else {
                val msg = userInfo["message"]?.jsonPrimitive?.content ?: "Authentication failed ($userStatus)"
                AuthResult.Error(msg)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error connecting to provider")
        }
    }

    /**
     * Builds playback stream URL
     */
    fun buildStreamUrl(
        portal: PortalConfig,
        streamId: Int,
        type: StreamType = StreamType.LIVE,
        extension: String = "ts"
    ): String {
        val server = portal.serverUrl.trim().removeSuffix("/")
        val section = when (type) {
            StreamType.LIVE -> "live"
            StreamType.MOVIE -> "movie"
            StreamType.SERIES -> "series"
        }
        return "$server/$section/${portal.username}/${portal.password}/$streamId.$extension"
    }

    /**
     * Builds API endpoint URL for low-level token-by-token streaming
     */
    fun buildCatalogApiUrl(portal: PortalConfig, type: StreamType): String {
        val server = portal.serverUrl.trim().removeSuffix("/")
        val action = when (type) {
            StreamType.LIVE -> "get_live_streams"
            StreamType.MOVIE -> "get_vod_streams"
            StreamType.SERIES -> "get_series"
        }
        return "$server/player_api.php?username=${portal.username}&password=${portal.password}&action=$action"
    }

    /**
     * Builds Categories API endpoint URL
     */
    fun buildCategoriesApiUrl(portal: PortalConfig, type: StreamType): String {
        val server = portal.serverUrl.trim().removeSuffix("/")
        val action = when (type) {
            StreamType.LIVE -> "get_live_categories"
            StreamType.MOVIE -> "get_vod_categories"
            StreamType.SERIES -> "get_series_categories"
        }
        return "$server/player_api.php?username=${portal.username}&password=${portal.password}&action=$action"
    }
}

sealed interface AuthResult {
    data class Success(
        val status: String,
        val expEpoch: Long?,
        val activeConnections: String,
        val maxConnections: String
    ) : AuthResult

    data class Error(val message: String) : AuthResult
}
