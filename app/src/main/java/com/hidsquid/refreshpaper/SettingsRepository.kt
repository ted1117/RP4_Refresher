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

    companion object {
        private const val PREFS_NAME = "MyPrefs"

        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"
        private const val PREF_KEY_TOUCH_REFRESH_ENABLED = "touch_refresh_enabled"

        private const val DEFAULT_PAGES_PER_REFRESH = 5
        private const val DEFAULT_SCREEN_BRIGHTNESS = 0
        private const val DEFAULT_SCREEN_BRIGHTNESS_COLOR = 0
        const val MAX_SCREEN_BRIGHTNESS = 206
        const val MAX_SCREEN_BRIGHTNESS_COLOR = 100

        private const val GLOBAL_KEY_TOUCH_REFRESH_ENABLED = "refresh_paper_auto_enabled"
        private const val GLOBAL_KEY_PAGES_PER_REFRESH = "refresh_paper_pages_per_refresh"
        private const val GLOBAL_KEY_HOME_LAUNCHER_COMPONENT = "refresh_paper_home_launcher_component"
        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS = "screen_brightness"
        private const val GLOBAL_KEY_SCREEN_BRIGHTNESS_COLOR = "screen_brightness_color"

        const val DEFAULT_HOME_LAUNCHER_PACKAGE = "cn.modificator.launcher"
        private const val DEFAULT_HOME_LAUNCHER_CLASS = "cn.modificator.launcher.Launcher"
        const val DEFAULT_HOME_LAUNCHER_COMPONENT =
            "$DEFAULT_HOME_LAUNCHER_PACKAGE/$DEFAULT_HOME_LAUNCHER_CLASS"
    }
}
