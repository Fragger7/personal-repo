package com.tvmime.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.tvmime.network.XtreamClient

/**
 * Media3 / ExoPlayer hardware-accelerated IPTV configuration.
 * Automatically injects anti-bot evasion headers into video chunk network requests.
 */
object Media3PlayerConfig {

    /**
     * Builds an HTTP Data Source Factory that spoofs IPTVSmartersPro/1.1.1.
     * Prevents HTTP 403 Forbidden responses on MPEG-TS and HLS media segments.
     */
    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(XtreamClient.EVASION_USER_AGENT)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
    }

    /**
     * Instantiates an ExoPlayer configured with:
     * 1. Hardware video decoder prioritization (EXTENSION_RENDERER_MODE_PREFER)
     * 2. Evasion HTTP DataSource
     */
    @OptIn(UnstableApi::class)
    fun buildPlayer(context: Context): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val httpDataSourceFactory = createHttpDataSourceFactory()
        val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(upstreamDataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
