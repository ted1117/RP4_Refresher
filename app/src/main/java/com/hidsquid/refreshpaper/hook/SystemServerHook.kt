package com.hidsquid.refreshpaper.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SystemServerHook {

    private const val REFRESH_INTERVAL_MS = 1_000L

    // isSecureLocked runs under the WM global lock, so the hook only reads this cached value.
    // A background thread checks every 1s via XSharedPreferences.hasFileChanged (a stat) and
    // reloads + re-caches only when the file changed, independent of window creation.
    @Volatile
    private var secureBypassEnabled = true

    private val refresherStarted = AtomicBoolean(false)

    private fun readBypassFlag() = HookPrefs.peekBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, true)

    private fun startRefresher() {
        if (!refresherStarted.compareAndSet(false, true)) return
        val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "RefreshPaper-prefs").apply { isDaemon = true }
        }
        scheduler.scheduleWithFixedDelay({
            // Must never throw: an exception would cancel the periodic task.
            try {
                if (HookPrefs.reloadIfChanged()) secureBypassEnabled = readBypassFlag()
            } catch (t: Throwable) {
                YLog.error("Secure bypass pref refresh failed: ${t.message}")
            }
        }, REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS)
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
                            if (secureBypassEnabled) result = false
                        }
                    }
                // Initial value is read here at install time, not on the hook path.
                secureBypassEnabled = runCatching { readBypassFlag() }.getOrDefault(true)
                runCatching { startRefresher() }.onFailure {
                    YLog.error("Failed to start pref refresher: ${it.message}")
                }
            }.onFailure {
                YLog.error("Failed to hook isSecureLocked: ${it.message}")
            }
        }
    }
}
