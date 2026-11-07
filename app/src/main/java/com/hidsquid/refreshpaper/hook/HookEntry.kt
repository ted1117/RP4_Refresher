package com.hidsquid.refreshpaper.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs { debugLog { tag = "RefreshPaperHook" } }

    override fun onHook() = encase {
        SystemServerHook.inject(this)
        SystemUIHook.inject(this)
        ShutdownTimerValidatorHook.inject(this)
    }
}
