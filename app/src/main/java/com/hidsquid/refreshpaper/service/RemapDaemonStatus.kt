package com.hidsquid.refreshpaper.service

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.hidsquid.refreshpaper.ButtonAction
import com.hidsquid.refreshpaper.SettingsRepository

object RemapDaemonStatus {
    private const val VIRTUAL_DEVICE_NAME = "rp_page_key_remap"

    // Magisk 모듈 데몬이 만든 가상 입력 장치가 있는지로 생존 판단
    fun isRunning(context: Context): Boolean {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            ?: return false
        return inputManager.inputDeviceIds.any { id ->
            InputDevice.getDevice(id)?.name == VIRTUAL_DEVICE_NAME
        }
    }

    fun isNeeded(settingsRepository: SettingsRepository): Boolean {
        val hasRemapTargets = settingsRepository.isPageKeyTapEnabled() && (
            settingsRepository.getPageKeyTapTargetPackages().isNotEmpty() ||
                settingsRepository.getPageKeySwapTargetPackages().isNotEmpty()
            )
        val hasPageLongPressAction =
            settingsRepository.getPageUpLongPressAction() != ButtonAction.NONE ||
                settingsRepository.getPageDownLongPressAction() != ButtonAction.NONE
        val hasDaemonTouchRefresh = settingsRepository.isAutoRefreshEnabled() &&
            settingsRepository.getDaemonTouchRefreshTargetPackages().isNotEmpty()
        return hasRemapTargets || hasPageLongPressAction || hasDaemonTouchRefresh
    }
}
