package com.projectstrong.iptv.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.projectstrong.iptv.data.SettingsManager
import com.projectstrong.iptv.data.AppThemeMode
import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer

import kotlinx.coroutines.delay

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
    var currentToastState by mutableStateOf<ToastMessage?>(null)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun show(message: String, type: ToastType = ToastType.INFO, durationMs: Long = 2500L) {
        val toast = ToastMessage(message, type, durationMs)
        mainHandler.post {
            currentToastState = toast
        }
    }

    fun clear() {
        mainHandler.post {
            currentToastState = null
        }
    }

    fun success(message: String) = show(message, ToastType.SUCCESS)
    fun info(message: String) = show(message, ToastType.INFO)
    fun warning(message: String) = show(message, ToastType.WARNING)
    fun error(message: String) = show(message, ToastType.ERROR)
}


@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val currentToast = ToastManager.currentToastState
    val theme = SettingsManager.currentTheme

    LaunchedEffect(currentToast) {
        if (currentToast != null) {
            delay(currentToast.durationMs)
            if (ToastManager.currentToastState == currentToast) {
                ToastManager.clear()
            }
        }
    }

    AnimatedVisibility(
        visible = currentToast != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -60 }) + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -60 }) + scaleOut(targetScale = 0.92f),
        modifier = modifier.padding(top = 16.dp)
    ) {
        currentToast?.let { toast ->
            val (bgColor, borderColor, iconTint, icon) = when (toast.type) {
                ToastType.SUCCESS -> Quadruple(
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color.Black else Color(0xFF064E3B).copy(alpha = 0.95f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFF00C805) else Color(0xFF34D399).copy(alpha = 0.6f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFF00C805) else Color(0xFF34D399),
                    Icons.Default.CheckCircle
                )
                ToastType.WARNING -> Quadruple(
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color.Black else Color(0xFF78350F).copy(alpha = 0.95f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFFFACC15) else Color(0xFFFBBF24).copy(alpha = 0.6f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFFFACC15) else Color(0xFFFBBF24),
                    Icons.Default.Warning
                )
                ToastType.ERROR -> Quadruple(
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color.Black else Color(0xFF7F1D1D).copy(alpha = 0.95f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFFEF4444) else Color(0xFFF87171).copy(alpha = 0.6f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFFEF4444) else Color(0xFFF87171),
                    Icons.Default.ErrorOutline
                )
                ToastType.INFO -> Quadruple(
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color.Black else Color(0xFF0F172A).copy(alpha = 0.95f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFF06B6D4) else Color(0xFF38BDF8).copy(alpha = 0.6f),
                    if (theme == AppThemeMode.ROBINHOOD_NEON) Color(0xFF06B6D4) else Color(0xFF38BDF8),
                    Icons.Default.Info
                )
            }
            
            val blurModifier = if (theme == AppThemeMode.MACOS_LIQUID_GLASS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        30f, 30f, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                    alpha = 0.9f
                }
            } else Modifier
            
            val shape = if (theme == AppThemeMode.ROBINHOOD_NEON || theme == AppThemeMode.CINEMATIC_DARK) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp)

            Box(
                modifier = Modifier
                    .shadow(if (theme == AppThemeMode.CINEMATIC_DARK) 16.dp else 8.dp, shape)
                    .clip(shape)
                    .then(blurModifier)
                    .background(if (theme == AppThemeMode.MACOS_LIQUID_GLASS) Color(0xFF1E1E24).copy(alpha = 0.7f) else bgColor)
                    .border(if (theme == AppThemeMode.ROBINHOOD_NEON) 2.dp else 1.dp, borderColor, shape)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        fontFamily = if (theme == AppThemeMode.ROBINHOOD_NEON) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
