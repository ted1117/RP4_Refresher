package com.hidsquid.refreshpaper.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    companion object {
        const val ACTION_REFRESH_SCREEN = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
    }

    override fun onInit() = configs { debugLog { tag = "RefreshPaperHook" } }

    override fun onHook() = encase {
        SystemServerHook.inject(this)
        AutoRefreshHook.inject(this)
        SystemUIHook.inject(this)
        ShutdownTimerValidatorHook.inject(this)
    }
}
