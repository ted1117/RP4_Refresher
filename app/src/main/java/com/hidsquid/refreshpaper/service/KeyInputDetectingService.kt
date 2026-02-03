package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.epd.EpdRefreshController
import com.hidsquid.refreshpaper.overlay.OverlayController

@SuppressLint("AccessibilityPolicy")
class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5

    private var currentPackageName: String = ""

    private var screenRefreshBroadcastReceiver: ScreenRefreshBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var overlayController: OverlayController

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        overlayController = OverlayController(this)

        screenRefreshBroadcastReceiver = ScreenRefreshBroadcastReceiver()
        registerReceiver(screenRefreshBroadcastReceiver, IntentFilter(ACTION_REFRESH_SCREEN))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        triggerCount = settingsRepository.getPagesPerRefresh()

        overlayController.attachOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString() ?: ""
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun doFullRefresh(): Boolean {
        val id = overlayController.getUniqueDrawingId()
        if (id == null) {
            Log.w(TAG, "doFullRefresh: uniqueDrawingId is null")
            return false
        }
        EpdRefreshController.refresh(id)
        return true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)

        val keyCode = event.keyCode
        val isAutoRefreshEnabled = settingsRepository.isAutoRefreshEnabled()
        val isManualRefreshEnabled = settingsRepository.isManualRefreshEnabled()
        val isBlockedApp = currentPackageName == BLOCKED_APP_PACKAGE_NAME

        if (!isBlockedApp && isAutoRefreshEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    pageUpDownCount++
                    if (pageUpDownCount >= triggerCount) {
                        doFullRefresh()
                        pageUpDownCount = 0
                    }
                    return false
                }
            }
        }

        if (isManualRefreshEnabled && keyCode == KeyEvent.KEYCODE_F4) {
            val ok = doFullRefresh()
            if (ok) overlayController.poke()
            return ok
        }

        return super.onKeyEvent(event)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            settingsRepository.setPagesPerRefresh(triggerCount)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        overlayController.detachOverlay()

        runCatching {
            screenRefreshBroadcastReceiver?.let { unregisterReceiver(it) }
        }
        screenRefreshBroadcastReceiver = null
    }

    inner class ScreenRefreshBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_SCREEN) {
                val ok = doFullRefresh()
                if (ok) overlayController.poke()
            }
        }
    }

    companion object {
        private const val TAG = "KeyInputService"
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
    }
}
