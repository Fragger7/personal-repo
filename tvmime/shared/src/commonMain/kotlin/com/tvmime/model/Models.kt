package com.tvmime.model

import kotlinx.serialization.Serializable

@Serializable
data class PortalConfig(
    val id: String = "",
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val m3uUrl: String? = null,
    val type: String = "xtream",
    val isActive: Boolean = true,
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    val expiryDate: String? = null
)

@Serializable
enum class StreamType {
    LIVE,
    MOVIE,
    SERIES
}

@Serializable
data class Category(
    val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0,
    val type: StreamType = StreamType.LIVE
)

@Serializable
data class Channel(
    val id: String,
    val streamId: Int,
    val num: Int = 0,
    val name: String,
    val type: StreamType = StreamType.LIVE,
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val categoryId: String,
    val containerExtension: String = "ts",
    val directSourceUrl: String = ""
)

@Serializable
data class EpgProgram(
    val id: String,
    val epgChannelId: String,
    val title: String,
    val description: String? = null,
    val startEpoch: Long,
    val endEpoch: Long
) {
    val progress: Float
        get() {
            val now = System.currentTimeMillis() / 1000
            if (now <= startEpoch) return 0f
            if (now >= endEpoch) return 1f
            val total = endEpoch - startEpoch
            return if (total > 0) (now - startEpoch).toFloat() / total else 0f
        }
}
