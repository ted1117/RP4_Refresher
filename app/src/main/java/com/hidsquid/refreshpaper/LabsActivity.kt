package com.hidsquid.refreshpaper

import android.os.Bundle
import android.app.Dialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hidsquid.refreshpaper.databinding.ActivityLabsBinding
import com.hidsquid.refreshpaper.databinding.DialogLabsWarningBinding

class LabsActivity : AppCompatActivity() {

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
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.topAppBar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        val autoEnabled = settingsRepository.isAutoRefreshEnabled()
        binding.touchRefreshSwitch.isChecked = settingsRepository.isTouchRefreshEnabled()
        binding.touchRefreshSwitch.isEnabled = autoEnabled
        binding.touchRefreshSwitch.alpha = if (autoEnabled) 1f else 0.5f
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
    }

    private fun showWarningIfNeeded() {
        if (prefs.getBoolean(PREF_KEY_SKIP_WARNING, false)) return

        val dialogBinding = DialogLabsWarningBinding.inflate(layoutInflater)

        val dialog = Dialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(R.drawable.shape_dialog_bg)
        dialog.window?.decorView?.elevation = 0f

        dialogBinding.btnConfirm.setOnClickListener {
            if (dialogBinding.checkboxDontShowAgain.isChecked) {
                prefs.edit().putBoolean(PREF_KEY_SKIP_WARNING, true).apply()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        private const val PREFS_NAME = "LabsPrefs"
        private const val PREF_KEY_SKIP_WARNING = "skip_labs_warning"
    }
}
