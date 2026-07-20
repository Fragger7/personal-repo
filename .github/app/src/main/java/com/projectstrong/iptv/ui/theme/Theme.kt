package com.projectstrong.iptv.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val GlassBackground = Color(0xFF0F172A) // Deep slate
val GlassSurface = Color(0x33FFFFFF) // Semi-transparent white
val GlassSurfaceVariant = Color(0x1AFFFFFF)
val GlassPrimary = Color(0xFF3B82F6) // Blue accent
val GlassSecondary = Color(0xFF8B5CF6) // Purple accent
val GlassError = Color(0xFFEF4444)
val GlassText = Color(0xFFF8FAFC)
val GlassTextSecondary = Color(0xFF94A3B8)

private val DarkGlassColorScheme = darkColorScheme(
    primary = GlassPrimary,
    secondary = GlassSecondary,
    background = GlassBackground,
    surface = GlassSurface,
    surfaceVariant = GlassSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GlassText,
    onSurface = GlassText,
    onSurfaceVariant = GlassTextSecondary,
    error = GlassError
)

@Composable
fun GlassTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkGlassColorScheme,
        content = content
    )
}
