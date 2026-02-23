package com.hidsquid.refreshpaper.hook

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

object SystemUIHook {

    private const val GLOBAL_KEY_HOME_LAUNCHER_COMPONENT = "refresh_paper_home_launcher_component"
    private const val GLOBAL_KEY_SCREENSHOT_TOAST_ENABLED = "refresh_paper_screenshot_toast_enabled"
    private val DEFAULT_HOME_COMPONENT = ComponentName(
        "cn.modificator.launcher",
        "cn.modificator.launcher.Launcher"
    )

    fun inject(param: PackageParam) {
        param.loadApp("com.android.systemui") {
            YLog.debug("Running in SystemUI Hook")

            runCatching {
                "com.android.systemui.screenshot.GlobalScreenshot".toClass()
                    .method {
                        name = "saveScreenshotInWorkerThread"
                        paramCount = 1
                        param(Runnable::class.java)
                    }
                    .hook {
                        before {
                            val context = appContext ?: return@before
                            val originalFinisher = args[0] as? Runnable ?: return@before
                            args[0] = Runnable {
                                runCatching { originalFinisher.run() }.onFailure {
                                    YLog.error("Wrapped screenshot finisher failed: ${it.message}")
                                }
                                if (isScreenshotToastEnabled(context)) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, "스크린샷 촬영", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            }
                        }
                    }
            }.onFailure {
                YLog.error("Failed to hook GlobalScreenshot finisher: ${it.message}")
            }

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
                                val configuredHome = try {
                                    Settings.Global.getString(
                                        appContext?.contentResolver,
                                        GLOBAL_KEY_HOME_LAUNCHER_COMPONENT
                                    )
                                } catch (_: Throwable) {
                                    null
                                }
                                val homeComponent = configuredHome?.let(ComponentName::unflattenFromString)
                                    ?: DEFAULT_HOME_COMPONENT

                                val intent = Intent().apply {
                                    component = homeComponent
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext?.startActivity(intent)
                            } catch (e: Exception) {
                                YLog.error("Home launch failed, fallback to default: ${e.message}")
                                try {
                                    val fallbackIntent = Intent().apply {
                                        component = DEFAULT_HOME_COMPONENT
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    appContext?.startActivity(fallbackIntent)
                                } catch (fallbackError: Exception) {
                                    YLog.error("Default home launch failed: ${fallbackError.message}")
                                }
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

    private fun isScreenshotToastEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                GLOBAL_KEY_SCREENSHOT_TOAST_ENABLED,
                1
            ) == 1
        }.getOrDefault(true)
    }
}
