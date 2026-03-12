package com.hidsquid.refreshpaper

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.device.DeviceSecurityController
import com.hidsquid.refreshpaper.epd.EPDDisplayModeController
import com.hidsquid.refreshpaper.launcher.HomeLauncherDialogController
import com.hidsquid.refreshpaper.shutdown.SleepModeTimerDialogController
import com.hidsquid.refreshpaper.shutdown.ShutdownTimerDialogController
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.utils.AccessibilityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var epdController: EPDDisplayModeController
    private lateinit var deviceSecurityController: DeviceSecurityController
    private lateinit var homeLauncherDialogController: HomeLauncherDialogController
    private lateinit var sleepModeTimerDialogController: SleepModeTimerDialogController
    private lateinit var shutdownTimerDialogController: ShutdownTimerDialogController
    private var shouldOpenLsposedWhenReady = false
    private var isRefreshingRootAccess = false
    private var hasVerifiedRootAccessThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        epdController = EPDDisplayModeController(this)
        deviceSecurityController = DeviceSecurityController(this)
        homeLauncherDialogController = HomeLauncherDialogController(this, settingsRepository)
        sleepModeTimerDialogController = SleepModeTimerDialogController(this, settingsRepository)
        shutdownTimerDialogController = ShutdownTimerDialogController(this, settingsRepository)

        checkPermissionAndShowUI()
        loadSettings()
        setupListeners()
        setupPermissionState()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndShowUI()
        updateEpdModeSummary()
        loadSettings() // Reload settings on resume
    }

    private fun checkPermissionAndShowUI() {
        val accessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        val showLsposedGuide = shouldOpenLsposedWhenReady &&
            accessibilityEnabled &&
            hasVerifiedRootAccessThisSession

        updatePermissionState(accessibilityEnabled)

        if (showLsposedGuide) {
            binding.layoutPermission.root.visibility = View.GONE
            binding.layoutLsposedGuide.root.visibility = View.VISIBLE
            binding.layoutSettings.root.visibility = View.GONE
        } else if (accessibilityEnabled) {
            binding.layoutPermission.root.visibility = View.GONE
            binding.layoutLsposedGuide.root.visibility = View.GONE
            binding.layoutSettings.root.visibility = View.VISIBLE
        } else {
            binding.layoutPermission.root.visibility = View.VISIBLE
            binding.layoutLsposedGuide.root.visibility = View.GONE
            binding.layoutSettings.root.visibility = View.GONE
        }
    }

    private fun loadSettings() {
        val layout = binding.layoutSettings
        layout.autoRefreshSwitch.isChecked = settingsRepository.isAutoRefreshEnabled()
        layout.manualRefreshSwitch.isChecked = settingsRepository.isManualRefreshEnabled()
        layout.screenshotSwitch.isChecked = deviceSecurityController.isSecureBypassEnabled()

        updateSummaryText()
        updateEpdModeSummary()
        updateHomeLauncherSummary()
        updateSleepModeTimerSummary()
        updateShutdownTimerSummary()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        val layout = binding.layoutSettings
        setupSettingsTabs()

        val showDialogAction = View.OnClickListener { showPageCountDialog() }
        layout.autoRefreshCard.setOnClickListener(showDialogAction)

        layout.autoRefreshSwitch.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

            val enabled = settingsRepository.isAutoRefreshEnabled()
            if (enabled) {
                settingsRepository.setAutoRefreshEnabled(false)
                updateSummaryText()
            } else {
                layout.autoRefreshSwitch.setOnCheckedChangeListener(null)
                layout.autoRefreshSwitch.isChecked = false
                attachAutoOffListener()
                showPageCountDialog()
            }
            true
        }

        attachAutoOffListener()

        layout.manualRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setManualRefreshEnabled(isChecked)
        }

        layout.manualRefreshCard.setOnClickListener {
            layout.manualRefreshSwitch.isChecked = !layout.manualRefreshSwitch.isChecked
        }

        layout.epdModeCard.setOnClickListener {
            showEpdDisplayModeDialog()
        }

        layout.brightnessCard.setOnClickListener {
            showBrightnessDialog()
        }

        layout.screenshotSwitch.setOnCheckedChangeListener { _, isChecked ->
            try {
                deviceSecurityController.setSecureBypass(isChecked)
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show()
                layout.screenshotSwitch.isChecked = !isChecked
            }
        }

        layout.screenshotCard.setOnClickListener {
            layout.screenshotSwitch.isChecked = !layout.screenshotSwitch.isChecked
        }

        layout.homeLauncherCard.setOnClickListener {
            homeLauncherDialogController.show { updateHomeLauncherSummary() }
        }

        layout.buttonActionAssignmentCard.setOnClickListener {
            startActivity(Intent(this, ButtonActionSettingsActivity::class.java))
        }

        layout.sleepModeTimerCard.setOnClickListener {
            sleepModeTimerDialogController.show { updateSleepModeTimerSummary() }
        }

        layout.shutdownTimerCard.setOnClickListener {
            shutdownTimerDialogController.show { updateShutdownTimerSummary() }
        }

        layout.labsCard.setOnClickListener {
            startActivity(Intent(this, LabsActivity::class.java))
        }
    }

    private fun setupPermissionState() {
        binding.layoutPermission.accessibilityButton.setOnClickListener {
            shouldOpenLsposedWhenReady = true
            requestAccessibilityWithRoot()
        }

        binding.layoutLsposedGuide.openLsposedManagerButton.setOnClickListener {
            shouldOpenLsposedWhenReady = false
            openLsposedManager()
        }

        binding.layoutLsposedGuide.laterButton.setOnClickListener {
            shouldOpenLsposedWhenReady = false
            checkPermissionAndShowUI()
        }
    }

    private fun updatePermissionState(accessibilityEnabled: Boolean) {
        binding.layoutPermission.accessibilityButton.text = getString(
            R.string.permission_button_with_status,
            getString(R.string.open_accessibility_settings),
            getString(
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
        lifecycleScope.launch {
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
            Toast.makeText(this@MainActivity, R.string.root_access_denied, Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            checkPermissionAndShowUI()
        }
    }

    private fun enableAccessibilityWithRoot() {
        binding.layoutPermission.accessibilityButton.isEnabled = false
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                val serviceComponent = ComponentName(
                    this@MainActivity,
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
                    this@MainActivity,
                    R.string.accessibility_enable_failed,
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@launch
            }

            Toast.makeText(
                this@MainActivity,
                R.string.accessibility_enabled_by_root,
                Toast.LENGTH_SHORT
            ).show()
            checkPermissionAndShowUI()
        }
    }

    private fun openLsposedManager() {
        lifecycleScope.launch {
            val openedByRoot = withContext(Dispatchers.IO) {
                runRootCommand(
                    "am start -c $LSPOSED_MANAGER_LAUNCH_CATEGORY $LSPOSED_MANAGER_SHELL_COMPONENT"
                )
            }

            if (openedByRoot) {
                return@launch
            }

            val intent = packageManager.getLaunchIntentForPackage(LSPOSED_MANAGER_PACKAGE)
            if (intent == null) {
                Toast.makeText(this@MainActivity, R.string.lsposed_not_found, Toast.LENGTH_SHORT).show()
                shouldOpenLsposedWhenReady = true
                checkPermissionAndShowUI()
                return@launch
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                startActivity(intent)
            }.onFailure {
                Toast.makeText(this@MainActivity, R.string.lsposed_not_found, Toast.LENGTH_SHORT).show()
                shouldOpenLsposedWhenReady = true
                checkPermissionAndShowUI()
            }
        }
    }

    private fun runRootCommand(
        command: String,
        timeoutMs: Long = ROOT_COMMAND_TIMEOUT_MS
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

    private fun setupSettingsTabs() {
        val layout = binding.layoutSettings

        fun selectPrimaryTab(primary: Boolean) {
            layout.tabPrimary.isSelected = primary
            layout.tabSecondary.isSelected = !primary
            layout.settingsPagePrimary.visibility = if (primary) View.VISIBLE else View.GONE
            layout.settingsPageSecondary.visibility = if (primary) View.GONE else View.VISIBLE
        }

        layout.tabPrimary.setOnClickListener { selectPrimaryTab(true) }
        layout.tabSecondary.setOnClickListener { selectPrimaryTab(false) }
        selectPrimaryTab(true)
    }

    private fun attachAutoOffListener() {
        val layout = binding.layoutSettings
        layout.autoRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingsRepository.setAutoRefreshEnabled(false)
                updateSummaryText()
            }
        }
    }

    private fun showEpdDisplayModeDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_epd_display_mode, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val itemMin = dialogView.findViewById<RelativeLayout>(R.id.item1)
        val itemNormal = dialogView.findViewById<RelativeLayout>(R.id.item3)
        val checkMin = dialogView.findViewById<ImageView>(R.id.check1)
        val checkNormal = dialogView.findViewById<ImageView>(R.id.check3)

        fun setChecked(mode: Int) {
            checkMin.visibility = if (mode == EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE) View.VISIBLE else View.GONE
            checkNormal.visibility = if (mode == EPDDisplayModeController.MODE_NORMAL) View.VISIBLE else View.GONE
            checkMin.bringToFront()
            checkNormal.bringToFront()
        }

        dialog.setOnShowListener {
            lifecycleScope.launch {
                val raw = epdController.getDisplayMode()
                val mode = epdController.normalize(raw)
                setChecked(mode)
            }
        }

        val onClick = View.OnClickListener { view ->
            val sysMode = when (view.id) {
                R.id.item1 -> EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE
                R.id.item3 -> EPDDisplayModeController.MODE_NORMAL
                else -> EPDDisplayModeController.MODE_NORMAL
            }

            lifecycleScope.launch {
                val ok = epdController.setDisplayMode(sysMode)
                if (!ok) {
                    Toast.makeText(
                        this@MainActivity,
                        "display_mode setting failed (system permission/allowlist check needed)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val raw = epdController.getDisplayMode()
                val modeNow = epdController.normalize(raw)
                setChecked(modeNow)
                updateEpdModeSummary()
                dialog.dismiss()
            }
        }

        itemMin.setOnClickListener(onClick)
        itemNormal.setOnClickListener(onClick)

        dialog.show()
    }

    private fun updateEpdModeSummary() {
        lifecycleScope.launch {
            val raw = epdController.getDisplayMode()
            val mode = epdController.normalize(raw)
            binding.layoutSettings.tvEpdModeSetting.text = getString(
                if (mode == EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE)
                    R.string.setting_value_display_mode_minimize_afterimage
                else
                    R.string.setting_value_display_mode_normal
            )
        }
    }

    private fun showBrightnessDialog() {
        BrightnessActivity.start(this)
    }

    private fun showPageCountDialog() {
        val isEnabled = settingsRepository.isAutoRefreshEnabled()
        val currentCount = settingsRepository.getPagesPerRefresh()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_page_count, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val item1 = dialogView.findViewById<RelativeLayout>(R.id.item1)
        val item3 = dialogView.findViewById<RelativeLayout>(R.id.item3)
        val item5 = dialogView.findViewById<RelativeLayout>(R.id.item5)
        val item10 = dialogView.findViewById<RelativeLayout>(R.id.item10)
        val item20 = dialogView.findViewById<RelativeLayout>(R.id.item20)
        val itemOff = dialogView.findViewById<RelativeLayout>(R.id.itemOff)

        val check1 = dialogView.findViewById<ImageView>(R.id.check1)
        val check3 = dialogView.findViewById<ImageView>(R.id.check3)
        val check5 = dialogView.findViewById<ImageView>(R.id.check5)
        val check10 = dialogView.findViewById<ImageView>(R.id.check10)
        val check20 = dialogView.findViewById<ImageView>(R.id.check20)
        val checkOff = dialogView.findViewById<ImageView>(R.id.checkOff)

        fun setChecked(v: ImageView, sel: Boolean) {
            v.visibility = if (sel) View.VISIBLE else View.GONE
        }

        if (!isEnabled) {
            setChecked(checkOff, true)
        } else {
            when (currentCount) {
                1 -> setChecked(check1, true)
                3 -> setChecked(check3, true)
                5 -> setChecked(check5, true)
                10 -> setChecked(check10, true)
                20 -> setChecked(check20, true)
                else -> setChecked(check5, true)
            }
        }

        val onClick = View.OnClickListener { view ->
            if (view.id == R.id.itemOff) {
                settingsRepository.setAutoRefreshEnabled(false)
            } else {
                settingsRepository.setAutoRefreshEnabled(true)

                val selectedCount = when (view.id) {
                    R.id.item1 -> 1
                    R.id.item3 -> 3
                    R.id.item5 -> 5
                    R.id.item10 -> 10
                    R.id.item20 -> 20
                    else -> 5
                }
                settingsRepository.setPagesPerRefresh(selectedCount)

                val intent = Intent(this, KeyInputDetectingService::class.java)
                intent.putExtra(KeyInputDetectingService.EXTRA_NUMBER, selectedCount)
                startService(intent)
            }

            updateSummaryText()
            dialog.dismiss()
        }

        item1.setOnClickListener(onClick)
        item3.setOnClickListener(onClick)
        item5.setOnClickListener(onClick)
        item10.setOnClickListener(onClick)
        item20.setOnClickListener(onClick)
        itemOff.setOnClickListener(onClick)

        dialog.show()
    }

    private fun updateHomeLauncherSummary() {
        val selectedLabel = homeLauncherDialogController.getSelectedLauncherLabel()
        binding.layoutSettings.tvHomeLauncherSetting.text = selectedLabel
    }

    private fun updateSleepModeTimerSummary() {
        binding.layoutSettings.tvSleepModeTimerSetting.text = sleepModeTimerDialogController.getSelectedTimerLabel()
    }

    private fun updateShutdownTimerSummary() {
        binding.layoutSettings.tvShutdownTimerSetting.text = shutdownTimerDialogController.getSelectedTimerLabel()
    }

    private fun updateSummaryText() {
        val layout = binding.layoutSettings

        val enabled = settingsRepository.isAutoRefreshEnabled()
        val count = settingsRepository.getPagesPerRefresh()

        layout.autoRefreshSwitch.setOnCheckedChangeListener(null)
        layout.autoRefreshSwitch.isChecked = enabled
        attachAutoOffListener()

        if (enabled) {
            layout.tvCurrentSetting.text = getString(R.string.setting_summary_auto_refresh_on, count)
            layout.tvCurrentSetting.alpha = 1f
        } else {
            layout.tvCurrentSetting.text = getString(R.string.setting_summary_auto_refresh_off)
            layout.tvCurrentSetting.alpha = 0.5f
        }
    }

    companion object {
        private const val ROOT_COMMAND_TIMEOUT_MS = 2_500L
        private const val ROOT_PERMISSION_REQUEST_TIMEOUT_MS = 20_000L
        private const val LSPOSED_MANAGER_PACKAGE = "org.lsposed.manager"
        private const val LSPOSED_MANAGER_LAUNCH_CATEGORY = "org.lsposed.manager.LAUNCH_MANAGER"
        private const val LSPOSED_MANAGER_SHELL_COMPONENT =
            "com.android.shell/.BugreportWarningActivity"
    }
}
