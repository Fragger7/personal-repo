package com.projectstrong.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Core Design Tokens
val AppBackground = Color(0xFF090D16) // Deep rich dark slate
val AppSurface = Color(0xFF131B2A)    // Elevated surface container
val AppSurfaceVariant = Color(0xFF1C2638) // Secondary interactive surface
val AppSurfaceBorder = Color(0xFF26334D)  // Subtle card border

val AppPrimary = Color(0xFF3B82F6)    // Modern electric blue
val AppPrimaryVariant = Color(0xFF2563EB)
val AppSecondary = Color(0xFF8B5CF6)  // Purple accent
val AppSuccess = Color(0xFF10B981)    // Emerald green
val AppSuccessContainer = Color(0x1A10B981)
val AppWarning = Color(0xFFF59E0B)    // Amber warning
val AppWarningContainer = Color(0x1AF59E0B)
val AppError = Color(0xFFEF4444)      // Crimson error
val AppErrorContainer = Color(0x1AEF4444)

val AppTextPrimary = Color(0xFFF8FAFC)   // High contrast white
val AppTextSecondary = Color(0xFF94A3B8) // Neutral slate gray
val AppTextMuted = Color(0xFF64748B)     // Subdued description text

private val AppDarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    secondary = AppSecondary,
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary,
    error = AppError
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun GlassTheme(
    content: @Composable () -> Unit
) {
    AppTheme(content = content)
}

