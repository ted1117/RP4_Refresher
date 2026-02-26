package com.hidsquid.refreshpaper.hook

import android.os.SystemClock
import com.highcapable.yukihookapi.hook.log.YLog
import com.hidsquid.refreshpaper.ModulePrefs
import de.robv.android.xposed.XSharedPreferences

object HookPrefs {

    private const val MODULE_PACKAGE = "com.hidsquid.refreshpaper"
    private const val RELOAD_INTERVAL_MS = 250L

    @Volatile
    private var prefs: XSharedPreferences? = null

    @Volatile
    private var lastReloadAt = 0L

    private val lock = Any()

    private fun getOrCreatePrefs(): XSharedPreferences {
        prefs?.let { return it }
        synchronized(lock) {
            prefs?.let { return it }
            return XSharedPreferences(MODULE_PACKAGE, ModulePrefs.PREFS_NAME).also {
                runCatching { it.makeWorldReadable() }
                runCatching { it.reload() }
                prefs = it
                lastReloadAt = SystemClock.uptimeMillis()
            }
        }
    }

    private fun maybeReload(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastReloadAt < RELOAD_INTERVAL_MS) return
        synchronized(lock) {
            val checkedNow = SystemClock.uptimeMillis()
            if (!force && checkedNow - lastReloadAt < RELOAD_INTERVAL_MS) return
            runCatching {
                getOrCreatePrefs().reload()
                lastReloadAt = checkedNow
            }.onFailure {
                YLog.error("HookPrefs reload failed: ${it.message}")
            }
        }
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean,
        forceReload: Boolean = false
    ): Boolean {
        maybeReload(forceReload)
        return runCatching {
            getOrCreatePrefs().getBoolean(key, defaultValue)
        }.getOrElse {
            YLog.error("HookPrefs boolean read failed key=$key: ${it.message}")
            defaultValue
        }
    }

    fun getInt(
        key: String,
        defaultValue: Int,
        forceReload: Boolean = false
    ): Int {
        maybeReload(forceReload)
        return runCatching {
            getOrCreatePrefs().getInt(key, defaultValue)
        }.getOrElse {
            YLog.error("HookPrefs int read failed key=$key: ${it.message}")
            defaultValue
        }
    }

    fun getString(
        key: String,
        defaultValue: String,
        forceReload: Boolean = false
    ): String {
        maybeReload(forceReload)
        return runCatching {
            getOrCreatePrefs().getString(key, null)
        }.getOrElse {
            YLog.error("HookPrefs string read failed key=$key: ${it.message}")
            null
        }?.takeIf { it.isNotBlank() } ?: defaultValue
    }
}
