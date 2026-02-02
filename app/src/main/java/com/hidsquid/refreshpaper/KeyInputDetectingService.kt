package com.hidsquid.refreshpaper

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.hidsquid.refreshpaper.MainUtils.Companion.refreshScreen

class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5
    private val uniqueDrawingId = 1

    private var currentPackageName: String = ""

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var handler: Handler? = null
    private var screenRefreshBroadcastReceiver: ScreenRefreshBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)

        screenRefreshBroadcastReceiver = ScreenRefreshBroadcastReceiver()
        val filter = IntentFilter(ACTION_REFRESH_SCREEN)

        registerReceiver(screenRefreshBroadcastReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 화면(앱) 변경 감지
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString() ?: ""
        }
    }

    override fun onInterrupt() {
        // 서비스 중단 시 처리 (필요 없음)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        triggerCount = settingsRepository.getPagesPerRefresh()
        Log.d(TAG, "onServiceConnected - triggerCount: $triggerCount")

        setupOverlayView()

        handler = Handler(Looper.getMainLooper())
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)
        overlayView?.visibility = View.GONE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            Log.d(TAG, "Received number: $triggerCount")

            settingsRepository.setPagesPerRefresh(triggerCount)
        } else {
            triggerCount = settingsRepository.getPagesPerRefresh()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
        if (screenRefreshBroadcastReceiver != null) {
            unregisterReceiver(screenRefreshBroadcastReceiver)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            return handleKeyDown(event)
        }
        return super.onKeyEvent(event)
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
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
                        refreshScreen(uniqueDrawingId)
                        pageUpDownCount = 0
                    }
                    return false
                }
            }
        }

        if (isManualRefreshEnabled) {
            if (keyCode == KeyEvent.KEYCODE_F4) {
                refreshScreen(uniqueDrawingId)
                showOverlay()
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun showOverlay() {
        overlayView?.visibility = View.VISIBLE
        handler?.postDelayed({ overlayView?.visibility = View.GONE }, 100)
    }

    inner class ScreenRefreshBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_SCREEN) {
                Log.d(TAG, "Screen refresh broadcast received")
                refreshScreen(uniqueDrawingId)
                showOverlay()
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