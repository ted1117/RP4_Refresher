package com.hidsquid.refreshpaper

/**
 * Constants shared by the app and the hooks.
 *
 * The hooks run in other processes (SystemUI, system_server), so this object must only hold
 * `const val` primitives/Strings and must not depend on Android or app classes.
 */
object SharedConstants {
    const val RIDI_PAPER_PACKAGE = "com.ridi.paper"

    const val DEFAULT_HOME_LAUNCHER_PACKAGE = "cn.modificator.launcher"
    const val DEFAULT_HOME_LAUNCHER_CLASS = "cn.modificator.launcher.Launcher"
    const val DEFAULT_HOME_LAUNCHER_COMPONENT =
        "$DEFAULT_HOME_LAUNCHER_PACKAGE/$DEFAULT_HOME_LAUNCHER_CLASS"

    const val ACTION_OPEN_QUICK_SETTINGS = "com.hidsquid.refreshpaper.ACTION_OPEN_QUICK_SETTINGS"
}
