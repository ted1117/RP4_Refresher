package com.hidsquid.refreshpaper.epd

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpdDisplayModeController(
    private val context: Context,
) {
    suspend fun getDisplayMode(): Int = withContext(Dispatchers.IO) {
        try {
            Settings.System.getInt(context.contentResolver, KEY_DISPLAY_MODE,
                MODE_NORMAL
            )
        } catch (_: Throwable) {
            // TODO: Implement vendor-specific or root(su)-based fallback if needed.
            MODE_NORMAL
        }
    }

    suspend fun setDisplayMode(mode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Magisk로 설치 시
            Settings.System.putInt(context.contentResolver, KEY_DISPLAY_MODE, mode)
            true
        } catch (_: Throwable) {
            // 일반 설치 시
            try {
                val process = ProcessBuilder(
                    "su", "-c", "settings put system $KEY_DISPLAY_MODE $mode"
                ).start()

                process.waitFor() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun normalize(rawMode: Int): Int {
        return if (rawMode == 0) MODE_MINIMIZE_AFTERIMAGE else MODE_NORMAL
    }

    companion object {
        const val MODE_MINIMIZE_AFTERIMAGE = 0

        const val MODE_NORMAL = 1

        const val KEY_DISPLAY_MODE = "display_mode"
    }
}
