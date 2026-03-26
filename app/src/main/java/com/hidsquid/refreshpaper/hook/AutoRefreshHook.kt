package com.hidsquid.refreshpaper.hook

import android.content.Intent
import android.view.MotionEvent
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs

object AutoRefreshHook {

    private var touchCount = 0
    private const val DEFAULT_TRIGGER = 5

    private fun isTouchRefreshActive(): Boolean {
        val autoEnabled = HookPrefs.getBoolean(
            ModulePrefs.KEY_AUTO_REFRESH_ENABLED,
            false
        )
        if (!autoEnabled) return false
        return HookPrefs.getBoolean(
            ModulePrefs.KEY_TOUCH_REFRESH_ENABLED,
            false
        )
    }

    private fun readTrigger(): Int =
        HookPrefs.getInt(
            ModulePrefs.KEY_PAGES_PER_REFRESH,
            DEFAULT_TRIGGER
        ).coerceAtLeast(1)

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
                        val ev = args[0] as? MotionEvent ?: return@before

                        if (ev.action != MotionEvent.ACTION_DOWN) return@before

                        if (!isTouchRefreshActive()) {
                            touchCount = 0
                            return@before
                        }
                        val trigger = readTrigger()

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
