package com.hidsquid.refreshpaper

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.hidsquid.refreshpaper.MainUtils.Companion.refreshScreen
import java.lang.reflect.Method

class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5

    private var currentPackageName: String = ""

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var epdDrawingId: Int? = null

    private var screenRefreshBroadcastReceiver: ScreenRefreshBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository

    private val getUniqueDrawingIdMethod: Method? =
        runCatching { View::class.java.getMethod("getUniqueDrawingId").apply { isAccessible = true } }
            .recoverCatching { View::class.java.getDeclaredMethod("getUniqueDrawingId").apply { isAccessible = true } }
            .getOrNull()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)

        screenRefreshBroadcastReceiver = ScreenRefreshBroadcastReceiver()
        registerReceiver(screenRefreshBroadcastReceiver, IntentFilter(ACTION_REFRESH_SCREEN))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        triggerCount = settingsRepository.getPagesPerRefresh()

        setupOverlay()

        // addView 이후 1회만 ID 확보
        overlayView?.postOnAnimation {
            if (epdDrawingId == null) {
                epdDrawingId = readUniqueDrawingId(overlayView)
                Log.d(TAG, "epdDrawingId(init)=$epdDrawingId")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString() ?: ""
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)

        overlayView?.visibility = View.VISIBLE
        overlayView?.alpha = 0f
    }

    private fun readUniqueDrawingId(v: View?): Int? {
        val view = v ?: return null
        val m = getUniqueDrawingIdMethod ?: return null
        return runCatching {
            val n = m.invoke(view) as? Number ?: return@runCatching null
            val id = n.toLong().toInt()
            id.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun doFullRefresh(): Boolean {
        val id = epdDrawingId
        if (id == null) {
            Log.w(TAG, "doFullRefresh: epdDrawingId is null")
            return false
        }
        refreshScreen(id)
        return true
    }

    // 수동 리프레시에서만 “poke” (보이지 않게 1프레임 alpha 흔들기)
    private fun pokeOverlayOnce() {
        val v = overlayView ?: return
        v.alpha = 0.01f
        v.invalidate()
        v.postOnAnimation {
            overlayView?.alpha = 0f
            overlayView?.invalidate()
        }
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
            if (ok) pokeOverlayOnce()
            return ok
        }

        return super.onKeyEvent(event)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            settingsRepository.setPagesPerRefresh(triggerCount)
        } else {
            triggerCount = settingsRepository.getPagesPerRefresh()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        runCatching {
            overlayView?.let { windowManager?.removeView(it) }
        }
        overlayView = null

        runCatching {
            screenRefreshBroadcastReceiver?.let { unregisterReceiver(it) }
        }
        screenRefreshBroadcastReceiver = null
    }

    inner class ScreenRefreshBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_SCREEN) {
                val ok = doFullRefresh()
                if (ok) pokeOverlayOnce()
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