package com.hidsquid.refreshpaper

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log

class SettingsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs = createPrefs(appContext)

    private val cr = context.contentResolver

    fun isAutoRefreshEnabled(): Boolean =
        readBoolean(ModulePrefs.KEY_AUTO_REFRESH_ENABLED, false)

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_AUTO_REFRESH_ENABLED, enabled)
            .commit()
    }

    fun isScreenshotToastEnabled(): Boolean {
        return readBoolean(ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED, true)
    }

    fun setScreenshotToastEnabled(enabled: Boolean): Boolean {
        return commitOrRestore(ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED) { putBoolean(ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED, enabled) }
    }

    fun isSecureBypassEnabled(): Boolean {
        return readBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, true)
    }

    fun setSecureBypassEnabled(enabled: Boolean): Boolean {
        return commitOrRestore(ModulePrefs.KEY_SECURE_BYPASS_ENABLED) { putBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, enabled) }
    }

    fun isPageKeyTapEnabled(): Boolean {
        return readBoolean(ModulePrefs.KEY_PAGE_KEY_TAP_ENABLED, false)
    }

    fun setPageKeyTapEnabled(enabled: Boolean): Boolean {
        return commitOrRestore(ModulePrefs.KEY_PAGE_KEY_TAP_ENABLED) { putBoolean(ModulePrefs.KEY_PAGE_KEY_TAP_ENABLED, enabled) }
    }

    fun getPageKeyTapTargetPackages(): Set<String> {
        return readStringSet(ModulePrefs.KEY_PAGE_KEY_TAP_TARGET_PACKAGES, emptySet())
            ?.mapNotNull { value ->
                value?.trim()?.takeIf { it.isNotEmpty() }
            }
            ?.toSet()
            .orEmpty()
    }

    fun setPageKeyTapTargetPackages(packages: Set<String>): Boolean {
        val sanitized = packages
            .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .toSet()

        return commitOrRestore(ModulePrefs.KEY_PAGE_KEY_TAP_TARGET_PACKAGES) { putStringSet(ModulePrefs.KEY_PAGE_KEY_TAP_TARGET_PACKAGES, sanitized) }
    }

    fun isPageKeyTapTargetPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return getPageKeyTapTargetPackages().contains(packageName)
    }

    fun getPageKeySwapTargetPackages(): Set<String> {
        return readStringSet(ModulePrefs.KEY_PAGE_KEY_SWAP_TARGET_PACKAGES, emptySet())
            ?.mapNotNull { value ->
                value?.trim()?.takeIf { it.isNotEmpty() }
            }
            ?.toSet()
            .orEmpty()
    }

    fun setPageKeySwapTargetPackages(packages: Set<String>): Boolean {
        val sanitized = packages
            .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .toSet()

        return commitOrRestore(ModulePrefs.KEY_PAGE_KEY_SWAP_TARGET_PACKAGES) { putStringSet(ModulePrefs.KEY_PAGE_KEY_SWAP_TARGET_PACKAGES, sanitized) }
    }

    fun isPageKeySwapTargetPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return getPageKeySwapTargetPackages().contains(packageName)
    }

    fun getDaemonTouchRefreshTargetPackages(): Set<String> {
        return readStringSet(ModulePrefs.KEY_DAEMON_TOUCH_REFRESH_TARGET_PACKAGES, emptySet())
            ?.mapNotNull { value ->
                value?.trim()?.takeIf { it.isNotEmpty() }
            }
            ?.toSet()
            .orEmpty()
    }

    fun setDaemonTouchRefreshTargetPackages(packages: Set<String>): Boolean {
        val sanitized = packages
            .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .toSet()

        return commitOrRestore(ModulePrefs.KEY_DAEMON_TOUCH_REFRESH_TARGET_PACKAGES) { putStringSet(ModulePrefs.KEY_DAEMON_TOUCH_REFRESH_TARGET_PACKAGES, sanitized) }
    }

    fun isDaemonTouchRefreshTargetPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return getDaemonTouchRefreshTargetPackages().contains(packageName)
    }

    fun isManualRefreshEnabled(): Boolean =
        readBoolean(ModulePrefs.KEY_MANUAL_REFRESH_ENABLED, false)

    fun setManualRefreshEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(ModulePrefs.KEY_MANUAL_REFRESH_ENABLED, enabled)
            .commit()
    }

    fun getPagesPerRefresh(): Int =
        readInt(ModulePrefs.KEY_PAGES_PER_REFRESH, DEFAULT_PAGES_PER_REFRESH)

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
        readString(ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT, null)
            ?.takeIf { it.isNotBlank() }
            ?: SharedConstants.DEFAULT_HOME_LAUNCHER_COMPONENT

    fun setHomeLauncherComponent(componentName: String): Boolean =
        commitOrRestore(ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT) { putString(ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT, componentName) }

    fun getF1Action(default: ButtonAction = ButtonAction.BACK): ButtonAction =
        ButtonAction.fromId(readInt(ModulePrefs.KEY_F1_ACTION, default.id)) ?: default

    fun setF1Action(action: ButtonAction): Boolean =
        commitOrRestore(ModulePrefs.KEY_F1_ACTION) { putInt(ModulePrefs.KEY_F1_ACTION, action.id) }

    fun getF1LongPressAction(default: ButtonAction = ButtonAction.BACK): ButtonAction =
        ButtonAction.fromId(readInt(ModulePrefs.KEY_F1_LONG_PRESS_ACTION, default.id)) ?: default

    fun setF1LongPressAction(action: ButtonAction): Boolean =
        commitOrRestore(ModulePrefs.KEY_F1_LONG_PRESS_ACTION) { putInt(ModulePrefs.KEY_F1_LONG_PRESS_ACTION, action.id) }

    fun getPageUpLongPressAction(default: ButtonAction = ButtonAction.NONE): ButtonAction =
        ButtonAction.fromId(readInt(ModulePrefs.KEY_PAGE_UP_LONG_PRESS_ACTION, default.id)) ?: default

    fun setPageUpLongPressAction(action: ButtonAction): Boolean =
        commitOrRestore(ModulePrefs.KEY_PAGE_UP_LONG_PRESS_ACTION) { putInt(ModulePrefs.KEY_PAGE_UP_LONG_PRESS_ACTION, action.id) }

    fun getPageDownLongPressAction(default: ButtonAction = ButtonAction.NONE): ButtonAction =
        ButtonAction.fromId(readInt(ModulePrefs.KEY_PAGE_DOWN_LONG_PRESS_ACTION, default.id)) ?: default

    fun setPageDownLongPressAction(action: ButtonAction): Boolean =
        commitOrRestore(ModulePrefs.KEY_PAGE_DOWN_LONG_PRESS_ACTION) { putInt(ModulePrefs.KEY_PAGE_DOWN_LONG_PRESS_ACTION, action.id) }

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


        const val DEFAULT_SHUTDOWN_TIMER_HOURS = 1
        const val DEFAULT_SCREEN_OFF_TIMEOUT_MILLIS = 60_000
    }

    private fun readBoolean(key: String, default: Boolean): Boolean =
        readOrDefault(key, default) { prefs.getBoolean(key, default) }

    private fun readInt(key: String, default: Int): Int =
        readOrDefault(key, default) { prefs.getInt(key, default) }

    private fun readString(key: String, default: String?): String? =
        readOrDefault(key, default) { prefs.getString(key, default) }

    private fun readStringSet(key: String, default: Set<String>): Set<String>? =
        readOrDefault(key, default) { prefs.getStringSet(key, default) }

    private inline fun <T> readOrDefault(key: String, default: T, read: () -> T): T =
        try {
            read()
        } catch (e: ClassCastException) {
            Log.w(TAG, "Wrong type for pref $key, using default", e)
            default
        }

    /** Commits [write]; if the disk write fails, restores the previous in-memory value of [key] so memory matches the file. */
    private fun commitOrRestore(key: String, write: SharedPreferences.Editor.() -> Unit): Boolean {
        val hadKey = prefs.contains(key)
        val previous = prefs.all[key]
        val ok = prefs.edit().apply(write).commit()
        if (!ok) {
            val restore = prefs.edit()
            when {
                !hadKey -> restore.remove(key)
                previous is Boolean -> restore.putBoolean(key, previous)
                previous is Int -> restore.putInt(key, previous)
                previous is String -> restore.putString(key, previous)
                previous is Set<*> -> restore.putStringSet(key, previous.filterIsInstance<String>().toSet())
                else -> restore.remove(key)
            }
            // apply() updates memory immediately; its disk write just re-writes the old content.
            restore.apply()
            Log.w(TAG, "Failed to save pref $key, restored previous value")
        }
        return ok
    }

    private fun createPrefs(context: Context) = try {
        @SuppressLint("WorldReadableFiles")
        context.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (e: SecurityException) {
        Log.w(TAG, "MODE_WORLD_READABLE unsupported, fallback to MODE_PRIVATE", e)
        context.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE)
    }
}
