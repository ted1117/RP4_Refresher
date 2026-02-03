package com.hidsquid.refreshpaper.hook

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
                    replaceAny { false }
                }
        }
    }
}