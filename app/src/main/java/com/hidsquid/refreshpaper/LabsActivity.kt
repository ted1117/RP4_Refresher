package com.hidsquid.refreshpaper

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
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
        binding.powerPageScreenshotSwitch.isChecked = settingsRepository.isPowerPageScreenshotEnabled()
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
}
