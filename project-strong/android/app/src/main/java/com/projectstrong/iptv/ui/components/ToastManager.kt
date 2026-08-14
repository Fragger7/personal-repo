package com.projectstrong.iptv.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ToastType {
    SUCCESS, INFO, WARNING, ERROR
}

data class ToastMessage(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = 2500L
)

object ToastManager {
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _toastEvents = MutableSharedFlow<ToastMessage>(replay = 1, extraBufferCapacity = 10)
    val toastEvents = _toastEvents.asSharedFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun show(message: String, type: ToastType = ToastType.INFO, durationMs: Long = 2500L) {
        val toast = ToastMessage(message, type, durationMs)
        _toastEvents.tryEmit(toast)

        // Also trigger guaranteed native Android toast on Main thread
        appContext?.let { ctx ->
            mainHandler.post {
                try {
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun success(message: String) = show(message, ToastType.SUCCESS)
    fun info(message: String) = show(message, ToastType.INFO)
    fun warning(message: String) = show(message, ToastType.WARNING)
    fun error(message: String) = show(message, ToastType.ERROR)
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    var currentToast by remember { mutableStateOf<ToastMessage?>(null) }

    LaunchedEffect(Unit) {
        ToastManager.toastEvents.collect { toast ->
            currentToast = toast
            delay(toast.durationMs)
            if (currentToast == toast) {
                currentToast = null
            }
        }
    }

    AnimatedVisibility(
        visible = currentToast != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
        modifier = modifier
    ) {
        currentToast?.let { toast ->
            val (bgColor, iconTint, icon) = when (toast.type) {
                ToastType.SUCCESS -> Triple(Color(0xFF064E3B), Color(0xFF34D399), Icons.Default.CheckCircle)
                ToastType.WARNING -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), Icons.Default.Warning)
                ToastType.ERROR -> Triple(Color(0xFF7F1D1D), Color(0xFFF87171), Icons.Default.ErrorOutline)
                ToastType.INFO -> Triple(Color(0xFF1E293B), Color(0xFF60A5FA), Icons.Default.Info)
            }

            Box(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = toast.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
