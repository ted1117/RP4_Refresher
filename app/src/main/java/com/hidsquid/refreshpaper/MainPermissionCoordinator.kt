package com.hidsquid.refreshpaper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.utils.AccessibilityUtils
import com.hidsquid.refreshpaper.utils.LsposedLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainPermissionCoordinator(
    private val activity: ComponentActivity,
    private val binding: ActivityMainBinding,
) {
    private var shouldOpenLsposedWhenReady = false
    private var isEnablingAccessibility = false
    private var hasSkippedPermissionGate = activity.getPreferences(Context.MODE_PRIVATE)
        .getBoolean(KEY_PERMISSION_GATE_SKIPPED, false)

    fun setup() {
        binding.layoutPermission.accessibilityButton.setOnClickListener {
            shouldOpenLsposedWhenReady = true
            setPermissionGateSkipped(false)
            enableAccessibility()
        }

        binding.layoutPermission.laterButton.setOnClickListener {
            shouldOpenLsposedWhenReady = false
            setPermissionGateSkipped(true)
            refreshUi()
        }

        binding.layoutLsposedGuide.openLsposedManagerButton.setOnClickListener {
            shouldOpenLsposedWhenReady = false
            openLsposedManager()
        }

        binding.layoutLsposedGuide.laterButton.setOnClickListener {
            shouldOpenLsposedWhenReady = false
            refreshUi()
        }
    }

    fun refreshUi() {
        val accessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(activity)
        if (accessibilityEnabled && hasSkippedPermissionGate) {
            setPermissionGateSkipped(false)
        }

        val showLsposedGuide = shouldOpenLsposedWhenReady && accessibilityEnabled
        val showSettings = accessibilityEnabled || hasSkippedPermissionGate

        updatePermissionState(accessibilityEnabled)

        when {
            showLsposedGuide -> {
                binding.layoutPermission.root.visibility = View.GONE
                binding.layoutLsposedGuide.root.visibility = View.VISIBLE
                binding.layoutSettings.root.visibility = View.GONE
            }

            showSettings -> {
                binding.layoutPermission.root.visibility = View.GONE
                binding.layoutLsposedGuide.root.visibility = View.GONE
                binding.layoutSettings.root.visibility = View.VISIBLE
            }

            else -> {
                binding.layoutPermission.root.visibility = View.VISIBLE
                binding.layoutLsposedGuide.root.visibility = View.GONE
                binding.layoutSettings.root.visibility = View.GONE
            }
        }
    }

    private fun updatePermissionState(accessibilityEnabled: Boolean) {
        binding.layoutPermission.accessibilityButton.text = activity.getString(
            R.string.permission_button_with_status,
            activity.getString(R.string.open_accessibility_settings),
            activity.getString(
                when {
                    accessibilityEnabled -> R.string.permission_status_done
                    isEnablingAccessibility -> R.string.permission_status_checking
                    else -> R.string.permission_status_needed
                }
            )
        )
    }

    private fun enableAccessibility() {
        binding.layoutPermission.accessibilityButton.isEnabled = false
        isEnablingAccessibility = true
        updatePermissionState(accessibilityEnabled = false)

        activity.lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) { writeAccessibilitySettings() }

            if (enabled) {
                // 서비스 목록이 설정 변경을 반영할 때까지 대기 (타임아웃 시에도 진행)
                withTimeoutOrNull(ACCESSIBILITY_WAIT_TIMEOUT_MS) {
                    while (!AccessibilityUtils.isAccessibilityServiceEnabled(activity)) {
                        delay(ACCESSIBILITY_POLL_INTERVAL_MS)
                    }
                }
            }

            isEnablingAccessibility = false
            binding.layoutPermission.accessibilityButton.isEnabled = true

            if (!enabled) {
                Toast.makeText(
                    activity,
                    R.string.accessibility_enable_failed,
                    Toast.LENGTH_SHORT
                ).show()
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                refreshUi()
                return@launch
            }

            Toast.makeText(activity, R.string.accessibility_enabled, Toast.LENGTH_SHORT).show()
            refreshUi()
        }
    }

    private fun writeAccessibilitySettings(): Boolean {
        return try {
            val resolver = activity.contentResolver
            val service = ComponentName(activity, KeyInputDetectingService::class.java)
            val entries = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').filter { it.isNotEmpty() }

            val alreadyEnabled = entries.any { ComponentName.unflattenFromString(it) == service }
            val updated = if (alreadyEnabled) entries else entries + service.flattenToString()

            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated.joinToString(":")
            ) &&
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: Throwable) {
            false
        }
    }

    private fun openLsposedManager() {
        val button = binding.layoutLsposedGuide.openLsposedManagerButton
        button.isEnabled = false
        activity.lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                LsposedLauncher.open(activity)
            }
            button.isEnabled = true

            if (!opened) {
                Toast.makeText(activity, R.string.lsposed_launch_failed, Toast.LENGTH_LONG).show()
                shouldOpenLsposedWhenReady = true
                refreshUi()
            }
        }
    }

    private fun setPermissionGateSkipped(skipped: Boolean) {
        hasSkippedPermissionGate = skipped
        activity.getPreferences(Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_GATE_SKIPPED, skipped)
            .apply()
    }

    private companion object {
        private const val KEY_PERMISSION_GATE_SKIPPED = "permission_gate_skipped"
        private const val ACCESSIBILITY_WAIT_TIMEOUT_MS = 3_000L
        private const val ACCESSIBILITY_POLL_INTERVAL_MS = 100L
    }
}
