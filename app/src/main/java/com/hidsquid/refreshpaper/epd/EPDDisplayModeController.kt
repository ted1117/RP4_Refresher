package com.hidsquid.refreshpaper.epd

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EPDDisplayModeController(
    private val context: Context,
) {
    suspend fun getDisplayMode(): Int = withContext(Dispatchers.IO) {
        try {
            Settings.System.getInt(context.contentResolver, KEY_DISPLAY_MODE,
                MODE_NORMAL
            )
        } catch (_: Throwable) {
            MODE_NORMAL
        }
    }

    suspend fun setDisplayMode(mode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Settings.System.putInt(context.contentResolver, KEY_DISPLAY_MODE, mode)
            true
        } catch (_: Throwable) {
            false
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
