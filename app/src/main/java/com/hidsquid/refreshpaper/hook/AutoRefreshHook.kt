package com.hidsquid.refreshpaper.hook

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.view.MotionEvent
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

object AutoRefreshHook {

    private var touchCount = 0
    private const val DEFAULT_TRIGGER = 5

    @Volatile
    private var cachedAutoRefreshEnabled = false

    @Volatile
    private var cachedTrigger = DEFAULT_TRIGGER

    @Volatile
    private var cacheInitialized = false

    @Volatile
    private var observerInitAttempted = false

    @Volatile
    private var observerActive = false

    private val initLock = Any()
    private var observerThread: HandlerThread? = null
    private var settingsObserver: ContentObserver? = null

    private fun readAutoEnabled(context: Context): Boolean =
        try {
            Settings.Global.getInt(
                context.contentResolver,
                HookEntry.GLOBAL_KEY_AUTO_REFRESH_ENABLED,
                0
            ) == 1
        } catch (_: Throwable) {
            false
        }

    private fun readTrigger(context: Context): Int =
        try {
            Settings.Global.getInt(
                context.contentResolver,
                HookEntry.GLOBAL_KEY_PAGES_PER_REFRESH,
                DEFAULT_TRIGGER
            ).coerceAtLeast(1)
        } catch (_: Throwable) {
            DEFAULT_TRIGGER
        }

    private fun refreshCache(context: Context, source: String) {
        val triggerBefore = cachedTrigger
        runCatching {
            cachedAutoRefreshEnabled = readAutoEnabled(context)
            cachedTrigger = readTrigger(context)
            if (source != "fallback") {
                YLog.debug(
                    "TouchRefreshCacheUpdated source=$source enabled=$cachedAutoRefreshEnabled trigger=$cachedTrigger"
                )
            }
        }.onFailure {
            // Fail-closed: read failure disables auto refresh.
            cachedAutoRefreshEnabled = false
            cachedTrigger = triggerBefore.coerceAtLeast(1)
            YLog.error("TouchRefreshCacheReadFailed source=$source")
        }
    }

    private fun registerObserver(context: Context) {
        val appCtx = context.applicationContext
        val thread = HandlerThread("refreshpaper-global-observer").apply { start() }
        val handler = Handler(thread.looper)
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshCache(appCtx, source = "observer")
            }
        }

        val enabledUri = Settings.Global.getUriFor(HookEntry.GLOBAL_KEY_AUTO_REFRESH_ENABLED)
        val triggerUri = Settings.Global.getUriFor(HookEntry.GLOBAL_KEY_PAGES_PER_REFRESH)
        appCtx.contentResolver.registerContentObserver(enabledUri, false, observer)
        appCtx.contentResolver.registerContentObserver(triggerUri, false, observer)

        observerThread = thread
        settingsObserver = observer
        observerActive = true
    }

    private fun ensureObserver(context: Context) {
        if (cacheInitialized && observerInitAttempted) return
        synchronized(initLock) {
            if (!cacheInitialized) {
                refreshCache(context, source = "init")
                cacheInitialized = true
            }
            if (!observerInitAttempted) {
                observerInitAttempted = true
                runCatching {
                    registerObserver(context)
                    YLog.debug("TouchRefreshObserverRegistered")
                }.onFailure {
                    observerActive = false
                    YLog.error("TouchRefreshObserverRegisterFailed")
                }
            }
        }
    }

    fun inject(param: PackageParam) {
        param.loadApp {
            if (packageName == "android" ||
                packageName == "com.android.systemui" ||
                packageName == "com.ridi.paper" ||
                packageName == "com.android.webview" ||
                packageName == "com.google.android.webview") {
                return@loadApp
            }

            "android.app.Activity".toClass()
                .method { name = "dispatchTouchEvent"; param(MotionEvent::class.java) }
                .hook {
                    before {
                        val context = appContext ?: return@before
                        ensureObserver(context)

                        val ev = args[0] as? MotionEvent ?: return@before

                        if (ev.action != MotionEvent.ACTION_DOWN) return@before

                        if (!observerActive) {
                            // Observer registration failed; fallback to direct read.
                            refreshCache(context, source = "fallback")
                        }

                        if (!cachedAutoRefreshEnabled) return@before

                        val trigger = cachedTrigger.coerceAtLeast(1)

                        touchCount++
                        if (touchCount >= trigger) {
                            touchCount = 0

                            context.sendBroadcast(
                                Intent(HookEntry.ACTION_REFRESH_SCREEN).apply {
                                    `package` = "com.hidsquid.refreshpaper"
                                }
                            )
                            YLog.debug("Triggered refresh in $packageName (trigger=$trigger)")
                        }
                    }
                }
        }
    }
}
