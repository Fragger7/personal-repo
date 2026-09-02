package com.projectstrong.iptv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode(val title: String, val description: String) {
    SHERLOCK_AMBER("Cyber Sherlock (Default)", "Cyber amber gold & deep obsidian navy"),
    MIDNIGHT_PURPLE("Midnight Purple", "Neon violet glow & deep twilight dark"),
    OCEAN_BLUE("Ocean Blue", "Electric cyan & deep abyss navy"),
    CRIMSON_DARK("Crimson Dark", "Vibrant crimson & pitch dark OLED"),
    MACOS_LIQUID_GLASS("macOS Liquid Glass", "Frosted graphite & icy electric blue"),
    ROBINHOOD_NEON("Robinhood Neon", "OLED black & high-contrast matrix green"),
    CINEMATIC_DARK("Nuvio Cinematic", "Theatrical charcoal & Netflix red"),
    SYSTEM_MONET("System Monet", "Dynamic Material You colors from your Android wallpaper")
}

object SettingsManager {
    private const val PREFS_NAME = "iptv_settings_prefs"
    private const val KEY_HTTP_TIMEOUT = "http_timeout_sec"
    private const val KEY_MAX_CONCURRENCY = "max_concurrency"
    private const val KEY_AUTO_REFRESH_NET = "auto_refresh_net"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_FAST_FAIL_HEDGING = "fast_fail_hedging"
    private const val KEY_EGRESS_VERIFICATION = "egress_verification_enabled"
    private const val KEY_AUTO_EGRESS_DEEP_SCAN = "auto_egress_deep_scan"
    private const val KEY_EGRESS_TIMEOUT = "egress_timeout_sec"
    private const val KEY_EGRESS_SAMPLE_COUNT = "egress_sample_count"
    private const val KEY_STREAM_FORMAT = "stream_output_format"
    private const val KEY_TLS_EVASION = "tls_evasion_enabled"

    private lateinit var prefs: SharedPreferences

    var httpTimeoutSeconds by mutableIntStateOf(6)
    var maxConcurrency by mutableIntStateOf(8)
    var autoRefreshNetwork by mutableStateOf(true)
    var keepScreenOnDuringScans by mutableStateOf(true)
    var currentTheme by mutableStateOf(AppThemeMode.SHERLOCK_AMBER)
    var fastFailHedgingEnabled by mutableStateOf(true)
    var egressVerificationEnabled by mutableStateOf(false)
    var autoEgressOnDeepScan by mutableStateOf(false)
    var egressTimeoutSeconds by mutableIntStateOf(4)
    var egressSampleCount by mutableIntStateOf(2)
    var streamOutputFormat by mutableStateOf("ts")
    var tlsEvasionEnabled by mutableStateOf(false)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        httpTimeoutSeconds = prefs.getInt(KEY_HTTP_TIMEOUT, 6)
        maxConcurrency = prefs.getInt(KEY_MAX_CONCURRENCY, 8)
        autoRefreshNetwork = prefs.getBoolean(KEY_AUTO_REFRESH_NET, true)
        keepScreenOnDuringScans = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        fastFailHedgingEnabled = prefs.getBoolean(KEY_FAST_FAIL_HEDGING, true)
        egressVerificationEnabled = prefs.getBoolean(KEY_EGRESS_VERIFICATION, false)
        autoEgressOnDeepScan = prefs.getBoolean(KEY_AUTO_EGRESS_DEEP_SCAN, false)
        egressTimeoutSeconds = prefs.getInt(KEY_EGRESS_TIMEOUT, 4)
        egressSampleCount = prefs.getInt(KEY_EGRESS_SAMPLE_COUNT, 2)
        streamOutputFormat = prefs.getString(KEY_STREAM_FORMAT, "ts") ?: "ts"
        tlsEvasionEnabled = prefs.getBoolean(KEY_TLS_EVASION, false)
        
        val savedThemeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SHERLOCK_AMBER.name)
        currentTheme = try {
            AppThemeMode.valueOf(savedThemeName ?: AppThemeMode.SHERLOCK_AMBER.name)
        } catch (e: Exception) {
            AppThemeMode.SHERLOCK_AMBER
        }
    }

        fun saveStreamOutputFormat(format: String) {
        streamOutputFormat = format
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_STREAM_FORMAT, format).apply()
        }
    }

    fun saveTlsEvasionEnabled(enabled: Boolean) {
        tlsEvasionEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_TLS_EVASION, enabled).apply()
        }
    }

    fun saveTheme(theme: AppThemeMode) {
        currentTheme = theme
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_THEME_MODE, theme.name).apply()
        }
    }

    fun saveEgressVerification(enabled: Boolean) {
        egressVerificationEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_EGRESS_VERIFICATION, enabled).apply()
        }
    }

    fun saveAutoEgressOnDeepScan(enabled: Boolean) {
        autoEgressOnDeepScan = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_AUTO_EGRESS_DEEP_SCAN, enabled).apply()
        }
    }

    fun saveEgressTimeoutSeconds(seconds: Int) {
        egressTimeoutSeconds = seconds.coerceIn(2, 15)
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_EGRESS_TIMEOUT, egressTimeoutSeconds).apply()
        }
    }

    fun saveEgressSampleCount(count: Int) {
        egressSampleCount = count.coerceIn(1, 5)
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_EGRESS_SAMPLE_COUNT, egressSampleCount).apply()
        }
    }

    fun saveFastFailHedging(enabled: Boolean) {
        fastFailHedgingEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_FAST_FAIL_HEDGING, enabled).apply()
        }
    }

    fun saveTimeout(seconds: Int) {
        httpTimeoutSeconds = seconds.coerceIn(3, 30)
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_HTTP_TIMEOUT, httpTimeoutSeconds).apply()
        }
    }

    fun saveTimeoutSeconds(context: Context? = null, seconds: Int) {
        saveTimeout(seconds)
    }

    fun saveConcurrency(count: Int) {
        maxConcurrency = count.coerceIn(2, 30)
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_MAX_CONCURRENCY, maxConcurrency).apply()
        }
    }

    fun saveConcurrencyLimit(context: Context? = null, count: Int) {
        saveConcurrency(count)
    }

    fun saveAutoRefresh(enabled: Boolean) {
        autoRefreshNetwork = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_AUTO_REFRESH_NET, enabled).apply()
        }
    }

    fun saveKeepScreenOn(enabled: Boolean) {
        keepScreenOnDuringScans = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        }
    }

    fun saveGithubToken(context: Context? = null, token: String) {
        CommittedManager.saveGithubToken(token)
    }

    fun purgeVolatileCache(): Int {
        val count = DataStore.scannedNodes.size
        DataStore.scannedNodes.clear()
        DataStore.scannerInput = ""
        DataStore.scanProgress = 0f
        DataStore.scanCountText = ""
        DataStore.isScanning = false
        DataStore.isScanPaused = false
        DataStore.isQueryingCatalogs = false
        DataStore.isCatalogQueryPaused = false
        DataStore.catalogQueryProgress = 0f
        DataStore.catalogQueryStatusText = ""
        return count
    }
}
