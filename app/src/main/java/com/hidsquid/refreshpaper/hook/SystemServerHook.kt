package com.hidsquid.refreshpaper.hook

import android.os.SystemClock
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object SystemServerHook {

    private const val REFRESH_CHECK_INTERVAL_MS = 3_000L

    // isSecureLocked runs under the WM global lock, so the hook only reads this cached value.
    // A background thread, at most once per interval, asks XSharedPreferences whether the
    // file changed (hasFileChanged) and reloads + re-caches only then; no disk I/O on the hot path.
    @Volatile
    private var secureBypassEnabled = true

    @Volatile
    private var lastCheckAt = 0L

    private val refreshing = AtomicBoolean(false)

    private val refreshExecutor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "RefreshPaper-prefs").apply { isDaemon = true }
        }
    }

    private fun readBypassFlag() = HookPrefs.peekBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, true)

    private fun scheduleRefreshIfDue() {
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckAt < REFRESH_CHECK_INTERVAL_MS) return
        if (!refreshing.compareAndSet(false, true)) return
        lastCheckAt = now
        runCatching {
            refreshExecutor.execute {
                try {
                    if (HookPrefs.reloadIfChanged()) secureBypassEnabled = readBypassFlag()
                } catch (t: Throwable) {
                    YLog.error("Secure bypass pref refresh failed: ${t.message}")
                } finally {
                    refreshing.set(false)
                }
            }
        }.onFailure { refreshing.set(false) }
    }

    fun inject(param: PackageParam) {
        param.loadSystem {
            runCatching {
                "com.android.server.wm.WindowManagerService".toClass()
                    .resolve()
                    .firstMethod {
                        name = "isSecureLocked"
                        parameters("com.android.server.wm.WindowState".toClass())
                    }
                    .hook {
                        before {
                            scheduleRefreshIfDue()
                            if (secureBypassEnabled) result = false
                        }
                    }
                // Initial value is read here at install time, not on the hook path.
                secureBypassEnabled = runCatching { readBypassFlag() }.getOrDefault(true)
                lastCheckAt = SystemClock.uptimeMillis()
            }.onFailure {
                YLog.error("Failed to hook isSecureLocked: ${it.message}")
            }
        }
    }
}
