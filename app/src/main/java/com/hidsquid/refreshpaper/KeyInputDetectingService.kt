package com.hidsquid.refreshpaper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
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

/**
 * 키 입력을 감지하고 화면을 새로고침하는 서비스
 * [최적화 적용됨]: UsageStats 대신 AccessibilityEvent로 현재 앱을 감지함 (권한 불필요, 속도 빠름)
 */
class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5
    private val uniqueDrawingId = 1

    // [최적화] 현재 화면에 떠 있는 앱 패키지명을 저장하는 변수
    private var currentPackageName: String = ""

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var handler: Handler? = null
    private var screenRefreshBroadcastReceiver: ScreenRefreshBroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        screenRefreshBroadcastReceiver = ScreenRefreshBroadcastReceiver()
        // [수정] 안드로이드 12 이상 호환성을 위해 flag 추가 (단, minSdk 29라 없어도 되지만 안전하게)
        val filter = IntentFilter(ACTION_REFRESH_SCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenRefreshBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenRefreshBroadcastReceiver, filter)
        }
    }

    /**
     * [핵심 최적화 로직]
     * 화면(앱)이 바뀔 때마다 시스템이 알려줍니다.
     * 무거운 DB 조회(UsageStats) 대신 이 변수만 업데이트하면 끝입니다.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 앱 이름표(패키지명)를 갱신합니다.
            currentPackageName = event.packageName?.toString() ?: ""
        }
    }

    override fun onInterrupt() {
        // 서비스 중단 시 필요한 로직이 있다면 작성
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        triggerCount = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5)
        Log.d(TAG, "onServiceConnected - triggerCount: $triggerCount")

        // 오버레이 뷰 초기화
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
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            Log.d(TAG, "Received number: $triggerCount")

            sharedPreferences.edit().putInt(PREF_KEY_PAGES_PER_REFRESH, triggerCount).apply()
        } else {
            triggerCount = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5)
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
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isAutoRefreshEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false)
        val isManualRefreshEnabled = sharedPreferences.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false)

        // [최적화 적용] MainUtils 호출 없이 변수 비교만 수행 (속도 매우 빠름)
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
                    // 리디 페이퍼나 다른 앱이 이 키 이벤트를 처리해야 하므로 false 반환 (가로채지 않음)
                    return false
                }
            }
        }

        if (isManualRefreshEnabled) {
            if (keyCode == KeyEvent.KEYCODE_F4) {
                refreshScreen(uniqueDrawingId)
                showOverlay()
                // 우리가 처리했으니 true 반환 (다른 앱에 전달 안 함)
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun showOverlay() {
        overlayView?.visibility = View.VISIBLE
        // 0.1초 뒤에 다시 숨김 (깜빡임 효과)
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
        private const val PREFS_NAME = "MyPrefs"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"
        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"

        // 차단할 앱 (리디북스)
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
    }
}