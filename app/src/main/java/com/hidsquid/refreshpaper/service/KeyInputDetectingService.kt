package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.IntentCompat
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_NONE
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
            onTakeScreenshot = ::triggerScreenshot,
            onOpenQuickSettings = actionLauncher::launchQuickSettingsDialog
        )
        registerReceiver(
            serviceBroadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_REFRESH_SCREEN)
                addAction(ACTION_SHOW_BRIGHTNESS_ACTIVITY)
                addAction(ACTION_RP400_GLOBAL_BUTTON)
                addAction(ACTION_PAGE_KEY_REMAP_STATE_CHANGED)
                addAction(ACTION_TAKE_SCREENSHOT)
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
        if (handlePageKeyLongPressSignal(event)) {
            return true
        }

        val screenshotChordMatch = resolveScreenshotChordMatch(event)
        // 스크린샷
        if (shortcutHandler.handleScreenshotChord(
                event = event,
                canonicalKeyCode = screenshotChordMatch?.canonicalKeyCode ?: event.keyCode,
                customChordWindowMs = screenshotChordMatch?.chordWindowMs
            )) {
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

    private fun dispatchPageKeyLongPressAction(keyCode: Int): Boolean {
        if (isBlockedForegroundApp()) return false

        val selectedAction = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> settingsRepository.getPageUpLongPressAction()
            KeyEvent.KEYCODE_PAGE_DOWN -> settingsRepository.getPageDownLongPressAction()
            else -> F1_ACTION_NONE
        }
        if (selectedAction == F1_ACTION_NONE) return false

        return f1ActionDispatcher.dispatch(selectedAction)
    }

    private fun resolveScreenshotChordMatch(event: KeyEvent): ScreenshotChordMatch? {
        if (!isRemapDaemonEvent(event)) return null

        val canonicalKeyCode = when (resolveCurrentRemapMode()) {
            REMAP_MODE_DISABLED -> when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
                else -> return null
            }

            REMAP_MODE_NORMAL -> when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
                else -> return null
            }

            REMAP_MODE_SWAPPED -> when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.KEYCODE_PAGE_DOWN
                else -> return null
            }

            REMAP_MODE_SWAP_ONLY -> when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_UP -> KeyEvent.KEYCODE_PAGE_DOWN
                else -> return null
            }

            else -> return null
        }

        return ScreenshotChordMatch(
            canonicalKeyCode = canonicalKeyCode,
            chordWindowMs = ViewConfiguration.getLongPressTimeout().toLong()
        )
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

    private fun isRemapDaemonEvent(event: KeyEvent): Boolean {
        val inputDevice = InputDevice.getDevice(event.deviceId) ?: return false
        return inputDevice.name == REMAP_DAEMON_DEVICE_NAME
    }

    private fun resolveCurrentRemapMode(): Int {
        if (!settingsRepository.isPageKeyTapEnabled()) return REMAP_MODE_DISABLED

        val packageName = foregroundAppTracker.getEffectivePackageName()
        if (packageName.isBlank()) return REMAP_MODE_DISABLED
        if (packageName == BLOCKED_APP_PACKAGE_NAME) return REMAP_MODE_DISABLED

        val isVolumeRemap = settingsRepository.isPageKeyTapTargetPackage(packageName)
        val isSwap = settingsRepository.isPageKeySwapTargetPackage(packageName)
        return when {
            isVolumeRemap && isSwap -> REMAP_MODE_SWAPPED
            isVolumeRemap -> REMAP_MODE_NORMAL
            isSwap -> REMAP_MODE_SWAP_ONLY
            else -> REMAP_MODE_DISABLED
        }
    }

    companion object {
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        const val ACTION_RP400_GLOBAL_BUTTON: String = "ridi.intent.action.GLOBAL_BUTTON"
        const val ACTION_PAGE_KEY_REMAP_STATE_CHANGED: String =
            "com.hidsquid.refreshpaper.ACTION_PAGE_KEY_REMAP_STATE_CHANGED"
        const val ACTION_TAKE_SCREENSHOT: String =
            "com.hidsquid.refreshpaper.ACTION_TAKE_SCREENSHOT"
        const val ACTION_OPEN_QUICK_SETTINGS: String =
            "com.hidsquid.refreshpaper.ACTION_OPEN_QUICK_SETTINGS"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
        private const val REMAP_DAEMON_DEVICE_NAME = "rp_page_key_remap"
        private const val PAGE_UP_LONG_PRESS_SIGNAL_KEY = KeyEvent.KEYCODE_F11
        private const val PAGE_DOWN_LONG_PRESS_SIGNAL_KEY = KeyEvent.KEYCODE_F12
        private const val REMAP_MODE_DISABLED = 0
        private const val REMAP_MODE_NORMAL = 1
        private const val REMAP_MODE_SWAPPED = 2
        private const val REMAP_MODE_SWAP_ONLY = 3
    }

    private data class ScreenshotChordMatch(
        val canonicalKeyCode: Int,
        val chordWindowMs: Long
    )
}
