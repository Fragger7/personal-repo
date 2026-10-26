package com.projectstrong.iptv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

enum class PlayerScope {
    INSPECTOR,
    VAULT,
    FAVORITES
}

@androidx.annotation.Keep
data class HistoryItem(
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("stream_name") val streamName: String,
    @SerializedName("stream_id") val streamId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

object PlayerHistoryManager {
    private const val PREFS_NAME = "sherlock_player_history"
    private const val KEY_INSPECTOR_HISTORY = "history_inspector"
    private const val KEY_FAVORITES_HISTORY = "history_favorites"
    const val MAX_HISTORY_ITEMS = 7

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    // Scoped reactive state lists for Jetpack Compose
    val inspectorHistory = mutableStateListOf<HistoryItem>()
    val vaultHistory = mutableStateListOf<HistoryItem>()
    val favoritesHistory = mutableStateListOf<HistoryItem>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromPrefs(PlayerScope.INSPECTOR)
            loadFromPrefs(PlayerScope.FAVORITES)
            // Vault history is strictly in-memory session only (Zero-Knowledge rule)
            vaultHistory.clear()
        }
    }

    fun getHistory(scope: PlayerScope): List<HistoryItem> {
        return when (scope) {
            PlayerScope.INSPECTOR -> inspectorHistory
            PlayerScope.VAULT -> vaultHistory
            PlayerScope.FAVORITES -> favoritesHistory
        }
    }

    private fun getTargetList(scope: PlayerScope) = when (scope) {
        PlayerScope.INSPECTOR -> inspectorHistory
        PlayerScope.VAULT -> vaultHistory
        PlayerScope.FAVORITES -> favoritesHistory
    }

    fun recordChannel(scope: PlayerScope, item: HistoryItem) {
        if (item.streamUrl.isBlank()) return
        val list = getTargetList(scope)

        // De-duplicate: remove if identical URL or matching streamId + streamName
        val existingIndex = list.indexOfFirst {
            it.streamUrl == item.streamUrl ||
            (it.streamId.isNotBlank() && it.streamId == item.streamId && it.streamName == item.streamName)
        }
        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
        }

        // Add to top (MRU)
        list.add(0, item.copy(timestamp = System.currentTimeMillis()))

        // Cap at MAX_HISTORY_ITEMS
        while (list.size > MAX_HISTORY_ITEMS) {
            list.removeAt(list.lastIndex)
        }

        persistScope(scope)
    }

    fun removeChannel(scope: PlayerScope, item: HistoryItem) {
        val list = getTargetList(scope)
        list.removeAll { it.streamUrl == item.streamUrl }
        persistScope(scope)
    }

    fun clearHistory(scope: PlayerScope) {
        val list = getTargetList(scope)
        list.clear()
        persistScope(scope)
    }

    fun clearVaultHistory() {
        vaultHistory.clear()
    }

    fun getLastChannel(scope: PlayerScope, currentUrl: String): HistoryItem? {
        val list = getTargetList(scope)
        if (list.isEmpty()) return null
        return if (list[0].streamUrl == currentUrl) {
            if (list.size > 1) list[1] else null
        } else {
            list[0]
        }
    }

    private fun persistScope(scope: PlayerScope) {
        val p = prefs ?: return
        val (key, list) = when (scope) {
            PlayerScope.INSPECTOR -> KEY_INSPECTOR_HISTORY to inspectorHistory
            PlayerScope.FAVORITES -> KEY_FAVORITES_HISTORY to favoritesHistory
            PlayerScope.VAULT -> return // Do NOT persist Vault channels to disk!
        }
        try {
            val json = gson.toJson(list.toList())
            p.edit().putString(key, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromPrefs(scope: PlayerScope) {
        val p = prefs ?: return
        val (key, targetList) = when (scope) {
            PlayerScope.INSPECTOR -> KEY_INSPECTOR_HISTORY to inspectorHistory
            PlayerScope.FAVORITES -> KEY_FAVORITES_HISTORY to favoritesHistory
            PlayerScope.VAULT -> return
        }
        try {
            val json = p.getString(key, null) ?: return
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            val loaded: List<HistoryItem> = gson.fromJson(json, type) ?: emptyList()
            targetList.clear()
            targetList.addAll(loaded.take(MAX_HISTORY_ITEMS))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
