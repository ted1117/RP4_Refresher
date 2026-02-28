package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.hidsquid.refreshpaper.BuildConfig
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BACK
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BRIGHTNESS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_MANUAL_REFRESH
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_QUICK_SETTINGS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_SCREENSHOT
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.StatusBarSettingsActivity
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.epd.EPDRefreshController
import com.hidsquid.refreshpaper.overlay.OverlayController
import kotlin.math.abs

@SuppressLint("AccessibilityPolicy")
class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5

    private var currentPackageName: String = ""
    private var currentClassName: String = ""

    private var serviceBroadcastReceiver: ServiceBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var overlayController: OverlayController

    // 스크린샷 상태 추적 (개별 키의 DOWN 이벤트 소비 여부 기록)
    private var f1KeyTime = 0L
    private var pageDownKeyTime = 0L
    private var consumedF1Down = false
    private var consumedPageDownDown = false
    private var skipNextF1Action = false
    private var lastHandledF1ActionTime = 0L

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        overlayController = OverlayController(this)

        serviceBroadcastReceiver = ServiceBroadcastReceiver()
        registerReceiver(
            serviceBroadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_REFRESH_SCREEN)
                addAction(ACTION_SHOW_BRIGHTNESS_ACTIVITY)
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        triggerCount = settingsRepository.getPagesPerRefresh()
        overlayController.attachOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString().orEmpty()
            currentClassName = event.className?.toString().orEmpty()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun doFullRefresh() {
        val v = overlayController.getView() ?: run {
            Log.w(TAG, "doFullRefresh: overlayView is null")
            return
        }

        val id = overlayController.getUniqueDrawingId() ?: run {
            Log.w(TAG, "doFullRefresh: uniqueDrawingId is null")
            return
        }

        EPDRefreshController.refresh(v, id)
    }

    private fun launchConfiguredHomeLauncher(): Boolean {
        val configuredComponent = settingsRepository.getHomeLauncherComponent()
        val defaultComponent =
            ComponentName.unflattenFromString(SettingsRepository.DEFAULT_HOME_LAUNCHER_COMPONENT)
                ?: return false
        val targetComponent = ComponentName.unflattenFromString(configuredComponent) ?: defaultComponent

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            component = targetComponent
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch configured home: $configuredComponent", e)
            if (targetComponent == defaultComponent) return false

            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        component = defaultComponent
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                true
            }.getOrElse { fallbackError ->
                Log.e(TAG, "Failed to launch default home launcher", fallbackError)
                false
            }
        }
    }

    private fun handleScreenshotChord(event: KeyEvent): Boolean {
        if (!settingsRepository.isScreenshotChordEnabled()) {
            return false
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_F1 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        f1KeyTime = event.eventTime
                        if (pageDownKeyTime > 0L && abs(f1KeyTime - pageDownKeyTime) <= SCREENSHOT_CHORD_DELAY_MS) {
                            consumedF1Down = true
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
                        if (f1KeyTime > 0L && abs(pageDownKeyTime - f1KeyTime) <= SCREENSHOT_CHORD_DELAY_MS) {
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
                        val consume = consumedPageDownDown
                        consumedPageDownDown = false
                        return consume
                    }
                }
            }
        }

        return false
    }

    private fun triggerScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun handleF1ShortPress(): Boolean {
        val now = SystemClock.uptimeMillis()

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
        return when (selectedAction) {
            F1_ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            F1_ACTION_SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            F1_ACTION_QUICK_SETTINGS -> launchQuickSettingsDialog()
            F1_ACTION_BRIGHTNESS -> launchBrightnessDialog()
            F1_ACTION_MANUAL_REFRESH -> triggerManualRefresh()
            else -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun triggerManualRefresh(): Boolean {
        doFullRefresh()
        return true
    }

    private fun launchQuickSettingsDialog(): Boolean {
        if (isTargetActivityOnTop(BuildConfig.APPLICATION_ID, CLASS_STATUS_BAR_SETTINGS_ACTIVITY)) {
            return true
        }
        return runCatching {
            startActivity(
                Intent(this, StatusBarSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            true
        }.getOrElse {
            Log.e(TAG, "Failed to launch quick settings dialog", it)
            false
        }
    }

    private fun launchBrightnessDialog(): Boolean {
        return runCatching {
            BrightnessActivity.start(applicationContext)
            true
        }.getOrElse {
            Log.e(TAG, "Failed to launch brightness dialog", it)
            false
        }
    }

    private fun isTargetActivityOnTop(packageName: String, className: String): Boolean {
        return currentPackageName == packageName && currentClassName == className
    }

    private fun isBlockedForegroundApp(): Boolean {
        return currentPackageName.isBlank() || currentPackageName == BLOCKED_APP_PACKAGE_NAME
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 스크린샷
        if (handleScreenshotChord(event)) return true

        val keyCode = event.keyCode

        // HOME 키
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return if (launchConfiguredHomeLauncher()) true else super.onKeyEvent(event)
        }

        // 자동 리프레시
        val isAutoRefreshEnabled = settingsRepository.isAutoRefreshEnabled()
        val isManualRefreshEnabled = settingsRepository.isManualRefreshEnabled()
        val isBlockedApp = isBlockedForegroundApp()

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

        // 수동 리프레시
        if (isManualRefreshEnabled && keyCode == KeyEvent.KEYCODE_F4) {
            doFullRefresh()
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
            serviceBroadcastReceiver?.let { unregisterReceiver(it) }
        }
        serviceBroadcastReceiver = null
    }

    inner class ServiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_SCREEN -> doFullRefresh()
                ACTION_SHOW_BRIGHTNESS_ACTIVITY -> launchBrightnessDialog()
            }
        }
    }

    companion object {
        private const val TAG = "KeyInputService"
        private const val SCREENSHOT_CHORD_DELAY_MS = 250L
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        private const val F1_ACTION_DEBOUNCE_MS = 120L
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
        private const val CLASS_STATUS_BAR_SETTINGS_ACTIVITY =
            "com.hidsquid.refreshpaper.StatusBarSettingsActivity"
    }
}
