package com.projectstrong.iptv.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.projectstrong.iptv.data.AppThemeMode
import com.projectstrong.iptv.data.SettingsManager

// Core Design Tokens - Sherlock Amber/Navy (Default)
val AppBackground = Color(0xFF090D16) // Deep rich dark slate
val AppSurface = Color(0xFF131B2A)    // Elevated surface container
val AppSurfaceVariant = Color(0xFF1C2638) // Secondary interactive surface
val AppSurfaceBorder = Color(0xFF26334D)  // Subtle card border

val AppPrimary = Color(0xFF3B82F6)    // Modern electric blue (Legacy alias)
val AppPrimaryVariant = Color(0xFF2563EB)
val AppPrimaryContainer = Color(0x1A3B82F6)
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

// Palette 1: Cyber Sherlock Amber / Navy
private val SherlockAmberColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF090D16),
    surface = Color(0xFF131B2A),
    surfaceVariant = Color(0xFF1C2638),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary,
    error = AppError
)

// Palette 2: Midnight Purple (Focus)
private val MidnightPurpleColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    secondary = Color(0xFFC084FC),
    tertiary = Color(0xFF818CF8),
    background = Color(0xFF0D0A1A),
    surface = Color(0xFF18132B),
    surfaceVariant = Color(0xFF241C3D),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFA5B4FC),
    error = AppError
)

// Palette 3: Ocean Blue (Glass)
private val OceanBlueColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF0A111E),
    surface = Color(0xFF132034),
    surfaceVariant = Color(0xFF1B2E4B),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = AppError
)

// Palette 4: Crimson Dark (OLED)
private val CrimsonDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF4444),
    secondary = Color(0xFFF43F5E),
    tertiary = Color(0xFFFB7185),
    background = Color(0xFF0F080A),
    surface = Color(0xFF1E1014),
    surfaceVariant = Color(0xFF2C161C),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFFDA4AF),
    error = AppError
)

@Composable
fun AppTheme(
    themeMode: AppThemeMode = SettingsManager.currentTheme,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when (themeMode) {
        AppThemeMode.SHERLOCK_AMBER -> SherlockAmberColorScheme
        AppThemeMode.MIDNIGHT_PURPLE -> MidnightPurpleColorScheme
        AppThemeMode.OCEAN_BLUE -> OceanBlueColorScheme
        AppThemeMode.CRIMSON_DARK -> CrimsonDarkColorScheme
        AppThemeMode.SYSTEM_MONET -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                SherlockAmberColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
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

