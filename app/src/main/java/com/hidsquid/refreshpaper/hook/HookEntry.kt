package com.hidsquid.refreshpaper.hook

import android.app.AndroidAppHelper
import android.content.Intent
import android.provider.Settings
import android.view.MotionEvent
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {
        const val ACTION_REFRESH_SCREEN = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"

        const val GLOBAL_KEY_AUTO_REFRESH_ENABLED = "refresh_paper_auto_enabled"
        const val GLOBAL_KEY_PAGES_PER_REFRESH = "refresh_paper_pages_per_refresh"
    }

    private var touchCount = 0

    override fun onInit() = configs { debugLog { tag = "RefreshPaperHook" } }

    override fun onHook() = encase {
        loadSystem {
            "com.android.server.wm.WindowManagerService".toClass()
                .method {
                    name = "isSecureLocked"
                    paramCount = 1
                    param("com.android.server.wm.WindowState")
                }
                .hook {
                    before {
                        val ctx = AndroidAppHelper.currentApplication()
                        val cr = ctx.contentResolver
                        val isBypassOn = try {
                            Settings.Global.getInt(cr, "secure_bypass_on", 1) == 1
                        } catch (_: Throwable) {
                            true
                        }
                        if (isBypassOn) result = false
                    }
                }
        }

        loadApp {
            if (packageName == "com.ridi.paper" ||
                packageName == "com.android.webview" ||
                packageName == "com.google.android.webview") {
                return@loadApp
            }

            "android.app.Activity".toClass()
                .method { name = "dispatchTouchEvent"; param(MotionEvent::class.java) }
                .hook {
                    before {
                        val isAutoRefreshEnabled = try {
                            Settings.Global.getInt(
                                appContext!!.contentResolver,
                                GLOBAL_KEY_AUTO_REFRESH_ENABLED,
                                0
                            ) == 1
                        } catch (_: Throwable) {
                            false
                        }

                        if (!isAutoRefreshEnabled) return@before

                        val ev = args[0] as MotionEvent
                        if (ev.action != MotionEvent.ACTION_DOWN) return@before

                        val trigger = try {
                            Settings.Global.getInt(
                                appContext!!.contentResolver,
                                GLOBAL_KEY_PAGES_PER_REFRESH,
                                5
                            )
                        } catch (_: Throwable) {
                            5
                        }

                        touchCount++
                        if (touchCount >= trigger) {
                            touchCount = 0

                            appContext!!.sendBroadcast(
                                Intent(ACTION_REFRESH_SCREEN).apply {
                                    `package` = "com.hidsquid.refreshpaper"
                                }
                            )
                            YLog.debug("Triggered refresh in $packageName (trigger=$trigger)")
                        }
                    }
                }
        }

        loadApp {
            if (packageName != "com.kyobo.ebook.eink") return@loadApp

            val WIDEVINE = "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"

            "android.media.MediaDrm".toClass()
                .method {
                    name = "isCryptoSchemeSupported"
                    paramCount(1)
                    returnType = Boolean::class.javaPrimitiveType!!
                }
                .hook {
                    before {
                        if (args[0].toString().equals(WIDEVINE, ignoreCase = true)) {
                            result = false
                        }
                    }
                }
        }
    }
}