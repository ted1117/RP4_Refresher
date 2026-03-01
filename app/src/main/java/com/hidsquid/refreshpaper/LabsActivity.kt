package com.hidsquid.refreshpaper

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import com.hidsquid.refreshpaper.databinding.ActivityLabsBinding

class LabsActivity : Activity() {

    private lateinit var binding: ActivityLabsBinding
    private lateinit var settingsRepository: SettingsRepository
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)

        showWarningIfNeeded()

        setupToolbar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        val autoEnabled = settingsRepository.isAutoRefreshEnabled()
        binding.touchRefreshSwitch.isChecked = settingsRepository.isTouchRefreshEnabled()
        binding.touchRefreshSwitch.isEnabled = autoEnabled
        binding.touchRefreshSwitch.alpha = if (autoEnabled) 1f else 0.5f
        binding.pageKeyTapSwitch.isChecked = settingsRepository.isPageKeyTapEnabled()
        updatePageKeyTapAppsSummary()
        binding.powerPageScreenshotSwitch.isChecked = settingsRepository.isPowerPageScreenshotEnabled()
        binding.screenshotToastSwitch.isChecked = settingsRepository.isScreenshotToastEnabled()
    }

    private fun setupListeners() {
        binding.touchRefreshCard.setOnClickListener {
            if (!settingsRepository.isAutoRefreshEnabled()) {
                Toast.makeText(this, R.string.labs_touch_refresh_requires_auto, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            binding.touchRefreshSwitch.toggle()
        }

        binding.touchRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsRepository.setTouchRefreshEnabled(isChecked)
        }

        binding.pageKeyTapCard.setOnClickListener {
            binding.pageKeyTapSwitch.toggle()
        }

        binding.pageKeyTapSwitch.setOnCheckedChangeListener { _, isChecked ->
            val saved = settingsRepository.setPageKeyTapEnabled(isChecked)
            if (!saved) {
                Toast.makeText(this, R.string.labs_page_key_tap_save_failed, Toast.LENGTH_SHORT).show()
                loadSettings()
            }
        }

        binding.pageKeyTapAppsCard.setOnClickListener {
            showPageKeyTapAppsDialog()
        }

        binding.powerPageScreenshotCard.setOnClickListener {
            binding.powerPageScreenshotSwitch.toggle()
        }

        binding.powerPageScreenshotSwitch.setOnCheckedChangeListener { _, isChecked ->
            val saved = settingsRepository.setPowerPageScreenshotEnabled(isChecked)
            if (!saved) {
                Toast.makeText(this, R.string.labs_power_page_screenshot_save_failed, Toast.LENGTH_SHORT)
                    .show()
                loadSettings()
            }
        }

        binding.screenshotToastCard.setOnClickListener {
            binding.screenshotToastSwitch.toggle()
        }

        binding.screenshotToastSwitch.setOnCheckedChangeListener { _, isChecked ->
            val saved = settingsRepository.setScreenshotToastEnabled(isChecked)
            if (!saved) {
                Toast.makeText(this, R.string.labs_screenshot_toast_save_failed, Toast.LENGTH_SHORT)
                    .show()
                loadSettings()
            }
        }
    }

    private fun updatePageKeyTapAppsSummary() {
        val selectedCount = settingsRepository.getPageKeyTapTargetPackages().size
        binding.pageKeyTapAppsSummary.text = if (selectedCount <= 0) {
            getString(R.string.labs_page_key_tap_apps_none)
        } else {
            getString(R.string.labs_page_key_tap_apps_count, selectedCount)
        }
    }

    private fun showPageKeyTapAppsDialog() {
        val options = getPageKeyTapTargetCandidates()
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.labs_page_key_tap_apps_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPackages = settingsRepository.getPageKeyTapTargetPackages().toMutableSet()
        val labels = options.map { "${it.label} (${it.packageName})" }.toTypedArray()
        val checked = BooleanArray(options.size) { index ->
            selectedPackages.contains(options[index].packageName)
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.labs_page_key_tap_apps_dialog_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val packageName = options[which].packageName
                if (isChecked) {
                    selectedPackages.add(packageName)
                } else {
                    selectedPackages.remove(packageName)
                }
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val saved = settingsRepository.setPageKeyTapTargetPackages(selectedPackages)
                if (!saved) {
                    Toast.makeText(this, R.string.labs_page_key_tap_apps_save_failed, Toast.LENGTH_SHORT)
                        .show()
                }
                updatePageKeyTapAppsSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getPageKeyTapTargetCandidates(): List<PageKeyTapTargetApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageNames = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it.isBlank() || it == packageName || it == "android" || it == "com.android.systemui" }
            .toSet()

        return packageNames
            .map { packageName ->
                val label = runCatching {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo)?.toString()
                }.getOrNull().orEmpty().ifBlank { packageName }

                PageKeyTapTargetApp(
                    packageName = packageName,
                    label = label
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun showWarningIfNeeded() {
        if (prefs.getBoolean(PREF_KEY_SKIP_WARNING, false)) return

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.labs_warning_title)
            .setMessage(R.string.labs_warning_message)
            .setPositiveButton(R.string.labs_warning_confirm) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(R.string.labs_warning_dont_show_again) { dialog, _ ->
                prefs.edit().putBoolean(PREF_KEY_SKIP_WARNING, true).apply()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "LabsPrefs"
        private const val PREF_KEY_SKIP_WARNING = "skip_labs_warning"
    }

    private data class PageKeyTapTargetApp(
        val packageName: String,
        val label: String
    )
}
