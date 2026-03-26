package com.hidsquid.refreshpaper.hook

import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.hidsquid.refreshpaper.ModulePrefs

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
                        val isBypassOn =
                            HookPrefs.getBoolean(
                                ModulePrefs.KEY_SECURE_BYPASS_ENABLED,
                                true
                            )
                        if (isBypassOn) result = false
                    }
                }
        }
    }
}
