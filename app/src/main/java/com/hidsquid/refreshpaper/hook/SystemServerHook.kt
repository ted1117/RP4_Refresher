package com.hidsquid.refreshpaper.hook

import android.view.KeyEvent
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs

object SystemServerHook {

    @Volatile
    private var isPowerKeyDown = false

    @Volatile
    private var isPageDownKeyDown = false

    @Volatile
    private var isScreenshotChordActive = false

    fun inject(param: PackageParam) {
        param.loadSystem {
            "com.android.server.wm.WindowManagerService".toClass()
                .method {
                    name = "isSecureLocked"
                    paramCount = 1
                    param("com.android.server.wm.WindowState")
                }
                .hook {
                    before {
                        val isBypassOn =
                            HookPrefs.getBoolean(
                                ModulePrefs.KEY_SECURE_BYPASS_ENABLED,
                                true
                            )
                        if (isBypassOn) result = false
                    }
                }
            "com.android.server.policy.PhoneWindowManager".toClass()
                .method {
                    name = "interceptKeyBeforeQueueing"
                    paramCount = 2
                }
                .hook {
                    before {
                        val event = args[0] as? KeyEvent ?: return@before
                        val pwm = this.instance ?: return@before
                        if (!isPowerPageScreenshotEnabled()) {
                            resetChordState()
                            return@before
                        }

                        when (event.keyCode) {
                            KeyEvent.KEYCODE_POWER -> {
                                isPowerKeyDown = event.action == KeyEvent.ACTION_DOWN
                                if (event.action == KeyEvent.ACTION_UP && !isPageDownKeyDown) {
                                    isScreenshotChordActive = false
                                }
                                if (event.action == KeyEvent.ACTION_DOWN && isPageDownKeyDown) {
                                    triggerScreenshotChord(pwm, event.eventTime)
                                }
                            }

                            KeyEvent.KEYCODE_PAGE_DOWN -> {
                                when (event.action) {
                                    KeyEvent.ACTION_DOWN -> {
                                        isPageDownKeyDown = true
                                        if (isPowerKeyDown || getBooleanField(pwm, "mScreenshotChordPowerKeyTriggered")) {
                                            triggerScreenshotChord(pwm, event.eventTime)
                                            // Consume PAGE_DOWN so page-turn action is suppressed during screenshot chord.
                                            result = 0
                                        }
                                    }

                                    KeyEvent.ACTION_UP -> {
                                        isPageDownKeyDown = false
                                        setField(pwm, "mScreenshotChordVolumeDownKeyTriggered", false)
                                        if (isScreenshotChordActive) {
                                            isScreenshotChordActive = false
                                            result = 0
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            "com.android.server.policy.PhoneWindowManager".toClass()
                .method {
                    name = "interceptKeyBeforeDispatching"
                    paramCount = 3
                }
                .hook {
                    before {
                        val pwm = this.instance ?: return@before
                        if (!isPowerPageScreenshotEnabled()) {
                            resetChordState()
                            return@before
                        }

                        val event = args[1] as? KeyEvent ?: return@before
                        if (event.keyCode != KeyEvent.KEYCODE_PAGE_DOWN) return@before

                        if (isScreenshotChordActive || (isPowerKeyDown && isPageDownKeyDown)) {
                            if (event.action == KeyEvent.ACTION_UP) {
                                isScreenshotChordActive = false
                                isPageDownKeyDown = false
                            }
                            // -1 means drop key in dispatch stage.
                            result = -1L
                        }
                    }
                }
        }
    }

    private fun triggerScreenshotChord(instance: Any, eventTime: Long) {
        if (isScreenshotChordActive) return
        isScreenshotChordActive = true
        setField(instance, "mPowerKeyHandled", true)
        setField(instance, "mScreenshotChordPowerKeyTriggered", true)
        setField(instance, "mScreenshotChordPowerKeyTime", eventTime)
        setField(instance, "mScreenshotChordVolumeDownKeyTriggered", true)
        setField(instance, "mScreenshotChordVolumeDownKeyTime", eventTime)
        invokeDirectScreenshot(instance)
    }

    private fun isPowerPageScreenshotEnabled(): Boolean {
        return HookPrefs.getBoolean(
            ModulePrefs.KEY_POWER_PAGE_SCREENSHOT_ENABLED,
            true
        )
    }

    private fun resetChordState() {
        isPowerKeyDown = false
        isPageDownKeyDown = false
        isScreenshotChordActive = false
    }

    private fun getBooleanField(instance: Any, name: String): Boolean {
        return runCatching {
            field(instance, name).getBoolean(instance)
        }.getOrDefault(false)
    }

    private fun setField(instance: Any, name: String, value: Any) {
        runCatching {
            field(instance, name).set(instance, value)
        }
    }

    private fun invokeDirectScreenshot(instance: Any): Boolean {
        return runCatching {
            val displayPolicy = field(instance, "mDefaultDisplayPolicy").get(instance)
                ?: return@runCatching false
            val takeScreenshot = displayPolicy.javaClass.getDeclaredMethod(
                "takeScreenshot",
                Int::class.javaPrimitiveType
            )
            takeScreenshot.isAccessible = true
            takeScreenshot.invoke(displayPolicy, 1)
            true
        }.getOrDefault(false)
    }

    private fun field(instance: Any, name: String) = instance.javaClass.getDeclaredField(name).apply {
        isAccessible = true
    }
}
