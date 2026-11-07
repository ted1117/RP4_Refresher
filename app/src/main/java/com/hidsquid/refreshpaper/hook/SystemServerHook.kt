package com.hidsquid.refreshpaper.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SystemServerHook {

    private const val SAFETY_POLL_INTERVAL_MS = 30_000L

    // isSecureLocked runs under the WM global lock, so the hook only reads this cached value.
    // LSPosed's prefs file watcher triggers a reload on change. Because watcher registration
    // can fail silently, a 30s poll (a hasFileChanged stat, reloading only on change) is the
    // fallback.
    @Volatile
    private var secureBypassEnabled = true

    private val refresherStarted = AtomicBoolean(false)

    private fun readBypassFlag() = HookPrefs.peekBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, true)

    // Must never throw: an exception would cancel the periodic task.
    private fun refreshFromDisk() {
        try {
            if (HookPrefs.reloadIfChanged()) secureBypassEnabled = readBypassFlag()
        } catch (t: Throwable) {
            YLog.error("Secure bypass pref refresh failed: ${t.message}")
        }
    }

    private fun startRefresher() {
        if (!refresherStarted.compareAndSet(false, true)) return
        runCatching {
            HookPrefs.registerChangeListener { _, _ -> refreshFromDisk() }
        }.onFailure {
            YLog.error("Failed to register pref change listener: ${it.message}")
        }
        runCatching {
            val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "RefreshPaper-prefs").apply { isDaemon = true }
            }
            scheduler.scheduleWithFixedDelay(
                { refreshFromDisk() },
                SAFETY_POLL_INTERVAL_MS,
                SAFETY_POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
        }.onFailure {
            YLog.error("Failed to start pref safety poll: ${it.message}")
        }
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
