package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.epd.EPDRefreshController
import com.hidsquid.refreshpaper.overlay.OverlayController

@SuppressLint("AccessibilityPolicy")
class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5

    private var currentPackageName: String = ""

    private var serviceBroadcastReceiver: ServiceBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var overlayController: OverlayController

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
            currentPackageName = event.packageName?.toString() ?: ""
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

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.onKeyEvent(event)

        val keyCode = event.keyCode

        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return if (launchConfiguredHomeLauncher()) true else super.onKeyEvent(event)
        }

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
                ACTION_SHOW_BRIGHTNESS_ACTIVITY -> BrightnessActivity.start(applicationContext)
            }
        }
    }

    companion object {
        private const val TAG = "KeyInputService"
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
    }
}
