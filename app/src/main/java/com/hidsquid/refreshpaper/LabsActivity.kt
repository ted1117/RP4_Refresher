package com.hidsquid.refreshpaper

import android.os.Bundle
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.hidsquid.refreshpaper.databinding.ActivityLabsBinding
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.service.RemapDaemonStatus
import com.hidsquid.refreshpaper.utils.LsposedLauncher

class LabsActivity : Activity() {

    private lateinit var binding: ActivityLabsBinding
    private lateinit var settingsRepository: SettingsRepository
    private var isLsposedLaunchPending = false
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
        updateDaemonWarning()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        val autoEnabled = settingsRepository.isAutoRefreshEnabled()
        binding.touchRefreshSwitch.setOnCheckedChangeListener(null)
        binding.touchRefreshSwitch.isChecked = settingsRepository.isTouchRefreshEnabled()
        binding.touchRefreshSwitch.isEnabled = autoEnabled
        binding.touchRefreshSwitch.alpha = if (autoEnabled) 1f else 0.5f
        attachTouchRefreshListener()
    }

    private fun updateDaemonWarning() {
        val showWarning = RemapDaemonStatus.isNeeded(settingsRepository) &&
            !RemapDaemonStatus.isRunning(this)
        binding.pageKeyDaemonWarning.visibility = if (showWarning) View.VISIBLE else View.GONE
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

        binding.pageKeyTapCard.setOnClickListener {
            showPageKeyAppsDialog(
                selectedPackagesProvider = { settingsRepository.getPageKeyTapTargetPackages() },
                saveTargets = { settingsRepository.setPageKeyTapTargetPackages(it) },
                saveEnabled = { selectedNormal ->
                    val hasAnyTarget = selectedNormal.isNotEmpty() ||
                        settingsRepository.getPageKeySwapTargetPackages().isNotEmpty()
                    settingsRepository.setPageKeyTapEnabled(hasAnyTarget)
                },
                titleResId = R.string.labs_page_key_tap_apps_dialog_title,
                saveFailedResId = R.string.labs_page_key_tap_save_failed
            )
        }

        binding.pageKeySwapCard.setOnClickListener {
            showPageKeyAppsDialog(
                selectedPackagesProvider = { settingsRepository.getPageKeySwapTargetPackages() },
                saveTargets = { settingsRepository.setPageKeySwapTargetPackages(it) },
                saveEnabled = { selectedSwap ->
                    val hasAnyTarget = selectedSwap.isNotEmpty() ||
                        settingsRepository.getPageKeyTapTargetPackages().isNotEmpty()
                    settingsRepository.setPageKeyTapEnabled(hasAnyTarget)
                },
                titleResId = R.string.labs_page_key_swap_apps_dialog_title,
                saveFailedResId = R.string.labs_page_key_swap_save_failed
            )
        }

    }

    private fun attachTouchRefreshListener() {
        binding.touchRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingsRepository.setTouchRefreshEnabled(false)
                return@setOnCheckedChangeListener
            }

            if (isTouchRefreshLsposedAcknowledged()) {
                settingsRepository.setTouchRefreshEnabled(true)
                return@setOnCheckedChangeListener
            }

            binding.touchRefreshSwitch.setOnCheckedChangeListener(null)
            binding.touchRefreshSwitch.isChecked = false
            attachTouchRefreshListener()
            showTouchRefreshLsposedNoticeDialog()
        }
    }

    private fun showTouchRefreshLsposedNoticeDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.labs_touch_refresh_lsposed_notice_title)
            .setMessage(R.string.labs_touch_refresh_lsposed_notice_message)
            .setPositiveButton(R.string.open_lsposed_manager) { dialog, _ ->
                setTouchRefreshLsposedAcknowledged(true)
                settingsRepository.setTouchRefreshEnabled(true)
                binding.touchRefreshSwitch.setOnCheckedChangeListener(null)
                binding.touchRefreshSwitch.isChecked = true
                attachTouchRefreshListener()
                openLsposedManager()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                settingsRepository.setTouchRefreshEnabled(false)
                binding.touchRefreshSwitch.setOnCheckedChangeListener(null)
                binding.touchRefreshSwitch.isChecked = false
                attachTouchRefreshListener()
                dialog.dismiss()
            }
            .show()
    }

    private fun isTouchRefreshLsposedAcknowledged(): Boolean {
        return prefs.getBoolean(PREF_KEY_TOUCH_REFRESH_LSPOSED_ACKNOWLEDGED, false) ||
            prefs.getBoolean(PREF_KEY_SKIP_TOUCH_REFRESH_LSPOSED_NOTICE_LEGACY, false)
    }

    private fun setTouchRefreshLsposedAcknowledged(acknowledged: Boolean) {
        prefs.edit()
            .putBoolean(PREF_KEY_TOUCH_REFRESH_LSPOSED_ACKNOWLEDGED, acknowledged)
            .putBoolean(PREF_KEY_SKIP_TOUCH_REFRESH_LSPOSED_NOTICE_LEGACY, acknowledged)
            .apply()
    }

    private fun openLsposedManager() {
        if (isLsposedLaunchPending) return
        isLsposedLaunchPending = true
        Thread {
            val opened = LsposedLauncher.open(this)
            runOnUiThread {
                isLsposedLaunchPending = false
                if (!opened) {
                    Toast.makeText(this, R.string.lsposed_launch_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showPageKeyAppsDialog(
        selectedPackagesProvider: () -> Set<String>,
        saveTargets: (Set<String>) -> Boolean,
        saveEnabled: (Set<String>) -> Boolean,
        titleResId: Int,
        saveFailedResId: Int
    ) {
        val options = getPageKeyTapTargetCandidates()
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.labs_page_key_tap_apps_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPackages = selectedPackagesProvider().toMutableSet()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_page_key_tap_apps, null)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val itemContainer = dialogView.findViewById<LinearLayout>(R.id.itemContainer)
        titleView.setText(titleResId)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
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

                val savedTargets = saveTargets(toggled)
                val savedEnabled = saveEnabled(toggled)

                if (!savedTargets) {
                    Toast.makeText(this, R.string.labs_page_key_tap_apps_save_failed, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!savedEnabled) {
                    Toast.makeText(this, saveFailedResId, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                notifyPageKeyRemapStateChanged()
                updateDaemonWarning()

                selectedPackages.clear()
                selectedPackages.addAll(toggled)
                itemCheck.visibility = if (selectedPackages.contains(option.packageName)) View.VISIBLE else View.INVISIBLE
                if (itemCheck.visibility == View.VISIBLE) itemCheck.bringToFront()
            }

            itemContainer.addView(itemView)
        }

        dialog.show()
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
        private const val PREF_KEY_TOUCH_REFRESH_LSPOSED_ACKNOWLEDGED =
            "touch_refresh_lsposed_acknowledged"
        private const val PREF_KEY_SKIP_TOUCH_REFRESH_LSPOSED_NOTICE_LEGACY =
            "skip_touch_refresh_lsposed_notice"
    }

    private data class PageKeyTapTargetApp(
        val packageName: String,
        val label: String
    )
}
