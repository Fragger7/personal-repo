package com.projectstrong.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.projectstrong.iptv.data.HistoryItem
import com.projectstrong.iptv.data.PlayerHistoryManager
import com.projectstrong.iptv.data.PlayerScope
import com.projectstrong.iptv.ui.theme.*
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

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamPreviewDialog(
    streamUrl: String,
    streamName: String,
    streamId: String = "",
    categoryName: String = "",
    scope: PlayerScope = PlayerScope.INSPECTOR,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Reactive State for Active Channel (supports seamless in-player channel switching)
    var currentStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var currentStreamName by remember(streamName) { mutableStateOf(streamName) }
    var currentStreamId by remember(streamId) { mutableStateOf(streamId) }
    var currentCategoryName by remember(categoryName) { mutableStateOf(categoryName) }
    var streamLoadStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Watch History Overlay Sheet State
    var showHistorySheet by remember { mutableStateOf(false) }

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

    // Auto-Hide Controls State (Fades out after ~3s of inactivity, reappears on video tap)
    var showControls by remember { mutableStateOf(true) }
    var controlsInteractionTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide timer: Hides controls after 3 seconds of no interaction while video is playing
    LaunchedEffect(showControls, controlsInteractionTrigger, isPlaying, playStatus, showHistorySheet) {
        if (showControls && isPlaying && playStatus == StreamPlayStatus.PLAYING && !showHistorySheet) {
            delay(3000)
            if (!isUserScrubbing) {
                showControls = false
            }
        }
    }

    val streamFormat = remember(currentStreamUrl) {
        when {
            currentStreamUrl.contains(".m3u8", ignoreCase = true) -> "HLS Multi-Bitrate (.m3u8)"
            currentStreamUrl.contains(".ts", ignoreCase = true) -> "MPEG-TS Live Stream (.ts)"
            currentStreamUrl.contains(".mp4", ignoreCase = true) -> "MPEG-4 Container (.mp4)"
            currentStreamUrl.contains(".mkv", ignoreCase = true) -> "Matroska Video (.mkv)"
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

    // Channel Switching Handler
    val switchChannel: (HistoryItem) -> Unit = { targetItem ->
        if (targetItem.streamUrl.isNotBlank() && targetItem.streamUrl != currentStreamUrl) {
            currentStreamUrl = targetItem.streamUrl
            currentStreamName = targetItem.streamName
            currentStreamId = targetItem.streamId
            currentCategoryName = targetItem.categoryName
            controlsInteractionTrigger = System.currentTimeMillis()
        }
    }

    // Manage Media Loading & History Recording when active channel URL changes
    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isBlank()) return@LaunchedEffect

        // 1. Record into PlayerHistoryManager for active scope
        PlayerHistoryManager.recordChannel(
            scope = scope,
            item = HistoryItem(
                streamUrl = currentStreamUrl,
                streamName = currentStreamName,
                streamId = currentStreamId,
                categoryName = currentCategoryName
            )
        )

        // 2. Reset diagnostic telemetry
        playStatus = StreamPlayStatus.CONNECTING
        errorMessage = null
        connectionLatencyMs = 0L
        streamLoadStartTime = System.currentTimeMillis()
        currentBitrateKbps = 0L
        bufferHealthSeconds = 0f
        videoResolution = "Detecting..."
        videoCodec = "Analyzing..."
        audioCodec = "Analyzing..."
        currentPositionMs = 0L
        durationMs = 0L

        // 3. Prepare and stream via ExoPlayer
        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(currentStreamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            playStatus = StreamPlayStatus.ERROR
            errorMessage = e.localizedMessage ?: "Failed to load stream"
        }
    }

    val activity = remember(context) { context.findActivity() }
    // Keep Screen On while previewing stream
    val window = activity?.window
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Fullscreen System UI & Orientation Sync
    val view = LocalView.current
    DisposableEffect(isFullScreen) {
        if (activity != null) {
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            if (isFullScreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
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

    // Monitor Latency & Playback Events on ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        playStatus = StreamPlayStatus.BUFFERING
                    }
                    Player.STATE_READY -> {
                        playStatus = StreamPlayStatus.PLAYING
                        if (connectionLatencyMs == 0L) {
                            connectionLatencyMs = (System.currentTimeMillis() - streamLoadStartTime).coerceAtLeast(1L)
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
                val cause = error.cause
                val message = when {
                    cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> {
                        when (cause.responseCode) {
                            456 -> "👻 Ghost Line (HTTP 456): Account authenticated via API, but provider blocked stream playback."
                            884 -> "🔒 Anti-Dump Lockout (HTTP 884): Provider blocks playlist and stream dumping."
                            403 -> "🛡️ Stream Forbidden (HTTP 403): Streaming server rejected access."
                            521 -> "🔴 Server Dead (HTTP 521): Stream delivery server is down."
                            else -> "HTTP ${cause.responseCode}: ${error.localizedMessage ?: "Stream request failed"}"
                        }
                    }
                    else -> error.localizedMessage ?: "Stream unreachable or codec unsupported (Error ${error.errorCodeName})"
                }
                errorMessage = message
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

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    BackHandler {
        if (showHistorySheet) {
            showHistorySheet = false
        } else {
            onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
            color = AppBackground
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
                            .background(AppSurface)
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
                                    .background(AppSurfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Player",
                                    tint = AppTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = when (scope) {
                                            PlayerScope.INSPECTOR -> "Stream Inspector"
                                            PlayerScope.VAULT -> "Vault Player"
                                            PlayerScope.FAVORITES -> "Favorites Player"
                                        },
                                        color = AppPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (currentCategoryName.isNotEmpty()) {
                                        Text(
                                            text = "• $currentCategoryName",
                                            color = AppTextSecondary,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = currentStreamName,
                                    color = AppTextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Live Play Status Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when (playStatus) {
                                StreamPlayStatus.PLAYING -> AppSuccess.copy(alpha = 0.15f)
                                StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> AppWarning.copy(alpha = 0.15f)
                                StreamPlayStatus.ERROR -> AppError.copy(alpha = 0.15f)
                                else -> AppSurfaceBorder
                            },
                            border = BorderStroke(
                                1.dp,
                                when (playStatus) {
                                    StreamPlayStatus.PLAYING -> AppSuccess.copy(alpha = 0.5f)
                                    StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> AppWarning.copy(alpha = 0.5f)
                                    StreamPlayStatus.ERROR -> AppError.copy(alpha = 0.5f)
                                    else -> AppSurfaceBorder
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (playStatus) {
                                                StreamPlayStatus.PLAYING -> AppSuccess
                                                StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> AppWarning
                                                StreamPlayStatus.ERROR -> AppError
                                                else -> Color.Gray
                                            }
                                        )
                                )
                                Text(
                                    text = when (playStatus) {
                                        StreamPlayStatus.PLAYING -> "LIVE"
                                        StreamPlayStatus.CONNECTING -> "SYNCING"
                                        StreamPlayStatus.BUFFERING -> "BUFFER"
                                        StreamPlayStatus.ERROR -> "FAILED"
                                        StreamPlayStatus.IDLE -> "IDLE"
                                    },
                                    color = when (playStatus) {
                                        StreamPlayStatus.PLAYING -> AppSuccess
                                        StreamPlayStatus.CONNECTING, StreamPlayStatus.BUFFERING -> AppWarning
                                        StreamPlayStatus.ERROR -> AppError
                                        else -> Color.Gray
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Video Display Canvas with Floating Overlay Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.height(280.dp))
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = !showControls
                            if (showControls) {
                                controlsInteractionTrigger = System.currentTimeMillis()
                            }
                        },
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
                                    text = errorMessage ?: "Unknown error while attempting to decode stream.",
                                    color = Color(0xFFCBD5E1),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Button(
                                    onClick = {
                                        controlsInteractionTrigger = System.currentTimeMillis()
                                        playStatus = StreamPlayStatus.CONNECTING
                                        errorMessage = null
                                        connectionLatencyMs = 0L
                                        streamLoadStartTime = System.currentTimeMillis()
                                        val mediaItem = MediaItem.fromUri(Uri.parse(currentStreamUrl))
                                        exoPlayer.setMediaItem(mediaItem)
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Connection", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Floating Fullscreen Top Info HUD
                    if (isFullScreen) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControls,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            Row(
                                modifier = Modifier
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
                                        text = currentStreamName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        controlsInteractionTrigger = System.currentTimeMillis()
                                        isFullScreen = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Watch History Bottom Overlay Card (Option A)
                    if (showHistorySheet) {
                        // Dismiss scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showHistorySheet = false
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showHistorySheet,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .padding(bottom = 54.dp) // Sits neatly above the bottom controls bar
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0F172A).copy(alpha = 0.96f),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { /* Consume tap inside sheet */ }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // Header: Title, Scope Badge, Clear All, Close
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = "Watch History",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Watch History",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1E293B)
                                        ) {
                                            Text(
                                                text = when (scope) {
                                                    PlayerScope.INSPECTOR -> "Inspector"
                                                    PlayerScope.VAULT -> "Vault"
                                                    PlayerScope.FAVORITES -> "Favorites"
                                                },
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val currentHistory = PlayerHistoryManager.getHistory(scope)
                                        if (currentHistory.isNotEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    PlayerHistoryManager.clearHistory(scope)
                                                    ToastManager.info("Watch history cleared")
                                                    controlsInteractionTrigger = System.currentTimeMillis()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteSweep,
                                                    contentDescription = "Clear All",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Clear All",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                showHistorySheet = false
                                                controlsInteractionTrigger = System.currentTimeMillis()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val historyItems = PlayerHistoryManager.getHistory(scope)
                                if (historyItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No channels in watch history yet for this section.",
                                            color = Color(0xFF64748B),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Tap to switch • Long-press to remove",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(historyItems, key = { it.streamUrl }) { item ->
                                            val isCurrent = item.streamUrl == currentStreamUrl
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) Color(0xFF1E293B) else Color(0xFF161E2E),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isCurrent) Color(0xFF38BDF8) else Color(0xFF334155).copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = {
                                                            switchChannel(item)
                                                            showHistorySheet = false
                                                            controlsInteractionTrigger = System.currentTimeMillis()
                                                        },
                                                        onLongClick = {
                                                            PlayerHistoryManager.removeChannel(scope, item)
                                                            ToastManager.info("Removed '${item.streamName}' from history")
                                                            controlsInteractionTrigger = System.currentTimeMillis()
                                                        }
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        if (isCurrent) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF34D399))
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.streamName,
                                                                color = if (isCurrent) Color(0xFF38BDF8) else Color.White,
                                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (item.categoryName.isNotBlank()) {
                                                                Text(
                                                                    text = item.categoryName,
                                                                    color = Color(0xFF94A3B8),
                                                                    fontSize = 11.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (isCurrent) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFF065F46).copy(alpha = 0.7f)
                                                            ) {
                                                                Text(
                                                                    text = "PLAYING",
                                                                    color = Color(0xFF34D399),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                PlayerHistoryManager.removeChannel(scope, item)
                                                                ToastManager.info("Removed '${item.streamName}' from history")
                                                                controlsInteractionTrigger = System.currentTimeMillis()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Remove",
                                                                tint = Color(0xFF64748B),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rich Bottom Player Action & Track Bar (Auto-hides with smooth animation)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier
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
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                        },
                                        onValueChangeFinished = {
                                            exoPlayer.seekTo(scrubPositionMs.toLong())
                                            isUserScrubbing = false
                                            controlsInteractionTrigger = System.currentTimeMillis()
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
                                // Left Actions: Play/Pause, Mute, Aspect, Sync, Last Channel, Watch History
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())
                                ) {
                                    PlayerLabeledButton(
                                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        label = if (isPlaying) "Pause" else "Play",
                                        tint = if (isPlaying) Color.White else Color(0xFF34D399),
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                    )

                                    PlayerLabeledButton(
                                        icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        label = if (isMuted) "Unmute" else "Mute",
                                        tint = if (isMuted) Color(0xFFF87171) else Color.White,
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
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
                                            controlsInteractionTrigger = System.currentTimeMillis()
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
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            exoPlayer.seekToDefaultPosition()
                                            exoPlayer.play()
                                        }
                                    )

                                    // 1. Last Channel Button (Immediately to the Right of Sync)
                                    val lastChannel = PlayerHistoryManager.getLastChannel(scope, currentStreamUrl)
                                    PlayerLabeledButton(
                                        icon = Icons.Default.FastRewind,
                                        label = "Last",
                                        tint = if (lastChannel != null) Color(0xFFFBBF24) else Color(0xFF64748B),
                                        enabled = lastChannel != null,
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            if (lastChannel != null) {
                                                switchChannel(lastChannel)
                                                ToastManager.info("Switched to: ${lastChannel.streamName}")
                                            } else {
                                                ToastManager.info("No previous channel")
                                            }
                                        }
                                    )

                                    // 2. Watch History Button (Immediately to the Right of Last Channel)
                                    val historyCount = PlayerHistoryManager.getHistory(scope).size
                                    PlayerLabeledButton(
                                        icon = Icons.Default.History,
                                        label = if (historyCount > 0) "History ($historyCount)" else "History",
                                        tint = if (showHistorySheet) Color(0xFF38BDF8) else Color.White,
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            showHistorySheet = !showHistorySheet
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
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            clipboardManager.setText(AnnotatedString(currentStreamUrl))
                                            showCopiedToast = true
                                        }
                                    )

                                    PlayerLabeledButton(
                                        icon = Icons.Default.OpenInNew,
                                        label = "VLC",
                                        tint = Color(0xFF60A5FA),
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(currentStreamUrl), "video/*")
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
                                        onClick = {
                                            controlsInteractionTrigger = System.currentTimeMillis()
                                            isFullScreen = !isFullScreen
                                        }
                                    )
                                }
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
                            color = AppTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppSurface,
                            border = BorderStroke(1.dp, AppSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Live Bitrate & Speed
                                DiagnosticMetricRow(
                                    icon = Icons.Default.Speed,
                                    iconColor = AppPrimary,
                                    label = "Live Bitrate Throughput",
                                    value = if (currentBitrateKbps > 0) "$currentBitrateKbps kbps (${String.format("%.2f", currentBitrateKbps / 1000f)} Mbps)" else "Estimating..."
                                )
                                Divider(color = AppSurfaceBorder)

                                // Buffer Health
                                DiagnosticMetricRow(
                                    icon = Icons.Default.HourglassTop,
                                    iconColor = AppSuccess,
                                    label = "Live Buffer Cushion",
                                    value = "${String.format("%.1f", bufferHealthSeconds)} s ahead"
                                )
                                Divider(color = AppSurfaceBorder)

                                // Video Resolution
                                DiagnosticMetricRow(
                                    icon = Icons.Default.HighQuality,
                                    iconColor = AppWarning,
                                    label = "Video Resolution",
                                    value = videoResolution
                                )
                                Divider(color = AppSurfaceBorder)

                                // Codecs
                                DiagnosticMetricRow(
                                    icon = Icons.Default.VideoLibrary,
                                    iconColor = Color(0xFF60A5FA),
                                    label = "Video Codec & FPS",
                                    value = videoCodec
                                )
                                Divider(color = AppSurfaceBorder)

                                // Audio Encoding
                                DiagnosticMetricRow(
                                    icon = Icons.Default.Audiotrack,
                                    iconColor = Color(0xFFF472B6),
                                    label = "Audio Encoding",
                                    value = audioCodec
                                )
                                Divider(color = AppSurfaceBorder)

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
                            color = AppSurface,
                            border = BorderStroke(1.dp, AppSurfaceBorder),
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
                                        color = AppTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ID: $currentStreamId",
                                        color = AppPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = currentStreamUrl,
                                    color = AppTextPrimary,
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (enabled) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFF1E293B).copy(alpha = 0.25f),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else Color(0xFF475569),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF64748B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    icon: ImageVector,
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
