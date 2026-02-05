package com.hidsquid.refreshpaper.hook

import android.app.AndroidAppHelper
import android.provider.Settings
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        debugLog { tag = "SecureBypass" }
    }

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
                        } catch (t: Throwable) {
                            true
                        }

                        if (isBypassOn) {
                            if (Settings.Global.getString(cr, "secure_bypass_on") == null) {
                                Settings.Global.putInt(cr, "secure_bypass_on", 1)
                                YLog.info(msg = "Initialized secure_bypass_on=1 from hook")
                            }

                            result = false
                        }
                    }
                }
        }
    }
}