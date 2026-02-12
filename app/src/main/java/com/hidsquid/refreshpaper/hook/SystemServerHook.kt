package com.hidsquid.refreshpaper.hook

import android.app.AndroidAppHelper
import android.provider.Settings
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam

object SystemServerHook {

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
    }
}
