package com.hidsquid.refreshpaper

import android.content.Context
import android.provider.Settings

class SettingsRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cr = context.contentResolver

    fun isAutoRefreshEnabled(): Boolean =
        prefs.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false)

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, enabled).apply()
        val touchPref = prefs.getBoolean(PREF_KEY_TOUCH_REFRESH_ENABLED, false)
        val effectiveTouch = enabled && touchPref
        try {
            Settings.Global.putInt(cr, GLOBAL_KEY_TOUCH_REFRESH_ENABLED, if (effectiveTouch) 1 else 0)
        } catch (e: SecurityException) {

        }
    }

    fun isTouchRefreshEnabled(): Boolean {
        if (!isAutoRefreshEnabled()) return false
        return prefs.getBoolean(PREF_KEY_TOUCH_REFRESH_ENABLED, false)
    }

    fun setTouchRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_TOUCH_REFRESH_ENABLED, enabled).apply()
        val effective = enabled && isAutoRefreshEnabled()
        val value = if (effective) 1 else 0
        try {
            Settings.Global.putInt(cr, GLOBAL_KEY_TOUCH_REFRESH_ENABLED, value)
        } catch (e: SecurityException) {

        }
    }

    fun isScreenshotChordEnabled(): Boolean {
        return prefs.getBoolean(PREF_KEY_SCREENSHOT_CHORD_ENABLED, true)
    }

    fun setScreenshotChordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_SCREENSHOT_CHORD_ENABLED, enabled).apply()
    }

    fun isPowerPageScreenshotEnabled(): Boolean {
        return try {
            Settings.Global.getInt(cr, GLOBAL_KEY_POWER_PAGE_SCREENSHOT_ENABLED, 1) == 1
        } catch (_: Throwable) {
            true
        }
    }

    fun setPowerPageScreenshotEnabled(enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                cr,
                GLOBAL_KEY_POWER_PAGE_SCREENSHOT_ENABLED,
                if (enabled) 1 else 0
            )
        } catch (_: Throwable) {
            false
        }
    }

    fun isManualRefreshEnabled(): Boolean =
        prefs.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false)

    fun setManualRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, enabled).apply()
    }

    fun getPagesPerRefresh(): Int =
        prefs.getInt(PREF_KEY_PAGES_PER_REFRESH, DEFAULT_PAGES_PER_REFRESH)

    fun setPagesPerRefresh(pages: Int) {
        prefs.edit().putInt(PREF_KEY_PAGES_PER_REFRESH, pages).apply()

        try {
            Settings.Global.putInt(cr, GLOBAL_KEY_PAGES_PER_REFRESH, pages)
        } catch (e: SecurityException) {

        }
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

    fun getHomeLauncherComponent(): String =
        try {
            Settings.Global.getString(cr, GLOBAL_KEY_HOME_LAUNCHER_COMPONENT)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_HOME_LAUNCHER_COMPONENT
        } catch (_: Throwable) {
            DEFAULT_HOME_LAUNCHER_COMPONENT
        }

    fun setHomeLauncherComponent(componentName: String): Boolean =
        try {
            Settings.Global.putString(cr, GLOBAL_KEY_HOME_LAUNCHER_COMPONENT, componentName)
        } catch (_: Throwable) {
            false
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
        private const val PREFS_NAME = "MyPrefs"

        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"
        private const val PREF_KEY_TOUCH_REFRESH_ENABLED = "touch_refresh_enabled"
        private const val PREF_KEY_SCREENSHOT_CHORD_ENABLED = "screenshot_chord_enabled"

        private const val DEFAULT_PAGES_PER_REFRESH = 5
        private const val DEFAULT_SCREEN_BRIGHTNESS = 0
        private const val DEFAULT_SCREEN_BRIGHTNESS_COLOR = 0
        const val MAX_SCREEN_BRIGHTNESS = 206
        const val MAX_SCREEN_BRIGHTNESS_COLOR = 100

        private const val GLOBAL_KEY_TOUCH_REFRESH_ENABLED = "refresh_paper_auto_enabled"
        private const val GLOBAL_KEY_PAGES_PER_REFRESH = "refresh_paper_pages_per_refresh"
        private const val GLOBAL_KEY_POWER_PAGE_SCREENSHOT_ENABLED =
            "refresh_paper_screenshot_chord_enabled"
        private const val GLOBAL_KEY_HOME_LAUNCHER_COMPONENT = "refresh_paper_home_launcher_component"
        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS = "screen_brightness"
        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS_COLOR = "screen_brightness_color"
        private const val GLOBAL_KEY_SHUTDOWN_TIMER_VALUE = "shutdown_timer_value"

        const val DEFAULT_HOME_LAUNCHER_PACKAGE = "cn.modificator.launcher"
        private const val DEFAULT_HOME_LAUNCHER_CLASS = "cn.modificator.launcher.Launcher"
        const val DEFAULT_HOME_LAUNCHER_COMPONENT =
            "$DEFAULT_HOME_LAUNCHER_PACKAGE/$DEFAULT_HOME_LAUNCHER_CLASS"

        const val DEFAULT_SHUTDOWN_TIMER_HOURS = 1
    }
}
