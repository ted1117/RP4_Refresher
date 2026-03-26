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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainPermissionCoordinator(
    private val activity: ComponentActivity,
    private val binding: ActivityMainBinding,
) {
    private var shouldOpenLsposedWhenReady = false
    private var isRefreshingRootAccess = false
    private var hasVerifiedRootAccessThisSession = false
    private var hasSkippedPermissionGate = activity.getPreferences(Context.MODE_PRIVATE)
        .getBoolean(KEY_PERMISSION_GATE_SKIPPED, false)

    fun setup() {
        binding.layoutPermission.accessibilityButton.setOnClickListener {
            shouldOpenLsposedWhenReady = true
            setPermissionGateSkipped(false)
            requestAccessibilityWithRoot()
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

        val showLsposedGuide = shouldOpenLsposedWhenReady &&
            accessibilityEnabled &&
            hasVerifiedRootAccessThisSession
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
                    isRefreshingRootAccess -> R.string.permission_status_checking
                    else -> R.string.permission_status_needed
                }
            )
        )
    }

    private fun requestAccessibilityWithRoot() {
        binding.layoutPermission.accessibilityButton.isEnabled = false
        isRefreshingRootAccess = true
        updatePermissionState(accessibilityEnabled = false)

        activity.lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) {
                runRootCommand("exit 0", ROOT_PERMISSION_REQUEST_TIMEOUT_MS)
            }

            hasVerifiedRootAccessThisSession = granted
            isRefreshingRootAccess = false

            if (granted) {
                enableAccessibilityWithRoot()
                return@launch
            }

            binding.layoutPermission.accessibilityButton.isEnabled = true
            Toast.makeText(activity, R.string.root_access_denied, Toast.LENGTH_SHORT).show()
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            refreshUi()
        }
    }

    private fun enableAccessibilityWithRoot() {
        binding.layoutPermission.accessibilityButton.isEnabled = false

        activity.lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                val serviceComponent = ComponentName(
                    activity,
                    KeyInputDetectingService::class.java
                ).flattenToString()
                val command = """
                    current="${'$'}(settings get secure enabled_accessibility_services 2>/dev/null || true)"
                    target="$serviceComponent"
                    case ":${'$'}current:" in
                      *":${'$'}target:"*) next="${'$'}current" ;;
                      "") next="${'$'}target" ;;
                      *) next="${'$'}current:${'$'}target" ;;
                    esac
                    settings put secure enabled_accessibility_services "${'$'}next"
                    settings put secure accessibility_enabled 1
                """.trimIndent()
                runRootCommand(command)
            }

            binding.layoutPermission.accessibilityButton.isEnabled = true
            if (!enabled) {
                Toast.makeText(
                    activity,
                    R.string.accessibility_enable_failed,
                    Toast.LENGTH_SHORT
                ).show()
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@launch
            }

            Toast.makeText(activity, R.string.accessibility_enabled_by_root, Toast.LENGTH_SHORT)
                .show()
            refreshUi()
        }
    }

    private fun openLsposedManager() {
        activity.lifecycleScope.launch {
            val openedByRoot = withContext(Dispatchers.IO) {
                runRootCommand(
                    "am start -c $LSPOSED_MANAGER_LAUNCH_CATEGORY $LSPOSED_MANAGER_SHELL_COMPONENT"
                )
            }

            if (openedByRoot) {
                return@launch
            }

            val intent = activity.packageManager.getLaunchIntentForPackage(LSPOSED_MANAGER_PACKAGE)
            if (intent == null) {
                Toast.makeText(activity, R.string.lsposed_not_found, Toast.LENGTH_SHORT).show()
                shouldOpenLsposedWhenReady = true
                refreshUi()
                return@launch
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                activity.startActivity(intent)
            }.onFailure {
                Toast.makeText(activity, R.string.lsposed_not_found, Toast.LENGTH_SHORT).show()
                shouldOpenLsposedWhenReady = true
                refreshUi()
            }
        }
    }

    private fun runRootCommand(
        command: String,
        timeoutMs: Long = ROOT_COMMAND_TIMEOUT_MS,
    ): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false
            }
            process.exitValue() == 0
        }.getOrDefault(false)
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
        private const val ROOT_COMMAND_TIMEOUT_MS = 2_500L
        private const val ROOT_PERMISSION_REQUEST_TIMEOUT_MS = 20_000L
        private const val LSPOSED_MANAGER_PACKAGE = "org.lsposed.manager"
        private const val LSPOSED_MANAGER_LAUNCH_CATEGORY = "org.lsposed.manager.LAUNCH_MANAGER"
        private const val LSPOSED_MANAGER_SHELL_COMPONENT =
            "com.android.shell/.BugreportWarningActivity"
    }
}
