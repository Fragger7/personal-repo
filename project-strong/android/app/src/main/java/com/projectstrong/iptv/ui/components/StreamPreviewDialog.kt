package com.projectstrong.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class StreamPlayStatus {
    IDLE,
    CONNECTING,
    PLAYING,
    BUFFERING,
    ERROR
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
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
    val clipboardManager = LocalClipboardManager.current

    var playStatus by remember { mutableStateOf(StreamPlayStatus.CONNECTING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoResolution by remember { mutableStateOf("Detecting...") }
    var videoCodec by remember { mutableStateOf("Analyzing...") }
    var audioCodec by remember { mutableStateOf("Analyzing...") }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var connectionLatencyMs by remember { mutableLongStateOf(0L) }
    var isFullScreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Real-Time Telemetry & Playback Position States
    var currentBitrateKbps by remember { mutableLongStateOf(0L) }
    var bufferHealthSeconds by remember { mutableFloatStateOf(0f) }
    var showCopiedToast by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLiveStream by remember { mutableStateOf(true) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val streamFormat = remember(streamUrl) {
        when {
            streamUrl.contains(".m3u8", ignoreCase = true) -> "HLS Multi-Bitrate (.m3u8)"
            streamUrl.contains(".ts", ignoreCase = true) -> "MPEG-TS Live Stream (.ts)"
            streamUrl.contains(".mp4", ignoreCase = true) -> "MPEG-4 Container (.mp4)"
            streamUrl.contains(".mkv", ignoreCase = true) -> "Matroska Video (.mkv)"
            else -> "IPTV Transport Stream"
        }
    }

    // High-Efficiency OkHttp Client with Aggressive Buffer and Evasion Headers
    val exoPlayer = remember {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("IPTVSmartersPro/1.1.1 (Linux; Android 12; Build/SQ1A.220105.002)")

        // Low-latency load control for ultra-responsive IPTV handshakes
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // Min buffer before playback starts
                15000,  // Max buffer duration
                500,    // Buffer for playback start
                1000    // Buffer for re-buffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setForceHighestSupportedBitrate(true))
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    // Fullscreen Screen Orientation Sync
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(isFullScreen) {
        if (activity != null) {
            if (isFullScreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Real-Time Polling for Bitrate, Buffer, Duration, and Position
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(500)
            if (exoPlayer.playbackState == Player.STATE_READY) {
                // Buffer Health
                val bufferedPosition = exoPlayer.bufferedPosition
                val currentPosition = exoPlayer.currentPosition
                val bufferDuration = (bufferedPosition - currentPosition).coerceAtLeast(0)
                bufferHealthSeconds = bufferDuration / 1000f

                if (!isUserScrubbing) {
                    currentPositionMs = currentPosition
                }
                val dur = exoPlayer.duration
                if (dur > 0 && dur != C.TIME_UNSET) {
                    durationMs = dur
                    isLiveStream = exoPlayer.isCurrentMediaItemLive
                } else {
                    isLiveStream = true
                }

                // Real-time track format bitrate estimation
                val videoFormat = exoPlayer.videoFormat
                if (videoFormat != null && videoFormat.bitrate > 0) {
                    currentBitrateKbps = (videoFormat.bitrate / 1000).toLong()
                } else if (exoPlayer.playbackParameters.speed > 0) {
                    val w = exoPlayer.videoSize.width
                    val h = exoPlayer.videoSize.height
                    if (w > 0 && h > 0) {
                        currentBitrateKbps = ((w * h * 30 * 0.07) / 1000).toLong()
                    }
                }
            }
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
                    Player.STATE_IDLE -> {}
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
                                if (format.bitrate > 0) {
                                    currentBitrateKbps = (format.bitrate / 1000).toLong()
                                }
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
                    .then(if (!isFullScreen) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            ) {
                // Header Bar (Hidden during full-screen mode to give maximum view)
                if (!isFullScreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                }

                // Video Player Stage (Expands to fill entire screen in Full-Screen Mode)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullScreen) Modifier.fillMaxSize() else Modifier.height(240.dp)
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                this.resizeMode = resizeMode
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.resizeMode = resizeMode
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (playStatus == StreamPlayStatus.CONNECTING) "Negotiating stream handshake..." else "Buffering video stream...",
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
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "Stream Playback Error",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = errorMessage ?: "Unable to establish video pipe.",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        playStatus = StreamPlayStatus.CONNECTING
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Connection", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Top Floating Controls in Fullscreen
                    if (isFullScreen) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34D399))
                                )
                                Text(
                                    text = streamName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { isFullScreen = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                            }
                        }
                    }

                    // Rich Bottom Player Action & Track Bar Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        // Track / Scrub Bar for Catchup & VOD / Timeshift
                        if (durationMs > 0 && !isLiveStream) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatTime(if (isUserScrubbing) scrubPositionMs.toLong() else currentPositionMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = if (isUserScrubbing) scrubPositionMs else currentPositionMs.toFloat(),
                                    onValueChange = {
                                        isUserScrubbing = true
                                        scrubPositionMs = it
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(scrubPositionMs.toLong())
                                        isUserScrubbing = false
                                    },
                                    valueRange = 0f..durationMs.toFloat(),
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF38BDF8),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )
                                Text(
                                    text = formatTime(durationMs),
                                    color = Color(0xFF94A3B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Labeled Controls Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Actions: Play/Pause, Mute, Aspect, Sync
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PlayerLabeledButton(
                                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    label = if (isPlaying) "Pause" else "Play",
                                    tint = if (isPlaying) Color.White else Color(0xFF34D399),
                                    onClick = {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                )

                                PlayerLabeledButton(
                                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    label = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Color(0xFFF87171) else Color.White,
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    }
                                )

                                PlayerLabeledButton(
                                    icon = Icons.Default.AspectRatio,
                                    label = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                        else -> "Aspect"
                                    },
                                    tint = Color(0xFF38BDF8),
                                    onClick = {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    }
                                )

                                PlayerLabeledButton(
                                    icon = Icons.Default.Sync,
                                    label = "Sync",
                                    tint = Color(0xFF34D399),
                                    onClick = {
                                        exoPlayer.seekToDefaultPosition()
                                        exoPlayer.play()
                                    }
                                )
                            }

                            // Right Actions: Copy, External, Fullscreen
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PlayerLabeledButton(
                                    icon = Icons.Default.ContentCopy,
                                    label = "Copy",
                                    tint = Color(0xFF94A3B8),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(streamUrl))
                                        showCopiedToast = true
                                    }
                                )

                                PlayerLabeledButton(
                                    icon = Icons.Default.OpenInNew,
                                    label = "VLC",
                                    tint = Color(0xFF60A5FA),
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(streamUrl), "video/*")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Play with External Player"))
                                        } catch (e: Exception) {
                                            // Ignore if no external player
                                        }
                                    }
                                )

                                PlayerLabeledButton(
                                    icon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    label = if (isFullScreen) "Exit" else "Full",
                                    tint = Color.White,
                                    onClick = { isFullScreen = !isFullScreen }
                                )
                            }
                        }
                    }
                }

                // Diagnostics Telemetry Board (Visible in normal mode)
                if (!isFullScreen) {
                    val telemetryScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(telemetryScroll)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "REAL-TIME STREAM TELEMETRY & METRICS",
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
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Live Bitrate & Speed
                                DiagnosticMetricRow(
                                    icon = Icons.Default.Speed,
                                    iconColor = Color(0xFF38BDF8),
                                    label = "Live Bitrate Throughput",
                                    value = if (currentBitrateKbps > 0) "$currentBitrateKbps kbps (${String.format("%.2f", currentBitrateKbps / 1000f)} Mbps)" else "Estimating..."
                                )
                                Divider(color = Color(0xFF1E293B))

                                // Buffer Health
                                DiagnosticMetricRow(
                                    icon = Icons.Default.HourglassTop,
                                    iconColor = Color(0xFF34D399),
                                    label = "Live Buffer Cushion",
                                    value = "${String.format("%.1f", bufferHealthSeconds)} s ahead"
                                )
                                Divider(color = Color(0xFF1E293B))

                                // Video Resolution
                                DiagnosticMetricRow(
                                    icon = Icons.Default.HighQuality,
                                    iconColor = Color(0xFFFBBF24),
                                    label = "Video Resolution",
                                    value = videoResolution
                                )
                                Divider(color = Color(0xFF1E293B))

                                // Codecs
                                DiagnosticMetricRow(
                                    icon = Icons.Default.VideoLibrary,
                                    iconColor = Color(0xFF60A5FA),
                                    label = "Video Codec & FPS",
                                    value = videoCodec
                                )
                                Divider(color = Color(0xFF1E293B))

                                // Audio Encoding
                                DiagnosticMetricRow(
                                    icon = Icons.Default.Audiotrack,
                                    iconColor = Color(0xFFF472B6),
                                    label = "Audio Encoding",
                                    value = audioCodec
                                )
                                Divider(color = Color(0xFF1E293B))

                                // First Frame Latency
                                DiagnosticMetricRow(
                                    icon = Icons.Default.Timer,
                                    iconColor = Color(0xFFA78BFA),
                                    label = "First Frame Handshake",
                                    value = if (connectionLatencyMs > 0) "${connectionLatencyMs} ms" else "Measuring..."
                                )
                            }
                        }

                        // Direct Stream Target Endpoint
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "STREAM ENDPOINT ($streamFormat)",
                                        color = Color(0xFF64748B),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ID: $streamId",
                                        color = Color(0xFF38BDF8),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
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
}

@Composable
private fun PlayerLabeledButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.6f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
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
                modifier = Modifier.size(16.dp)
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
