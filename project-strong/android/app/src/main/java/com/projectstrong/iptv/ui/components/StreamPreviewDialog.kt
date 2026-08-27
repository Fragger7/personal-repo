package com.projectstrong.iptv.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

enum class StreamPlayStatus {
    IDLE,
    CONNECTING,
    PLAYING,
    BUFFERING,
    ERROR
}

@OptIn(UnstableApi::class)
@Composable
fun StreamPreviewDialog(
    streamUrl: String,
    streamName: String,
    streamId: String = "",
    categoryName: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var playStatus by remember { mutableStateOf(StreamPlayStatus.CONNECTING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoResolution by remember { mutableStateOf("Detecting...") }
    var videoCodec by remember { mutableStateOf("Analyzing...") }
    var audioCodec by remember { mutableStateOf("Analyzing...") }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var connectionLatencyMs by remember { mutableLongStateOf(0L) }
    var streamFormat by remember { mutableStateOf(if (streamUrl.endsWith(".ts")) "MPEG-TS (.ts)" else if (streamUrl.endsWith(".m3u8")) "HLS (.m3u8)" else "Direct Video") }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPro/1.1.1 (Linux; Android 12; Build/SQ1A.220105.002)")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    // Monitor Latency & Playback Events
    DisposableEffect(streamUrl) {
        val startTime = System.currentTimeMillis()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        playStatus = StreamPlayStatus.BUFFERING
                    }
                    Player.STATE_READY -> {
                        playStatus = StreamPlayStatus.PLAYING
                        if (connectionLatencyMs == 0L) {
                            connectionLatencyMs = System.currentTimeMillis() - startTime
                        }
                    }
                    Player.STATE_ENDED -> {
                        playStatus = StreamPlayStatus.IDLE
                    }
                    Player.STATE_IDLE -> {
                        // Keep current unless error
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playStatus = StreamPlayStatus.ERROR
                errorMessage = error.localizedMessage ?: "Stream unreachable or codec unsupported (Error ${error.errorCodeName})"
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val label = when {
                        videoSize.height >= 2160 -> "4K UHD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 1080 -> "1080p FHD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 720 -> "720p HD (${videoSize.width}x${videoSize.height})"
                        else -> "${videoSize.width}x${videoSize.height} SD"
                    }
                    videoResolution = label
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (trackGroup in tracks.groups) {
                    for (i in 0 until trackGroup.length) {
                        if (trackGroup.isTrackSelected(i)) {
                            val format = trackGroup.getTrackFormat(i)
                            if (format.sampleMimeType?.startsWith("video") == true) {
                                val mime = format.sampleMimeType ?: ""
                                val codecName = when {
                                    mime.contains("avc") || mime.contains("h264") -> "H.264 / AVC"
                                    mime.contains("hevc") || mime.contains("h265") -> "H.265 / HEVC"
                                    mime.contains("mp4v") -> "MPEG-4"
                                    mime.contains("vp9") -> "VP9"
                                    mime.contains("av1") -> "AV1"
                                    else -> mime.substringAfterLast("/")
                                }
                                val fps = if (format.frameRate > 0) " @ ${format.frameRate.toInt()}fps" else ""
                                videoCodec = "$codecName$fps"
                            }
                            if (format.sampleMimeType?.startsWith("audio") == true) {
                                val mime = format.sampleMimeType ?: ""
                                val audioName = when {
                                    mime.contains("mp4a") || mime.contains("aac") -> "AAC Stereo"
                                    mime.contains("ac3") || mime.contains("eac3") -> "Dolby Digital (AC-3)"
                                    mime.contains("mpeg") -> "MP3 / MPEG"
                                    else -> mime.substringAfterLast("/")
                                }
                                audioCodec = audioName
                            }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        exoPlayer.addListener(listener)

        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B0E)),
            color = Color(0xFF070B0E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Stream Inspector",
                                    color = Color(0xFF38BDF8),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (categoryName.isNotEmpty()) {
                                    Text(
                                        text = "• $categoryName",
                                        color = Color(0xFF94A3B8),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = streamName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Live Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (playStatus) {
                            StreamPlayStatus.PLAYING -> Color(0xFF065F46)
                            StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> Color(0xFF854D0E)
                            StreamPlayStatus.ERROR -> Color(0xFF991B1B)
                            StreamPlayStatus.IDLE -> Color(0xFF334155)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (playStatus) {
                                StreamPlayStatus.PLAYING -> Color(0xFF10B981)
                                StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> Color(0xFFF59E0B)
                                StreamPlayStatus.ERROR -> Color(0xFFEF4444)
                                StreamPlayStatus.IDLE -> Color(0xFF64748B)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (playStatus) {
                                            StreamPlayStatus.PLAYING -> Color(0xFF34D399)
                                            StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> Color(0xFFFBBF24)
                                            StreamPlayStatus.ERROR -> Color(0xFFF87171)
                                            StreamPlayStatus.IDLE -> Color(0xFF94A3B8)
                                        }
                                    )
                            )
                            Text(
                                text = when (playStatus) {
                                    StreamPlayStatus.PLAYING -> "LIVE PLAYING"
                                    StreamPlayStatus.CONNECTING -> "CONNECTING"
                                    StreamPlayStatus.BUFFERING -> "BUFFERING"
                                    StreamPlayStatus.ERROR -> "STREAM FAILED"
                                    StreamPlayStatus.IDLE -> "PAUSED"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Video Player Stage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // Custom Controls below
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Loading / Buffering Overlay
                    if (playStatus == StreamPlayStatus.CONNECTING || playStatus == StreamPlayStatus.BUFFERING) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = if (playStatus == StreamPlayStatus.CONNECTING) "Connecting to Stream Gateway..." else "Buffering Stream Packets...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Error Overlay
                    if (playStatus == StreamPlayStatus.ERROR) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Stream Diagnostic Failed",
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = errorMessage ?: "The provider server rejected the stream request or the transport stream is currently dead.",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        playStatus = StreamPlayStatus.CONNECTING
                                        errorMessage = null
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Connection", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Player Floating Controls Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) Color(0xFFF87171) else Color.White
                                )
                            }
                        }

                        // Codec & Format Quick Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = streamFormat,
                                color = Color(0xFF38BDF8),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Diagnostics Telemetry Board
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REAL-TIME STREAM TELEMETRY",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DiagnosticMetricRow(
                                icon = Icons.Default.HighQuality,
                                iconColor = Color(0xFF34D399),
                                label = "Video Resolution",
                                value = videoResolution
                            )
                            Divider(color = Color(0xFF1E293B))
                            DiagnosticMetricRow(
                                icon = Icons.Default.VideoLibrary,
                                iconColor = Color(0xFF60A5FA),
                                label = "Video Codec & FPS",
                                value = videoCodec
                            )
                            Divider(color = Color(0xFF1E293B))
                            DiagnosticMetricRow(
                                icon = Icons.Default.Audiotrack,
                                iconColor = Color(0xFFF472B6),
                                label = "Audio Encoding",
                                value = audioCodec
                            )
                            Divider(color = Color(0xFF1E293B))
                            DiagnosticMetricRow(
                                icon = Icons.Default.Speed,
                                iconColor = Color(0xFFFBBF24),
                                label = "First Frame Handshake",
                                value = if (connectionLatencyMs > 0) "${connectionLatencyMs} ms" else "Measuring..."
                            )
                        }
                    }

                    // Direct Stream URL Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "STREAM ENDPOINT TARGET",
                                color = Color(0xFF64748B),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = streamUrl,
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
