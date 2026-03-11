package com.hidsquid.refreshpaper.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BACK
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BRIGHTNESS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_HOME_LAUNCHER
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_MANUAL_REFRESH
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_NONE
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_PAGE_KEY_SWAP
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_QUICK_SETTINGS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_SCREENSHOT
import com.hidsquid.refreshpaper.StatusBarSettingsActivity
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.epd.EPDRefreshController
import com.hidsquid.refreshpaper.overlay.OverlayController

class ServiceActionLauncher(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val foregroundPackageProvider: () -> String
) {
    fun launchConfiguredHomeLauncher(): Boolean {
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
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch configured home: $configuredComponent", e)
            if (targetComponent == defaultComponent) return false

            runCatching {
                context.startActivity(
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

    fun launchQuickSettingsDialog(): Boolean {
        return runCatching {
            context.startActivity(
                Intent(context, StatusBarSettingsActivity::class.java).apply {
                    putExtra(StatusBarSettingsActivity.EXTRA_TARGET_PACKAGE, foregroundPackageProvider())
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

    fun launchBrightnessDialog(): Boolean {
        return runCatching {
            BrightnessActivity.start(context)
            true
        }.getOrElse {
            Log.e(TAG, "Failed to launch brightness dialog", it)
            false
        }
    }

    private companion object {
        private const val TAG = "ServiceActionLauncher"
    }
}

class F1ActionDispatcher(
    private val actionLauncher: ServiceActionLauncher,
    private val pageKeySwapToggleUseCase: PageKeySwapToggleUseCase,
    private val triggerManualRefresh: () -> Boolean,
    private val performBack: () -> Boolean,
    private val performScreenshot: () -> Boolean
) {
    fun dispatch(selectedAction: Int): Boolean {
        return when (selectedAction) {
            F1_ACTION_BACK -> performBack()
            F1_ACTION_SCREENSHOT -> performScreenshot()
            F1_ACTION_QUICK_SETTINGS -> actionLauncher.launchQuickSettingsDialog()
            F1_ACTION_BRIGHTNESS -> actionLauncher.launchBrightnessDialog()
            F1_ACTION_MANUAL_REFRESH -> triggerManualRefresh()
            F1_ACTION_PAGE_KEY_SWAP -> pageKeySwapToggleUseCase.toggleForCurrentApp()
            F1_ACTION_HOME_LAUNCHER -> actionLauncher.launchConfiguredHomeLauncher()
            F1_ACTION_NONE -> true
            else -> performBack()
        }
    }
}

class PageKeySwapToggleUseCase(
    private val settingsRepository: SettingsRepository,
    private val blockedPackageName: String,
    private val foregroundPackageProvider: () -> String,
    private val onRemapTargetsChanged: () -> Unit
) {
    fun toggleForCurrentApp(): Boolean {
        val targetPackage = foregroundPackageProvider()
        if (targetPackage.isBlank() || targetPackage == blockedPackageName) {
            return false
        }

        val updatedTargets = settingsRepository.getPageKeySwapTargetPackages().toMutableSet().apply {
            if (contains(targetPackage)) {
                remove(targetPackage)
            } else {
                add(targetPackage)
            }
        }

        if (!settingsRepository.setPageKeySwapTargetPackages(updatedTargets)) {
            return false
        }

        val hasAnyTarget = updatedTargets.isNotEmpty() ||
            settingsRepository.getPageKeyTapTargetPackages().isNotEmpty()
        if (!settingsRepository.setPageKeyTapEnabled(hasAnyTarget)) {
            return false
        }

        onRemapTargetsChanged()
        return true
    }
}

class ForegroundAppTracker(
    private val selfPackageName: String
) {
    private var currentPackageName: String = ""
    private var foregroundAppPackage: String = ""

    fun onWindowStateChanged(packageName: String) {
        currentPackageName = packageName
        if (isUserAppPackage(packageName)) {
            foregroundAppPackage = packageName
        }
    }

    fun getEffectivePackageName(): String {
        if (isUserAppPackage(currentPackageName)) return currentPackageName
        return foregroundAppPackage
    }

    fun isBlocked(blockedPackageName: String): Boolean {
        val packageName = getEffectivePackageName()
        return packageName.isBlank() || packageName == blockedPackageName
    }

    private fun isUserAppPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return packageName != "android" &&
            packageName != "com.android.systemui" &&
            packageName != selfPackageName
    }
}

class RefreshCoordinator(
    private val overlayController: OverlayController
) {
    fun doFullRefresh() {
        val view = overlayController.getView() ?: run {
            Log.w(TAG, "doFullRefresh: overlayView is null")
            return
        }

        val drawingId = overlayController.getUniqueDrawingId() ?: run {
            Log.w(TAG, "doFullRefresh: uniqueDrawingId is null")
            return
        }

        EPDRefreshController.refresh(view, drawingId)
    }

    fun triggerManualRefresh(): Boolean {
        doFullRefresh()
        return true
    }

    private companion object {
        private const val TAG = "RefreshCoordinator"
    }
}

class KeyInputServiceBroadcastReceiver(
    private val onRefreshScreen: () -> Unit,
    private val onShowBrightness: () -> Unit,
    private val onRp400GlobalButton: (Intent) -> Unit,
    private val onRemapStateChanged: () -> Unit,
    private val onTakeScreenshot: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            KeyInputDetectingService.ACTION_REFRESH_SCREEN -> onRefreshScreen()
            KeyInputDetectingService.ACTION_SHOW_BRIGHTNESS_ACTIVITY -> onShowBrightness()
            KeyInputDetectingService.ACTION_RP400_GLOBAL_BUTTON -> onRp400GlobalButton(intent)
            KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED -> onRemapStateChanged()
            KeyInputDetectingService.ACTION_TAKE_SCREENSHOT -> onTakeScreenshot()
        }
    }
}
