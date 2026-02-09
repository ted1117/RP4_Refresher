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

    companion object {
        private const val PREFS_NAME = "MyPrefs"

        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"
        private const val PREF_KEY_TOUCH_REFRESH_ENABLED = "touch_refresh_enabled"

        private const val DEFAULT_PAGES_PER_REFRESH = 5

        private const val GLOBAL_KEY_TOUCH_REFRESH_ENABLED = "refresh_paper_auto_enabled"
        private const val GLOBAL_KEY_PAGES_PER_REFRESH = "refresh_paper_pages_per_refresh"
    }
}
