package com.hidsquid.refreshpaper

import android.content.Context

class SettingsRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoRefreshEnabled(): Boolean =
        prefs.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false)

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, enabled).apply()
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
    }

    companion object {
        private const val PREFS_NAME = "MyPrefs"

        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"

        private const val DEFAULT_PAGES_PER_REFRESH = 5
    }
}