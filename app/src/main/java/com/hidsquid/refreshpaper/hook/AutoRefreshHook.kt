package com.hidsquid.refreshpaper.hook

import android.content.Intent
import android.provider.Settings
import android.view.MotionEvent
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

object AutoRefreshHook {

    private var touchCount = 0

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
                        val ev = args[0] as? MotionEvent ?: return@before

                        if (ev.action != MotionEvent.ACTION_DOWN) return@before

                        val isAutoRefreshEnabled = try {
                            Settings.Global.getInt(
                                appContext!!.contentResolver,
                                HookEntry.GLOBAL_KEY_AUTO_REFRESH_ENABLED,
                                0
                            ) == 1
                        } catch (_: Throwable) {
                            false
                        }

                        if (!isAutoRefreshEnabled) return@before

                        val trigger = try {
                            Settings.Global.getInt(
                                appContext!!.contentResolver,
                                HookEntry.GLOBAL_KEY_PAGES_PER_REFRESH,
                                5
                            )
                        } catch (_: Throwable) {
                            5
                        }

                        touchCount++
                        if (touchCount >= trigger) {
                            touchCount = 0

                            appContext!!.sendBroadcast(
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