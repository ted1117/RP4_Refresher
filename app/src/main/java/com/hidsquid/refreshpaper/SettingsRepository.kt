package com.hidsquid.refreshpaper

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log

class SettingsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs = createPrefs(appContext)

    private val cr = context.contentResolver

    fun isAutoRefreshEnabled(): Boolean =
        prefs.getBoolean(ModulePrefs.KEY_AUTO_REFRESH_ENABLED, false)

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_AUTO_REFRESH_ENABLED, enabled)
            .commit()
    }

    fun isTouchRefreshEnabled(): Boolean {
        if (!isAutoRefreshEnabled()) return false
        return prefs.getBoolean(ModulePrefs.KEY_TOUCH_REFRESH_ENABLED, false)
    }

    fun setTouchRefreshEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_TOUCH_REFRESH_ENABLED, enabled)
            .commit()
    }

    fun isScreenshotChordEnabled(): Boolean {
        return prefs.getBoolean(ModulePrefs.KEY_SCREENSHOT_CHORD_ENABLED, true)
    }

    fun setScreenshotChordEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_SCREENSHOT_CHORD_ENABLED, enabled)
            .commit()
    }

    fun isPowerPageScreenshotEnabled(): Boolean {
        return prefs.getBoolean(ModulePrefs.KEY_POWER_PAGE_SCREENSHOT_ENABLED, true)
    }

    fun setPowerPageScreenshotEnabled(enabled: Boolean): Boolean {
        return prefs.edit()
            .putBoolean(ModulePrefs.KEY_POWER_PAGE_SCREENSHOT_ENABLED, enabled)
            .commit()
    }

    fun isScreenshotToastEnabled(): Boolean {
        return prefs.getBoolean(ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED, true)
    }

    fun setScreenshotToastEnabled(enabled: Boolean): Boolean {
        return prefs.edit()
            .putBoolean(ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED, enabled)
            .commit()
    }

    fun isManualRefreshEnabled(): Boolean =
        prefs.getBoolean(ModulePrefs.KEY_MANUAL_REFRESH_ENABLED, false)

    fun setManualRefreshEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_MANUAL_REFRESH_ENABLED, enabled)
            .commit()
    }

    fun getPagesPerRefresh(): Int =
        prefs.getInt(ModulePrefs.KEY_PAGES_PER_REFRESH, DEFAULT_PAGES_PER_REFRESH)

    fun setPagesPerRefresh(pages: Int) {
        prefs.edit()
            .putInt(ModulePrefs.KEY_PAGES_PER_REFRESH, pages)
            .commit()
    }

    fun getScreenBrightness(default: Int = DEFAULT_SCREEN_BRIGHTNESS): Int =
        Settings.System.getInt(cr, GLOBAL_KEY_SCREEN_BRIGHTNESS, default)

    fun setScreenBrightness(value: Int) {
        val clamped = value.coerceIn(0, MAX_SCREEN_BRIGHTNESS)
        try {
            Settings.System.putInt(cr, GLOBAL_KEY_SCREEN_BRIGHTNESS, clamped)
        } catch (_: Throwable) {
        }
    }

    fun getScreenBrightnessColor(default: Int = DEFAULT_SCREEN_BRIGHTNESS_COLOR): Int =
        Settings.System.getInt(cr, GLOBAL_KEY_SCREEN_BRIGHTNESS_COLOR, default)

    fun setScreenBrightnessColor(value: Int) {
        val clamped = value.coerceIn(0, MAX_SCREEN_BRIGHTNESS_COLOR)
        try {
            Settings.System.putInt(cr, GLOBAL_KEY_SCREEN_BRIGHTNESS_COLOR, clamped)
        } catch (_: Throwable) {
        }
    }

    fun getScreenOffTimeoutMillis(default: Int = DEFAULT_SCREEN_OFF_TIMEOUT_MILLIS): Int =
        Settings.System.getInt(cr, GLOBAL_KEY_SCREEN_OFF_TIMEOUT, default)

    fun setScreenOffTimeoutMillis(value: Int): Boolean {
        val clamped = value.coerceAtLeast(0)
        return try {
            Settings.System.putInt(cr, GLOBAL_KEY_SCREEN_OFF_TIMEOUT, clamped)
        } catch (_: Throwable) {
            false
        }
    }

    fun getHomeLauncherComponent(): String =
        try {
            prefs.getString(ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT, null)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_HOME_LAUNCHER_COMPONENT
        } catch (_: Throwable) {
            DEFAULT_HOME_LAUNCHER_COMPONENT
        }

    fun setHomeLauncherComponent(componentName: String): Boolean =
        prefs.edit().putString(ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT, componentName).commit()

    fun getF1Action(default: Int = F1_ACTION_BACK): Int {
        val value = prefs.getInt(ModulePrefs.KEY_F1_ACTION, default)
        return when (value) {
            F1_ACTION_BACK,
            F1_ACTION_SCREENSHOT,
            F1_ACTION_QUICK_SETTINGS,
            F1_ACTION_BRIGHTNESS,
            F1_ACTION_MANUAL_REFRESH -> value
            else -> default
        }
    }

    fun setF1Action(action: Int): Boolean {
        if (action !in VALID_F1_ACTIONS) return false
        return prefs.edit().putInt(ModulePrefs.KEY_F1_ACTION, action).commit()
    }

    fun getShutdownTimerHours(default: Int = DEFAULT_SHUTDOWN_TIMER_HOURS): Int {
        val rawMinutes = Settings.System.getInt(cr, GLOBAL_KEY_SHUTDOWN_TIMER_VALUE, default * 60)
        if (rawMinutes == 0) return 0
        if (rawMinutes < 0 || rawMinutes % 60 != 0) return default
        return rawMinutes / 60
    }

    fun setShutdownTimerHours(hours: Int): Boolean {
        val encodedValue = hours * 60
        return try {
            Settings.System.putInt(cr, GLOBAL_KEY_SHUTDOWN_TIMER_VALUE, encodedValue)
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val DEFAULT_PAGES_PER_REFRESH = 5
        private const val DEFAULT_SCREEN_BRIGHTNESS = 0
        private const val DEFAULT_SCREEN_BRIGHTNESS_COLOR = 0
        const val MAX_SCREEN_BRIGHTNESS = 206
        const val MAX_SCREEN_BRIGHTNESS_COLOR = 100

        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS = "screen_brightness"
        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS_COLOR = "screen_brightness_color"
        private const val GLOBAL_KEY_SCREEN_OFF_TIMEOUT = "screen_off_timeout"
        private const val GLOBAL_KEY_SHUTDOWN_TIMER_VALUE = "shutdown_timer_value"

        const val DEFAULT_HOME_LAUNCHER_PACKAGE = "cn.modificator.launcher"
        private const val DEFAULT_HOME_LAUNCHER_CLASS = "cn.modificator.launcher.Launcher"
        const val DEFAULT_HOME_LAUNCHER_COMPONENT =
            "$DEFAULT_HOME_LAUNCHER_PACKAGE/$DEFAULT_HOME_LAUNCHER_CLASS"

        const val F1_ACTION_BACK = 0
        const val F1_ACTION_SCREENSHOT = 1
        const val F1_ACTION_QUICK_SETTINGS = 2
        const val F1_ACTION_BRIGHTNESS = 3
        const val F1_ACTION_MANUAL_REFRESH = 4

        const val DEFAULT_SHUTDOWN_TIMER_HOURS = 1
        const val DEFAULT_SCREEN_OFF_TIMEOUT_MILLIS = 60_000

        private val VALID_F1_ACTIONS = setOf(
            F1_ACTION_BACK,
            F1_ACTION_SCREENSHOT,
            F1_ACTION_QUICK_SETTINGS,
            F1_ACTION_BRIGHTNESS,
            F1_ACTION_MANUAL_REFRESH
        )
    }

    private fun createPrefs(context: Context) = try {
        @SuppressLint("WorldReadableFiles")
        context.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (e: SecurityException) {
        Log.w(TAG, "MODE_WORLD_READABLE unsupported, fallback to MODE_PRIVATE", e)
        context.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE)
    }
}
