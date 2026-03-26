package com.hidsquid.refreshpaper.hook

import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

object ShutdownTimerValidatorHook {

    private const val SETTINGS_PROVIDER_PACKAGE = "com.android.providers.settings"
    private const val TARGET_SETTING_NAME = "shutdown_timer_value"
    private const val MAX_TIMER_MINUTES = 72 * 60

    fun inject(param: PackageParam) {
        param.loadApp(SETTINGS_PROVIDER_PACKAGE) {
            "com.android.providers.settings.SettingsProvider".toClass()
                .method {
                    name = "validateSystemSettingValue"
                    param(String::class.java, String::class.java)
                }
                .hook {
                    before {
                        val settingName = args[0] as? String ?: return@before
                        if (settingName != TARGET_SETTING_NAME) return@before

                        val settingValue = args[1] as? String ?: return@before
                        val timerMinutes = settingValue.toIntOrNull() ?: return@before
                        if (timerMinutes !in 1..MAX_TIMER_MINUTES) {
                            YLog.debug("Rejected shutdown timer over limit: $settingValue")
                            return@before
                        }

                        result = null
                        YLog.debug("Bypassed validator for setting: $TARGET_SETTING_NAME, value=$settingValue")
                    }
                }
        }
    }
}
