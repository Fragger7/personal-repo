package com.tvmime.theme

/**
 * TVMime Centralized Design System Tokens
 * 
 * Strict "Deep Black & Crimson Red" palette matching:
 * - Mockup: mockups/tv_livetv_red_black.jpg
 * - Admin Web Portal: https://tivimime.vercel.app
 */
object DesignSystemTokens {
    // 🎨 Color Palette (Raw ARGB Long values for multiplatform independence)
    object Colors {
        // Backgrounds
        const val Background = 0xFF070709
        const val Surface = 0xFF121217
        const val Card = 0xFF181822
        const val CardElevated = 0xFF20202C
        
        // Translucent Overlays (60fps Firestick optimized - flat alpha, no heavy blurs)
        const val OverlayScrim = 0xCC070709
        const val CardTranslucent = 0xDD121217
        const val StripTranslucent = 0xAA181822

        // Brand Accents
        const val Crimson = 0xFFE50914
        const val CrimsonBright = 0xFFFF1E27
        const val CrimsonDark = 0xFFB80710
        const val CrimsonGlow = 0x40E50914

        // Borders & Dividers
        const val Border = 0xFF262632
        const val BorderFocused = 0xFFFF1E27
        const val BorderSubtle = 0xFF1E1E26

        // Typography
        const val TextPrimary = 0xFFF3F4F6
        const val TextSecondary = 0xFF9CA3AF
        const val TextMuted = 0xFF6B7280
        const val TextCrimson = 0xFFFF1E27

        // Status
        const val StatusOnline = 0xFF10B981
        const val StatusWarning = 0xFFF59E0B
        const val StatusOffline = 0xFFEF4444
    }

    // 📐 Spacing & Radii
    object Spacing {
        const val Xs = 4
        const val Sm = 8
        const val Md = 12
        const val Lg = 16
        const val Xl = 24
        const val Xxl = 32

        // Radii
        const val RadiusSm = 8
        const val RadiusMd = 12
        const val RadiusLg = 16
        const val RadiusFull = 9999
    }

    // 📺 TV-Specific D-Pad Focus Tokens
    object DPad {
        const val FocusScale = 1.05f
        const val FocusBorderWidth = 2
        const val FocusGlowRadius = 8
    }
}
