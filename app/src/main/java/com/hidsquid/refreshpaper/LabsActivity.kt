package com.hidsquid.refreshpaper

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.databinding.ActivityLabsBinding
import com.hidsquid.refreshpaper.service.KeyInputDetectingService

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

    private fun showPageKeyTapAppsDialog() {
        val options = getPageKeyTapTargetCandidates()
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.labs_page_key_tap_apps_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPackages = settingsRepository.getPageKeyTapTargetPackages().toMutableSet()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_page_key_tap_apps, null)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val itemContainer = dialogView.findViewById<LinearLayout>(R.id.itemContainer)
        titleView.setText(R.string.labs_page_key_tap_apps_dialog_title)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        options.forEachIndexed { index, option ->
            val itemView =
                LayoutInflater.from(this).inflate(R.layout.dialog_home_launcher_item, itemContainer, false)
            val itemRoot = itemView.findViewById<RelativeLayout>(R.id.itemRoot)
            val itemTitle = itemView.findViewById<TextView>(R.id.itemTitle)
            val itemCheck = itemView.findViewById<ImageView>(R.id.itemCheck)
            val itemDivider = itemView.findViewById<View>(R.id.itemDivider)

            itemTitle.text = option.label
            itemCheck.visibility = if (selectedPackages.contains(option.packageName)) View.VISIBLE else View.INVISIBLE
            itemDivider.visibility = if (index == options.lastIndex) View.GONE else View.VISIBLE

            itemRoot.setOnClickListener {
                val toggled = selectedPackages.toMutableSet().apply {
                    if (contains(option.packageName)) remove(option.packageName) else add(option.packageName)
                }

                val savedTargets = settingsRepository.setPageKeyTapTargetPackages(toggled)
                val savedEnabled = settingsRepository.setPageKeyTapEnabled(toggled.isNotEmpty())

                if (!savedTargets) {
                    Toast.makeText(this, R.string.labs_page_key_tap_apps_save_failed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!savedEnabled) {
                    Toast.makeText(this, R.string.labs_page_key_tap_save_failed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                notifyPageKeyRemapStateChanged()

                selectedPackages.clear()
                selectedPackages.addAll(toggled)
                itemCheck.visibility = if (selectedPackages.contains(option.packageName)) View.VISIBLE else View.INVISIBLE
                if (itemCheck.visibility == View.VISIBLE) itemCheck.bringToFront()
            }

            itemContainer.addView(itemView)
        }

        dialog.show()
        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun getPageKeyTapTargetCandidates(): List<PageKeyTapTargetApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it.isBlank() || it == packageName || it == "android" || it == "com.android.systemui" }
            .distinct()
            .mapNotNull { targetPackageName ->
                val appInfo = runCatching {
                    packageManager.getApplicationInfo(targetPackageName, 0)
                }.getOrNull() ?: return@mapNotNull null

                val isSystemApp =
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystemApp) return@mapNotNull null

                val label = packageManager.getApplicationLabel(appInfo)?.toString()
                    .orEmpty()
                    .ifBlank { targetPackageName }

                PageKeyTapTargetApp(
                    packageName = targetPackageName,
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

    private fun notifyPageKeyRemapStateChanged() {
        sendBroadcast(Intent(KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED))
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
