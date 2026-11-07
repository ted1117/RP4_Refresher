package com.hidsquid.refreshpaper.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.BuildConfig
import com.hidsquid.refreshpaper.ModulePrefs

object SystemUIHook {

    private val PACKAGE_REFRESH_PAPER = BuildConfig.APPLICATION_ID
    private const val PACKAGE_RIDI_PAPER = "com.ridi.paper"
    private val CLASS_BRIGHTNESS_ACTIVITY = "$PACKAGE_REFRESH_PAPER.brightness.BrightnessActivity"
    private val ACTION_OPEN_QUICK_SETTINGS = "$PACKAGE_REFRESH_PAPER.ACTION_OPEN_QUICK_SETTINGS"
    private const val ACTION_RIDI_SHOW_SETTINGS = "com.ridi.paper.ACTION.SHOW_SETTINGS"
    private const val ACTION_RIDI_SHOW_BRIGHTNESS = "com.ridi.paper.ACTION.SHOW_BRIGHTNESS"

    private val DEFAULT_HOME_COMPONENT = ComponentName(
        "cn.modificator.launcher",
        "cn.modificator.launcher.Launcher"
    )

    fun inject(param: PackageParam) {
        param.loadApp("com.android.systemui") {
            YLog.debug("Running in SystemUI Hook")

            runCatching {
                "com.android.systemui.screenshot.GlobalScreenshot".toClass()
                    .resolve()
                    .firstMethod {
                        name = "saveScreenshotInWorkerThread"
                        parameters(Runnable::class.java)
                    }
                    .hook {
                        before {
                            val context = appContext ?: return@before
                            val originalFinisher = args[0] as? Runnable ?: return@before
                            args[0] = Runnable {
                                runCatching { originalFinisher.run() }.onFailure {
                                    YLog.error("Wrapped screenshot finisher failed: ${it.message}")
                                }
                                if (isScreenshotToastEnabled()) {
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

            // Each lookup is failure-safe: a missing id only disables that button.
            fun readRidiId(fieldName: String): Int? = runCatching {
                "com.android.systemui.R\$id".toClass()
                    .resolve()
                    .firstField { name = fieldName }
                    .get<Int>()
            }.onFailure {
                YLog.error("R.id.$fieldName lookup failed, button not wired: ${it.message}")
            }.getOrNull()

            val ridiHomeButtonId = readRidiId("ridi_status_bar_button_home")
            val ridiSettingsButtonId = readRidiId("ridi_status_bar_button_settings")
            val ridiBrightnessButtonId = readRidiId("ridi_status_bar_button_brightness")

            // BubbleController.isForegroundApp is resolved once, lazily; null if unavailable.
            val isForegroundAppMethod by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                runCatching {
                    "com.android.systemui.bubbles.BubbleController".toClass()
                        .resolve()
                        .firstMethod {
                            name = "isForegroundApp"
                            parameters(Context::class.java, String::class.java)
                        }
                }.onFailure {
                    YLog.error("BubbleController.isForegroundApp unavailable: ${it.message}")
                }.getOrNull()
            }

            runCatching {
                "com.android.systemui.statusbar.ridi.RidiStatusBarFragment".toClass()
                    .resolve()
                    .firstMethod {
                        name = "onViewCreated"
                        parameters(View::class.java, Bundle::class.java)
                    }
                    .hook {
                        after {
                            // Never let an exception escape into SystemUI.
                            runCatching {
                                val view = args[0] as? View ?: return@runCatching
                                val userHandleAll = runCatching {
                                    UserHandle::class.java.resolve()
                                        .firstField { name = "ALL" }
                                        .get<UserHandle>()
                                }.getOrNull()

                                fun isRidiForeground(): Boolean {
                                    val method = isForegroundAppMethod ?: return false
                                    return runCatching {
                                        method.invoke<Boolean>(appContext, PACKAGE_RIDI_PAPER) ?: false
                                    }.getOrDefault(false)
                                }

                                fun launchModuleFeature(pkg: String, cls: String) {
                                    try {
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

                                fun sendBroadcastToAll(intent: Intent) {
                                    if (userHandleAll == null) {
                                        YLog.error("UserHandle.ALL unavailable, broadcast skipped")
                                        return
                                    }
                                    appContext?.sendBroadcastAsUser(intent, userHandleAll)
                                }

                                fun launchRidiFeature(intentString: String) {
                                    sendBroadcastToAll(Intent(intentString))
                                }

                                fun requestQuickSettingsDialog() {
                                    val intent = Intent(ACTION_OPEN_QUICK_SETTINGS).apply {
                                        `package` = PACKAGE_REFRESH_PAPER
                                    }
                                    sendBroadcastToAll(intent)
                                }

                                fun findButton(id: Int?, label: String): ImageButton? {
                                    if (id == null) return null
                                    return view.findViewById<ImageButton?>(id).also {
                                        if (it == null) YLog.error("$label button view missing, not wired")
                                    }
                                }

                                findButton(ridiHomeButtonId, "home")?.setOnClickListener {
                                    try {
                                        val configuredHome = HookPrefs.getString(
                                            ModulePrefs.KEY_HOME_LAUNCHER_COMPONENT,
                                            DEFAULT_HOME_COMPONENT.flattenToString(),
                                            forceReload = true
                                        )
                                        val homeComponent = configuredHome.let(ComponentName::unflattenFromString)
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

                                findButton(ridiSettingsButtonId, "settings")?.setOnClickListener {
                                    if (isRidiForeground()) {
                                        YLog.debug("Ridi foreground detected, fallback to Ridi settings")
                                        launchRidiFeature(ACTION_RIDI_SHOW_SETTINGS)
                                    } else {
                                        requestQuickSettingsDialog()
                                    }
                                }

                                findButton(ridiBrightnessButtonId, "brightness")?.setOnClickListener {
                                    if (isRidiForeground()) {
                                        YLog.debug("Ridi foreground detected, fallback to Ridi brightness")
                                        launchRidiFeature(ACTION_RIDI_SHOW_BRIGHTNESS)
                                    } else {
                                        launchModuleFeature(
                                            PACKAGE_REFRESH_PAPER,
                                            CLASS_BRIGHTNESS_ACTIVITY
                                        )
                                    }
                                }
                            }.onFailure {
                                YLog.error("RidiStatusBarFragment wiring failed: ${it.message}")
                            }
                        }
                    }
            }.onFailure {
                YLog.error("Failed to hook RidiStatusBarFragment.onViewCreated: ${it.message}")
            }
        }
    }

    private fun isScreenshotToastEnabled(): Boolean {
        return HookPrefs.getBoolean(
            ModulePrefs.KEY_SCREENSHOT_TOAST_ENABLED,
            true,
            forceReload = true
        )
    }
}
