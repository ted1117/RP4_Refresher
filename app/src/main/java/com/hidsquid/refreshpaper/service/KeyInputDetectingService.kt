package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.IntentCompat
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.overlay.OverlayController

@SuppressLint("AccessibilityPolicy")
class KeyInputDetectingService : AccessibilityService() {
    private var serviceBroadcastReceiver: KeyInputServiceBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var overlayController: OverlayController
    private lateinit var remapDaemonController: PageKeyRemapDaemonController
    private lateinit var shortcutHandler: KeyShortcutHandler
    private lateinit var actionLauncher: ServiceActionLauncher
    private lateinit var pageKeySwapToggleUseCase: PageKeySwapToggleUseCase
    private lateinit var f1ActionDispatcher: F1ActionDispatcher
    private lateinit var foregroundAppTracker: ForegroundAppTracker
    private lateinit var refreshCoordinator: RefreshCoordinator

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        overlayController = OverlayController(this)
        refreshCoordinator = RefreshCoordinator(overlayController)
        foregroundAppTracker = ForegroundAppTracker(selfPackageName = packageName)
        remapDaemonController = PageKeyRemapDaemonController(
            context = applicationContext,
            settingsRepository = settingsRepository,
            blockedPackageName = BLOCKED_APP_PACKAGE_NAME,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName
        )
        actionLauncher = ServiceActionLauncher(
            context = this,
            settingsRepository = settingsRepository,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName
        )
        pageKeySwapToggleUseCase = PageKeySwapToggleUseCase(
            settingsRepository = settingsRepository,
            blockedPackageName = BLOCKED_APP_PACKAGE_NAME,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName,
            onRemapTargetsChanged = remapDaemonController::scheduleStateUpdate
        )
        f1ActionDispatcher = F1ActionDispatcher(
            actionLauncher = actionLauncher,
            pageKeySwapToggleUseCase = pageKeySwapToggleUseCase,
            triggerManualRefresh = refreshCoordinator::triggerManualRefresh,
            performBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            performScreenshot = { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }
        )
        shortcutHandler = KeyShortcutHandler(
            settingsRepository = settingsRepository,
            isBlockedForegroundApp = ::isBlockedForegroundApp,
            runConfiguredF1Action = f1ActionDispatcher::dispatch,
            doFullRefresh = refreshCoordinator::doFullRefresh,
            triggerScreenshot = ::triggerScreenshot
        )

        serviceBroadcastReceiver = KeyInputServiceBroadcastReceiver(
            onRefreshScreen = refreshCoordinator::doFullRefresh,
            onShowBrightness = actionLauncher::launchBrightnessDialog,
            onRp400GlobalButton = ::handleRp400GlobalButton,
            onRemapStateChanged = remapDaemonController::scheduleStateUpdate,
            onOpenQuickSettings = actionLauncher::launchQuickSettingsDialog
        )
        registerReceiver(
            serviceBroadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_REFRESH_SCREEN)
                addAction(ACTION_SHOW_BRIGHTNESS_ACTIVITY)
                addAction(ACTION_RP400_GLOBAL_BUTTON)
                addAction(ACTION_PAGE_KEY_REMAP_STATE_CHANGED)
                addAction(ACTION_OPEN_QUICK_SETTINGS)
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        shortcutHandler.setTriggerCount(settingsRepository.getPagesPerRefresh())
        overlayController.attachOverlay()
        remapDaemonController.scheduleStateUpdate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString().orEmpty()
            foregroundAppTracker.onWindowStateChanged(packageName)
            remapDaemonController.scheduleStateUpdate()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun triggerScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun isBlockedForegroundApp(): Boolean {
        return foregroundAppTracker.isBlocked(BLOCKED_APP_PACKAGE_NAME)
    }

    private fun handleRp400GlobalButton(intent: Intent) {
        val keyEvent = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_KEY_EVENT,
            KeyEvent::class.java
        ) ?: return

        shortcutHandler.handleRp400GlobalButton(keyEvent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 스크린샷
        if (shortcutHandler.handleScreenshotChord(event)) return true

        val keyCode = event.keyCode

        if (event.action != KeyEvent.ACTION_UP) return super.onKeyEvent(event)

        // HOME 키
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return if (actionLauncher.launchConfiguredHomeLauncher()) true else super.onKeyEvent(event)
        }

        // 자동 리프레시
        val isManualRefreshEnabled = settingsRepository.isManualRefreshEnabled()
        if (shortcutHandler.handleAutoRefreshIfNeeded(keyCode)) {
            return false
        }

        // 수동 리프레시
        if (isManualRefreshEnabled && keyCode == KeyEvent.KEYCODE_F4) {
            refreshCoordinator.triggerManualRefresh()
        }

        return super.onKeyEvent(event)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            val triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            shortcutHandler.setTriggerCount(triggerCount)
            settingsRepository.setPagesPerRefresh(triggerCount)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        overlayController.detachOverlay()
        remapDaemonController.shutdown()

        runCatching {
            serviceBroadcastReceiver?.let { unregisterReceiver(it) }
        }
        serviceBroadcastReceiver = null
    }

    companion object {
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        const val ACTION_RP400_GLOBAL_BUTTON: String = "ridi.intent.action.GLOBAL_BUTTON"
        const val ACTION_PAGE_KEY_REMAP_STATE_CHANGED: String =
            "com.hidsquid.refreshpaper.ACTION_PAGE_KEY_REMAP_STATE_CHANGED"
        const val ACTION_OPEN_QUICK_SETTINGS: String =
            "com.hidsquid.refreshpaper.ACTION_OPEN_QUICK_SETTINGS"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
    }
}
