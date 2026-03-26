package com.hidsquid.refreshpaper.service

import android.util.Log
import android.view.KeyEvent
import com.hidsquid.refreshpaper.SettingsRepository

class KeyShortcutHandler(
    private val settingsRepository: SettingsRepository,
    private val isBlockedForegroundApp: () -> Boolean,
    private val runConfiguredF1Action: (Int) -> Boolean,
    private val doFullRefresh: () -> Unit
) {
    private var pageUpDownCount = 0
    private var triggerCount = DEFAULT_TRIGGER_COUNT

    private var lastHandledF1ActionTime = 0L

    fun setTriggerCount(count: Int) {
        triggerCount = count.coerceAtLeast(1)
    }

    fun handleAutoRefreshIfNeeded(keyCode: Int): Boolean {
        if (!isAutoRefreshTriggerKey(keyCode)) return false
        if (!settingsRepository.isAutoRefreshEnabled()) return false
        if (isBlockedForegroundApp()) return false

        pageUpDownCount++
        if (pageUpDownCount >= triggerCount) {
            doFullRefresh()
            pageUpDownCount = 0
        }
        return true
    }

    @Suppress("DEPRECATION")
    fun handleRp400GlobalButton(keyEvent: KeyEvent) {
        if (keyEvent.keyCode != KeyEvent.KEYCODE_F1) return

        val isLongPress = keyEvent.action == KeyEvent.ACTION_MULTIPLE ||
            keyEvent.repeatCount > 0 ||
            (keyEvent.flags and KeyEvent.FLAG_LONG_PRESS) != 0 ||
            (keyEvent.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0

        Log.d(
            TAG,
            "[RP400] action=${keyEvent.action} repeat=${keyEvent.repeatCount} " +
                "flags=0x${keyEvent.flags.toString(16)} long=$isLongPress"
        )

        if (isLongPress) {
            handleF1LongPress()
            return
        }

        if (keyEvent.action == KeyEvent.ACTION_UP) {
            handleF1ShortPress()
        }
    }

    private fun isAutoRefreshTriggerKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN -> true
            else -> false
        }
    }

    private fun handleF1ShortPress(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()

        if (isBlockedForegroundApp()) {
            return false
        }

        if (now - lastHandledF1ActionTime <= F1_ACTION_DEBOUNCE_MS) {
            return true
        }
        lastHandledF1ActionTime = now

        val selectedAction = settingsRepository.getF1Action()
        return runConfiguredF1Action(selectedAction)
    }

    private fun handleF1LongPress(): Boolean {
        if (isBlockedForegroundApp()) {
            return false
        }
        val selectedAction = settingsRepository.getF1LongPressAction(settingsRepository.getF1Action())
        return runConfiguredF1Action(selectedAction)
    }

    private companion object {
        private const val TAG = "KeyShortcutHandler"
        private const val DEFAULT_TRIGGER_COUNT = 5
        private const val F1_ACTION_DEBOUNCE_MS = 120L
    }
}
