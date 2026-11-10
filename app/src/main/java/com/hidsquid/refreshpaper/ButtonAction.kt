package com.hidsquid.refreshpaper

/** Action that can be assigned to a hardware button (F1 short/long press, page up/down long press). [id] is the value stored in prefs — never change existing ids. */
enum class ButtonAction(val id: Int) {
    BACK(0),
    SCREENSHOT(1),
    QUICK_SETTINGS(2),
    BRIGHTNESS(3),
    MANUAL_REFRESH(4),
    HOME_LAUNCHER(5),
    NONE(6),
    PAGE_KEY_SWAP(7);

    companion object {
        fun fromId(id: Int): ButtonAction? = entries.firstOrNull { it.id == id }
    }
}
