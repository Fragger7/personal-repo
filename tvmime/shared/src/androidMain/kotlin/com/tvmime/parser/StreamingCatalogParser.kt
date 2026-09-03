package com.tvmime.parser

import android.util.JsonReader
import com.tvmime.model.Category
import com.tvmime.model.Channel
import com.tvmime.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Ultra-low memory, token-by-token streaming catalog parser.
 * 
 * Bypasses high-level memory object mappers (Gson, Moshi, kotlinx.serialization)
 * to process 50MB - 100MB+ JSON payloads directly from network socket streams 
 * with a near-zero memory footprint (< 10MB RAM).
 */
object StreamingCatalogParser {

    /**
     * Stream-parses channels or VOD items token-by-token from an InputStream
     * and emits them incrementally into a Coroutine Flow.
     */
    fun parseChannelsStream(
        inputStream: InputStream,
        portalServerUrl: String,
        portalUser: String,
        portalPass: String,
        streamType: StreamType = StreamType.LIVE
    ): Flow<Channel> = flow {
        JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
            reader.isLenient = true

            // The root response is a JSON array: [ {...}, {...} ]
            reader.beginArray()
            while (reader.hasNext()) {
                val channel = parseSingleChannel(reader, portalServerUrl, portalUser, portalPass, streamType)
                if (channel != null) {
                    emit(channel)
                }
            }
            reader.endArray()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stream-parses categories token-by-token
     */
    fun parseCategoriesStream(
        inputStream: InputStream,
        streamType: StreamType = StreamType.LIVE
    ): Flow<Category> = flow {
        JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
            reader.isLenient = true

            reader.beginArray()
            while (reader.hasNext()) {
                var categoryId = ""
                var categoryName = ""
                var parentId = 0

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "category_id" -> categoryId = reader.nextString()
                        "category_name" -> categoryName = reader.nextString()
                        "parent_id" -> {
                            parentId = try {
                                reader.nextInt()
                            } catch (e: Exception) {
                                reader.nextString().toIntOrNull() ?: 0
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (categoryId.isNotBlank() && categoryName.isNotBlank()) {
                    emit(
                        Category(
                            categoryId = categoryId,
                            categoryName = categoryName.trim(),
                            parentId = parentId,
                            type = streamType
                        )
                    )
                }
            }
            reader.endArray()
        }
    }.flowOn(Dispatchers.IO)

    private fun parseSingleChannel(
        reader: JsonReader,
        serverUrl: String,
        user: String,
        pass: String,
        type: StreamType
    ): Channel? {
        var num = 0
        var name = ""
        var streamId = 0
        var streamIcon: String? = null
        var epgChannelId: String? = null
        var categoryId = "0"
        var containerExtension = "ts"

        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            when (field) {
                "num" -> {
                    num = try {
                        reader.nextInt()
                    } catch (e: Exception) {
                        reader.nextString().toIntOrNull() ?: 0
                    }
                }
                "name" -> name = reader.nextString()
                "stream_id" -> {
                    streamId = try {
                        reader.nextInt()
                    } catch (e: Exception) {
                        reader.nextString().toIntOrNull() ?: 0
                    }
                }
                "stream_icon" -> {
                    val icon = reader.nextString()
                    streamIcon = if (icon.isNotBlank()) icon else null
                }
                "epg_channel_id" -> {
                    val epg = reader.nextString()
                    epgChannelId = if (epg.isNotBlank()) epg else null
                }
                "category_id" -> categoryId = reader.nextString()
                "container_extension" -> {
                    val ext = reader.nextString()
                    if (ext.isNotBlank()) containerExtension = ext
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (streamId <= 0 || name.isBlank()) return null

        val section = when (type) {
            StreamType.LIVE -> "live"
            StreamType.MOVIE -> "movie"
            StreamType.SERIES -> "series"
        }
        val directUrl = "${serverUrl.removeSuffix("/")}/$section/$user/$pass/$streamId.$containerExtension"

        return Channel(
            id = "${type.name.lowercase()}_$streamId",
            streamId = streamId,
            num = num,
            name = name.trim(),
            type = type,
            streamIcon = streamIcon,
            epgChannelId = epgChannelId,
            categoryId = categoryId,
            containerExtension = containerExtension,
            directSourceUrl = directUrl
        )
    }
}
