package com.projectstrong.iptv.utils

import android.content.ClipData
import android.content.Context
import android.os.TransactionTooLargeException
import androidx.compose.ui.platform.ClipboardManager
import com.projectstrong.iptv.ui.components.ToastManager

object ClipboardHelper {
    // Maximum safe characters to process at once to prevent Binder IPC buffer overflow
    private const val MAX_SAFE_CHARS = 500_000

    /**
     * Safely reads text from clipboard with multi-tiered fallbacks and IPC buffer overflow protection.
     */
    fun getSafeClipboardText(context: Context, composeClipboard: ClipboardManager?): String? {
        // Tier 1: Try Compose ClipboardManager
        try {
            val text = composeClipboard?.getText()?.text
            if (!text.isNullOrEmpty()) {
                return sanitizeText(text)
            }
        } catch (e: TransactionTooLargeException) {
            ToastManager.warning("Pasted content is very large; processing safely.")
        } catch (e: Throwable) {
            // Fall through to system clipboard service
        }

        // Tier 2: Try Android System Clipboard Service directly
        try {
            val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (sysClipboard != null && sysClipboard.hasPrimaryClip()) {
                val clipData: ClipData? = sysClipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val text = item.coerceToText(context)?.toString()
                    if (!text.isNullOrEmpty()) {
                        return sanitizeText(text)
                    }
                }
            }
        } catch (e: TransactionTooLargeException) {
            ToastManager.warning("Pasted content exceeds clipboard buffer size.")
        } catch (e: SecurityException) {
            ToastManager.error("Clipboard access denied by system permissions.")
        } catch (e: Throwable) {
            ToastManager.error("Unable to read clipboard: ${e.localizedMessage ?: "Unknown error"}")
        }

        return null
    }

    private fun sanitizeText(input: String): String {
        if (input.length > MAX_SAFE_CHARS) {
            ToastManager.warning("Pasted payload truncated to safe limit (${MAX_SAFE_CHARS / 1000}k chars)")
            return input.substring(0, MAX_SAFE_CHARS)
        }
        return input
    }
}
