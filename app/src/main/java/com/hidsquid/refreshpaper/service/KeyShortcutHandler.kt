package com.hidsquid.refreshpaper.service

import android.util.Log
import android.view.KeyEvent
import com.hidsquid.refreshpaper.SettingsRepository
import kotlin.math.abs

class KeyShortcutHandler(
    private val settingsRepository: SettingsRepository,
    private val isBlockedForegroundApp: () -> Boolean,
    private val runConfiguredF1Action: (Int) -> Boolean,
    private val doFullRefresh: () -> Unit,
    private val triggerScreenshot: () -> Unit
) {
    private var pageUpDownCount = 0
    private var triggerCount = DEFAULT_TRIGGER_COUNT

    private var f1KeyTime = 0L
    private var pageDownKeyTime = 0L
    private var pageDownChordWindowMs = SCREENSHOT_CHORD_DELAY_MS
    private var consumedF1Down = false
    private var consumedPageDownDown = false
    private var skipNextF1Action = false
    private var lastHandledF1ActionTime = 0L

    fun setTriggerCount(count: Int) {
        triggerCount = count.coerceAtLeast(1)
    }

    fun handleScreenshotChord(
        event: KeyEvent,
        canonicalKeyCode: Int = event.keyCode,
        customChordWindowMs: Long? = null
    ): Boolean {
        if (!settingsRepository.isScreenshotChordEnabled()) {
            return false
        }

        when (canonicalKeyCode) {
            KeyEvent.KEYCODE_F1 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        f1KeyTime = event.eventTime
                        if (pageDownKeyTime > 0L &&
                            abs(f1KeyTime - pageDownKeyTime) <= pageDownChordWindowMs) {
                            consumedF1Down = true
                            consumedPageDownDown = true
                            skipNextF1Action = true
                            triggerScreenshot()
                            return true
                        }
                        consumedF1Down = false
                        return false
                    }
                    KeyEvent.ACTION_UP -> {
                        f1KeyTime = 0L
                        val consume = consumedF1Down
                        consumedF1Down = false
                        return consume
                    }
                }
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        pageDownKeyTime = event.eventTime
                        pageDownChordWindowMs = customChordWindowMs ?: SCREENSHOT_CHORD_DELAY_MS
                        if (f1KeyTime > 0L &&
                            abs(pageDownKeyTime - f1KeyTime) <= pageDownChordWindowMs) {
                            consumedF1Down = true
                            consumedPageDownDown = true
                            skipNextF1Action = true
                            triggerScreenshot()
                            return true
                        }
                        consumedPageDownDown = false
                        return false
                    }
                    KeyEvent.ACTION_UP -> {
                        pageDownKeyTime = 0L
                        pageDownChordWindowMs = SCREENSHOT_CHORD_DELAY_MS
                        val consume = consumedPageDownDown
                        consumedPageDownDown = false
                        return consume
                    }
                }
            }
        }

        return false
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
            if (skipNextF1Action) {
                skipNextF1Action = false
                return
            }
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

        if (skipNextF1Action) {
            skipNextF1Action = false
            return true
        }

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
        private const val SCREENSHOT_CHORD_DELAY_MS = 250L
        private const val F1_ACTION_DEBOUNCE_MS = 120L
    }
}
