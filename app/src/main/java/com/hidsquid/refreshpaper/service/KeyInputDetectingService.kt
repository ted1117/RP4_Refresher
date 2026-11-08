package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.IntentCompat
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.ButtonAction
import com.hidsquid.refreshpaper.SharedConstants
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
    private lateinit var buttonActionDispatcher: ButtonActionDispatcher
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
            blockedPackageName = SharedConstants.RIDI_PAPER_PACKAGE,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName,
            touchForegroundPackageProvider = foregroundAppTracker::getCurrentUserAppPackageName
        )
        actionLauncher = ServiceActionLauncher(
            context = this,
            settingsRepository = settingsRepository,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName
        )
        pageKeySwapToggleUseCase = PageKeySwapToggleUseCase(
            settingsRepository = settingsRepository,
            blockedPackageName = SharedConstants.RIDI_PAPER_PACKAGE,
            foregroundPackageProvider = foregroundAppTracker::getEffectivePackageName,
            onRemapTargetsChanged = remapDaemonController::scheduleStateUpdate
        )
        buttonActionDispatcher = ButtonActionDispatcher(
            actionLauncher = actionLauncher,
            pageKeySwapToggleUseCase = pageKeySwapToggleUseCase,
            triggerManualRefresh = refreshCoordinator::triggerManualRefresh,
            performBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            performScreenshot = { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }
        )
        shortcutHandler = KeyShortcutHandler(
            settingsRepository = settingsRepository,
            isBlockedForegroundApp = ::isBlockedForegroundApp,
            runConfiguredF1Action = buttonActionDispatcher::dispatch,
            doFullRefresh = refreshCoordinator::doFullRefresh
        )

        serviceBroadcastReceiver = KeyInputServiceBroadcastReceiver(
            onRefreshScreen = refreshCoordinator::doFullRefresh,
            onShowBrightness = actionLauncher::launchBrightnessDialog,
            onRp400GlobalButton = ::handleRp400GlobalButton,
            onRemapStateChanged = ::handleSettingsChanged,
            onTakeScreenshot = ::triggerScreenshot,
            onOpenQuickSettings = actionLauncher::launchQuickSettingsDialog,
            onOverlayClosed = ::handleOverlayClosed
        )
        registerReceiver(
            serviceBroadcastReceiver,
            IntentFilter().apply {
                // 외부 공개 API. 권한을 걸지 말 것 (docs/external-api.md)
                addAction(ACTION_REFRESH_SCREEN)
                addAction(ACTION_SHOW_BRIGHTNESS_ACTIVITY)
                addAction(ACTION_RP400_GLOBAL_BUTTON)
                addAction(ACTION_PAGE_KEY_REMAP_STATE_CHANGED)
                addAction(ACTION_TAKE_SCREENSHOT)
                addAction(ACTION_OPEN_QUICK_SETTINGS)
                addAction(ACTION_OVERLAY_CLOSED)
            }
        )
    }

    private fun handleOverlayClosed() {
        foregroundAppTracker.onOverlayClosed()
        remapDaemonController.scheduleStateUpdate()
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
            val className = event.className?.toString().orEmpty()
            foregroundAppTracker.onWindowStateChanged(packageName, className)
            remapDaemonController.scheduleStateUpdate()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun triggerScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun isBlockedForegroundApp(): Boolean {
        return foregroundAppTracker.isBlocked(SharedConstants.RIDI_PAPER_PACKAGE)
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
        if (handlePageKeyLongPressSignal(event)) {
            return true
        }
        if (handleTouchRefreshSignal(event)) {
            return true
        }

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

    private fun handleSettingsChanged() {
        shortcutHandler.setTriggerCount(settingsRepository.getPagesPerRefresh())
        remapDaemonController.scheduleStateUpdate()
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

    private fun dispatchPageKeyLongPressAction(keyCode: Int): Boolean {
        if (isBlockedForegroundApp()) return false

        val selectedAction = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> settingsRepository.getPageUpLongPressAction()
            KeyEvent.KEYCODE_PAGE_DOWN -> settingsRepository.getPageDownLongPressAction()
            else -> ButtonAction.NONE
        }
        if (selectedAction == ButtonAction.NONE) return false

        return buttonActionDispatcher.dispatch(selectedAction)
    }

    private fun handlePageKeyLongPressSignal(event: KeyEvent): Boolean {
        if (!isRemapDaemonEvent(event)) return false

        val pageKeyCode = when (event.keyCode) {
            PAGE_UP_LONG_PRESS_SIGNAL_KEY -> KeyEvent.KEYCODE_PAGE_UP
            PAGE_DOWN_LONG_PRESS_SIGNAL_KEY -> KeyEvent.KEYCODE_PAGE_DOWN
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            dispatchPageKeyLongPressAction(pageKeyCode)
        }
        return true
    }

    private fun handleTouchRefreshSignal(event: KeyEvent): Boolean {
        if (event.keyCode != TOUCH_REFRESH_SIGNAL_KEY) return false
        if (!isRemapDaemonEvent(event)) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            refreshCoordinator.doFullRefresh()
        }
        return true
    }

    private fun isRemapDaemonEvent(event: KeyEvent): Boolean {
        val inputDevice = InputDevice.getDevice(event.deviceId) ?: return false
        return inputDevice.name == REMAP_DAEMON_DEVICE_NAME
    }

    companion object {
        /**
         * 화면 새로고침 broadcast. Key Mapper 같은 키 매핑 앱이 부르는 외부 공개 API다.
         * 앱 안에서는 쓰지 않는다. 지우거나 권한을 걸면 안 된다 (docs/external-api.md).
         * #28에서 실수로 지웠다가 복구했다.
         */
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        const val ACTION_RP400_GLOBAL_BUTTON: String = "ridi.intent.action.GLOBAL_BUTTON"
        const val ACTION_PAGE_KEY_REMAP_STATE_CHANGED: String =
            "com.hidsquid.refreshpaper.ACTION_PAGE_KEY_REMAP_STATE_CHANGED"
        const val ACTION_TAKE_SCREENSHOT: String =
            "com.hidsquid.refreshpaper.ACTION_TAKE_SCREENSHOT"
        const val ACTION_OPEN_QUICK_SETTINGS: String =
            SharedConstants.ACTION_OPEN_QUICK_SETTINGS
        const val ACTION_OVERLAY_CLOSED: String =
            "com.hidsquid.refreshpaper.ACTION_OVERLAY_CLOSED"
        private const val REMAP_DAEMON_DEVICE_NAME = "rp_page_key_remap"
        private const val PAGE_UP_LONG_PRESS_SIGNAL_KEY = KeyEvent.KEYCODE_F11
        private const val PAGE_DOWN_LONG_PRESS_SIGNAL_KEY = KeyEvent.KEYCODE_F12
        private const val TOUCH_REFRESH_SIGNAL_KEY = KeyEvent.KEYCODE_F10
    }
}
