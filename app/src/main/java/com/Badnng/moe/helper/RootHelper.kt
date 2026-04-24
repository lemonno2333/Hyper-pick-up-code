package com.Badnng.moe.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object RootHelper {
    private const val TAG = "RootHelper"

    fun isSuAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("sh", "-c", "command -v su >/dev/null 2>&1").start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    // Avoid spamming Magisk toast: cache root check result and only re-check occasionally.
    @Volatile private var cachedRootAccess: Boolean? = null
    @Volatile private var lastRootCheckAtMs: Long = 0L
    private val rootCheckLock = Any()

    fun hasRootAccess(forceRefresh: Boolean = false): Boolean {
        val cached = cachedRootAccess
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null) {
            val ttlMs = if (cached) {
                // Once granted, keep it for process lifetime unless a root command fails.
                Long.MAX_VALUE
            } else {
                // If denied/unavailable, back off to avoid repeated prompts/toasts.
                5 * 60 * 1000L
            }
            if (now - lastRootCheckAtMs < ttlMs) return cached
        }

        synchronized(rootCheckLock) {
            val cached2 = cachedRootAccess
            val now2 = System.currentTimeMillis()
            if (!forceRefresh && cached2 != null) {
                val ttlMs = if (cached2) Long.MAX_VALUE else 5 * 60 * 1000L
                if (now2 - lastRootCheckAtMs < ttlMs) return cached2
            }
            val result = checkRootAccessOnce()
            cachedRootAccess = result
            lastRootCheckAtMs = now2
            return result
        }
    }

    fun invalidateRootAccessCache() {
        cachedRootAccess = null
        lastRootCheckAtMs = 0L
    }

    private fun checkRootAccessOnce(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val ok = process.waitFor() == 0
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Root not available", e)
            false
        }
    }

    fun captureScreenshot(): Bitmap? {
        return try {
            val process = ProcessBuilder("su", "-c", "screencap -p").start()
            val bitmap = BitmapFactory.decodeStream(process.inputStream)
            val code = process.waitFor()
            if (code == 0) bitmap else null
        } catch (e: Exception) {
            Log.e(TAG, "Root screenshot failed", e)
            // Root can be revoked while app is running; clear cache so future checks can re-validate.
            invalidateRootAccessCache()
            null
        }
    }
}
