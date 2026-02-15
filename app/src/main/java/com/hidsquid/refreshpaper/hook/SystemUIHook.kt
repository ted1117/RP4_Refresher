package com.hidsquid.refreshpaper.hook

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

object SystemUIHook {

    fun inject(param: PackageParam) {
        param.loadApp("com.android.systemui") {
            YLog.debug("Running in SystemUI Hook")

            val ridiHomeButtonId = "com.android.systemui.R\$id".toClass()
                .field { name = "ridi_status_bar_button_home" }.get().int()
            val ridiSettingsButtonId = "com.android.systemui.R\$id".toClass()
                .field { name = "ridi_status_bar_button_settings" }.get().int()
            val ridiBrightnessButtonId = "com.android.systemui.R\$id".toClass()
                .field { name = "ridi_status_bar_button_brightness" }.get().int()

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

                        homeBtn.setOnClickListener {
                            try {
                                val intent = Intent().apply {
                                    component = ComponentName(
                                        "cn.modificator.launcher",
                                        "cn.modificator.launcher.Launcher"
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext?.startActivity(intent)
                            } catch (e: Exception) {
                                YLog.error("Home launch failed: ${e.message}")
                            }
                        }

                        settingsBtn.setOnClickListener {
                            launchIsolatedActivity(
                                "com.ridi.paper",
                                "com.ridi.books.viewer.main.activity.SettingsActivity"
                            )
                        }

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
