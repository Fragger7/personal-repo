package com.projectstrong.iptv.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import com.projectstrong.iptv.data.AppThemeMode
import com.projectstrong.iptv.data.SettingsManager

// Dynamic Theme-Aware Token Accessors (Reactively adapting to current MaterialTheme colorScheme)
val AppBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val AppSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val AppSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val AppSurfaceBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

val AppPrimary: Color @Composable get() = MaterialTheme.colorScheme.primary
val AppPrimaryVariant: Color @Composable get() = MaterialTheme.colorScheme.secondary
val AppPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
val AppSecondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
val AppSuccess = Color(0xFF10B981)    // Emerald green
val AppSuccessContainer = Color(0x1A10B981)
val AppWarning = Color(0xFFF59E0B)    // Amber warning
val AppWarningContainer = Color(0x1AF59E0B)
val AppError = Color(0xFFEF4444)      // Crimson error
val AppErrorContainer = Color(0x1AEF4444)

val AppTextPrimary = Color(0xFFF8FAFC)   // High contrast white
val AppTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val AppTextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

// Palette 1: Cyber Sherlock Amber / Navy
private val SherlockAmberColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF090D16),
    surface = Color(0xFF131B2A),
    surfaceVariant = Color(0xFF1C2638),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF26334D),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = AppError
)

// Palette 2: Midnight Purple (Focus)
private val MidnightPurpleColorScheme = darkColorScheme(
    primary = Color(0xFFA855F7),
    secondary = Color(0xFFC084FC),
    tertiary = Color(0xFF818CF8),
    background = Color(0xFF0D0A1A),
    surface = Color(0xFF18132B),
    surfaceVariant = Color(0xFF271C44),
    outline = Color(0xFF4C1D95),
    outlineVariant = Color(0xFF3B2766),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFA5B4FC),
    error = AppError
)

// Palette 3: Ocean Blue (Glass)
private val OceanBlueColorScheme = darkColorScheme(
    primary = Color(0xFF0EA5E9),
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF07131F),
    surface = Color(0xFF0E2238),
    surfaceVariant = Color(0xFF173554),
    outline = Color(0xFF0369A1),
    outlineVariant = Color(0xFF1E4976),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFF7DD3FC),
    error = AppError
)

// Palette 4: Crimson Dark (OLED)
private val CrimsonDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE11D48),
    secondary = Color(0xFFF43F5E),
    tertiary = Color(0xFFFB7185),
    background = Color(0xFF10070A),
    surface = Color(0xFF220E15),
    surfaceVariant = Color(0xFF331420),
    outline = Color(0xFF9F1239),
    outlineVariant = Color(0xFF4C1628),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFFDA4AF),
    error = AppError
)

// Palette 5: macOS Liquid Glass
private val MacOsLiquidGlassColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF1E1E24), // Frosted graphite
    surface = Color(0xFF2A2A35),
    surfaceVariant = Color(0xFF373746),
    outline = Color(0xFFFFFFFF).copy(alpha = 0.15f), // 1px crisp frosted border
    outlineVariant = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFE2E8F0),
    error = AppError
)

// Palette 6: Robinhood Neon Dark
private val RobinhoodNeonColorScheme = darkColorScheme(
    primary = Color(0xFF00C805), // High-contrast matrix green
    secondary = Color(0xFF10B981),
    tertiary = Color(0xFF06B6D4), // Electric cyan
    background = Color(0xFF000000), // OLED black
    surface = Color(0xFF111111),
    surfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFF00C805).copy(alpha = 0.3f), // Neon border
    outlineVariant = Color(0xFF333333),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFD1D5DB),
    error = AppError
)

// Palette 7: Cinematic Netflix Dark
private val CinematicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE50914), // Netflix Red
    secondary = Color(0xFFFACC15), // Gold rating badges
    tertiary = Color(0xFFB91C1C),
    background = Color(0xFF141414), // Deep theatrical charcoal
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF2B2B2B),
    outline = Color(0xFFE50914).copy(alpha = 0.4f),
    outlineVariant = Color(0xFF3F3F3F),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = Color(0xFFD4D4D8),
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
        AppThemeMode.MACOS_LIQUID_GLASS -> MacOsLiquidGlassColorScheme
        AppThemeMode.ROBINHOOD_NEON -> RobinhoodNeonColorScheme
        AppThemeMode.CINEMATIC_DARK -> CinematicDarkColorScheme
        AppThemeMode.SYSTEM_MONET -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                SherlockAmberColorScheme
            }
        }
    }

    // Dynamic Shape Language based on Theme
    val shapes = when (themeMode) {
        AppThemeMode.ROBINHOOD_NEON -> androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )
        AppThemeMode.MACOS_LIQUID_GLASS -> androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
        AppThemeMode.CINEMATIC_DARK -> androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )
        else -> androidx.compose.material3.Shapes( // Default Cyber / Rounded
            small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }

    val defaultTypography = androidx.compose.material3.Typography()
    
    val typography = when (themeMode) {
        AppThemeMode.ROBINHOOD_NEON -> Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp)
        )
        AppThemeMode.CINEMATIC_DARK -> Typography(
            titleLarge = defaultTypography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
            labelLarge = defaultTypography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
        else -> defaultTypography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography,
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
