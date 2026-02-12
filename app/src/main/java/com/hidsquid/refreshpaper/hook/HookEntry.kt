package com.hidsquid.refreshpaper.hook

import android.app.AndroidAppHelper
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.field
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

        loadApp("com.android.systemui") {
            YLog.debug("Running in SystemUI Hook")

            val ridiHomeButtonId = "com.android.systemui.R\$id".toClass().field { name = "ridi_status_bar_button_home" }.get().int()
            val ridiSettingsButtonId = "com.android.systemui.R\$id".toClass().field { name = "ridi_status_bar_button_settings" }.get().int()
            val ridiBrightnessButtonId = "com.android.systemui.R\$id".toClass().field { name = "ridi_status_bar_button_brightness" }.get().int()

            "com.android.systemui.statusbar.ridi.RidiStatusBarFragment".toClass().apply {

                method { name = "updateSetupcomplete" }.hook {
                    after {
                        val homeButton = this.instanceClass!!.field { name = "mImageButtonHome" }.get().any() as ImageButton
                        val backButton = this.instanceClass!!.field { name = "mImageButtonBack" }.get().any() as ImageButton
                        val settingsButton = this.instanceClass!!.field { name = "mImageButtonSettings" }.get().any() as ImageButton

                        homeButton.isEnabled = true
                        backButton.isEnabled = true
                        settingsButton.isEnabled = true
                    }
                }

                method {
                    name = "onViewCreated"
                    param(View::class.java, Bundle::class.java)
                }.hook {
                    after {
                        val view = args[0] as View
                        val homeBtn = view.findViewById<ImageButton>(ridiHomeButtonId)
                        val settingsBtn = view.findViewById<ImageButton>(ridiSettingsButtonId)
                        val brightnessBtn = view.findViewById<ImageButton>(ridiBrightnessButtonId)

                        val isTargetActivityOnTop = { pkg: String, cls: String ->
                            try {
                                val am = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                                val top = am?.getRunningTasks(1)?.firstOrNull()?.topActivity
                                top == ComponentName(pkg, cls)
                            } catch (e: Throwable) {
                                YLog.error("Failed to inspect top activity: ${e.message}")
                                false
                            }
                        }

                        fun launchIsolatedActivity(pkg: String, cls: String) {
                            try {
                                if (isTargetActivityOnTop(pkg, cls)) {
                                    YLog.debug("Skip launch, already on top: $cls")
                                    return
                                }

                                val intent = Intent().apply {
                                    component = ComponentName(pkg, cls)

                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                }
                                appContext?.startActivity(intent)
                                YLog.debug("Launched isolated activity: $cls")
                            } catch (e: Exception) {
                                YLog.error("Failed to launch $cls: ${e.message}")
                            }
                        }

                        // 홈 버튼: 런처 실행
                        homeBtn.setOnClickListener {
                            try {
                                val intent = Intent().apply {
                                    component = ComponentName("cn.modificator.launcher", "cn.modificator.launcher.Launcher")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext?.startActivity(intent)
                            } catch (e: Exception) {
                                YLog.error("Home launch failed: ${e.message}")
                            }
                        }

                        // 설정 버튼: 리디 설정 액티비티 독립 실행
                        settingsBtn.setOnClickListener {
                            launchIsolatedActivity(
                                "com.ridi.paper",
                                "com.ridi.books.viewer.main.activity.SettingsActivity"
                            )
                        }

                        // 밝기 버튼: 밝기 조절 팝업 독립 실행
                        brightnessBtn.setOnClickListener {
                            launchIsolatedActivity(
                                "com.ridi.paper",
                                "com.ridi.books.viewer.common.activity.ExtraBrightnessActivity"
                            )
                        }
                    }
                }
            }
        }
    }
}
