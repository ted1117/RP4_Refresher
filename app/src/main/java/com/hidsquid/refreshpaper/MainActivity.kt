package com.hidsquid.refreshpaper

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.device.DeviceSecurityController
import com.hidsquid.refreshpaper.epd.EPDDisplayModeController
import com.hidsquid.refreshpaper.epd.EpdModeDialogController
import com.hidsquid.refreshpaper.launcher.HomeLauncherDialogController
import com.hidsquid.refreshpaper.shutdown.SleepModeTimerDialogController
import com.hidsquid.refreshpaper.shutdown.ShutdownTimerDialogController
import androidx.lifecycle.lifecycleScope
import android.annotation.SuppressLint
import android.view.View

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var epdController: EPDDisplayModeController
    private lateinit var epdModeDialogController: EpdModeDialogController
    private lateinit var pageCountDialogController: PageCountDialogController
    private lateinit var deviceSecurityController: DeviceSecurityController
    private lateinit var homeLauncherDialogController: HomeLauncherDialogController
    private lateinit var sleepModeTimerDialogController: SleepModeTimerDialogController
    private lateinit var shutdownTimerDialogController: ShutdownTimerDialogController
    private lateinit var permissionCoordinator: MainPermissionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        epdController = EPDDisplayModeController(this)
        epdModeDialogController = EpdModeDialogController(this, epdController, lifecycleScope)
        pageCountDialogController = PageCountDialogController(this, settingsRepository)
        deviceSecurityController = DeviceSecurityController(this)
        homeLauncherDialogController = HomeLauncherDialogController(this, settingsRepository)
        sleepModeTimerDialogController = SleepModeTimerDialogController(this, settingsRepository)
        shutdownTimerDialogController = ShutdownTimerDialogController(this, settingsRepository)
        permissionCoordinator = MainPermissionCoordinator(this, binding)

        permissionCoordinator.setup()
        permissionCoordinator.refreshUi()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        permissionCoordinator.refreshUi()
        updateEpdModeSummary()
        loadSettings() // Reload settings on resume
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

        val showDialogAction = View.OnClickListener {
            pageCountDialogController.show { updateSummaryText() }
        }
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
                pageCountDialogController.show { updateSummaryText() }
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
            epdModeDialogController.show { updateEpdModeSummary() }
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

    private fun updateEpdModeSummary() {
        epdModeDialogController.loadSelectedModeLabel { label ->
            binding.layoutSettings.tvEpdModeSetting.text = label
        }
    }

    private fun showBrightnessDialog() {
        BrightnessActivity.start(this)
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
}
