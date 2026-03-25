package com.hidsquid.refreshpaper

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hidsquid.refreshpaper.epd.EPDDisplayModeController
import com.hidsquid.refreshpaper.epd.EpdModeDialogController
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.shutdown.SleepModeTimerDialogController

class StatusBarSettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var epdController: EPDDisplayModeController
    private lateinit var epdModeDialogController: EpdModeDialogController
    private lateinit var pageCountDialogController: PageCountDialogController
    private lateinit var sleepModeTimerDialogController: SleepModeTimerDialogController

    private lateinit var autoRefreshSummary: TextView
    private lateinit var epdModeSummary: TextView
    private lateinit var sleepModeTimerSummary: TextView
    private lateinit var pageKeyRemapSummary: TextView
    private lateinit var pageKeySwapSummary: TextView
    private lateinit var pageKeyRemapCard: View
    private lateinit var pageKeySwapCard: View

    private var currentTargetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_bar_settings)
        setFinishOnTouchOutside(true)

        settingsRepository = SettingsRepository(this)
        epdController = EPDDisplayModeController(this)
        epdModeDialogController = EpdModeDialogController(this, epdController, lifecycleScope)
        pageCountDialogController = PageCountDialogController(this, settingsRepository)
        sleepModeTimerDialogController = SleepModeTimerDialogController(this, settingsRepository)

        autoRefreshSummary = findViewById(R.id.tvQuickAutoRefreshSummary)
        epdModeSummary = findViewById(R.id.tvQuickEpdModeSummary)
        sleepModeTimerSummary = findViewById(R.id.tvQuickSleepModeTimerSummary)
        pageKeyRemapSummary = findViewById(R.id.tvQuickPageKeyRemapSummary)
        pageKeySwapSummary = findViewById(R.id.tvQuickPageKeySwapSummary)
        pageKeyRemapCard = findViewById(R.id.pageKeyRemapCard)
        pageKeySwapCard = findViewById(R.id.pageKeySwapCard)

        setupListeners()
        loadSettings()

        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.autoRefreshCard).setOnClickListener {
            pageCountDialogController.show { updateAutoRefreshSummary() }
        }

        findViewById<View>(R.id.epdModeCard).setOnClickListener {
            epdModeDialogController.show { updateEpdModeSummary() }
        }

        findViewById<View>(R.id.sleepModeTimerCard).setOnClickListener {
            sleepModeTimerDialogController.show {
                updateSleepModeTimerSummary()
            }
        }

        findViewById<View>(R.id.screenshotCard).setOnClickListener {
            requestQuickSettingsScreenshot()
        }

        pageKeyRemapCard.setOnClickListener {
            showPageKeyRemapDialog()
        }

        pageKeySwapCard.setOnClickListener {
            showPageKeySwapDialog()
        }
    }

    private fun loadSettings() {
        updateAutoRefreshSummary()
        updateEpdModeSummary()
        updateSleepModeTimerSummary()
        resolveCurrentTargetPackage()
        bindCurrentAppToggles()
    }

    private fun resolveCurrentTargetPackage() {
        val candidate = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        currentTargetPackage = when (candidate) {
            null -> null
            packageName,
            "android",
            "com.android.systemui" -> null
            else -> candidate
        }
    }

    private fun bindCurrentAppToggles() {
        val targetPackage = currentTargetPackage
        when {
            targetPackage.isNullOrBlank() -> {
                val unavailableText = getString(R.string.quick_setting_summary_unavailable)
                pageKeyRemapSummary.text = unavailableText
                pageKeySwapSummary.text = unavailableText
                pageKeySwapSummary.visibility = View.VISIBLE

                setPageKeyCardsEnabled(false)
            }

            targetPackage == BLOCKED_APP_PACKAGE_NAME -> {
                val blockedText = getString(R.string.quick_setting_summary_current_app_blocked)
                pageKeyRemapSummary.text = blockedText
                pageKeySwapSummary.text = blockedText
                pageKeySwapSummary.visibility = View.VISIBLE

                setPageKeyCardsEnabled(false)
            }

            else -> {
                val remapEnabled = settingsRepository.isPageKeyTapTargetPackage(targetPackage)
                val swapEnabled = settingsRepository.isPageKeySwapTargetPackage(targetPackage)

                pageKeyRemapSummary.text = getString(
                    if (remapEnabled) {
                        R.string.quick_setting_value_volume_key
                    } else {
                        R.string.quick_setting_value_page_key
                    }
                )
                pageKeySwapSummary.text = getString(
                    if (swapEnabled) {
                        R.string.quick_setting_value_next_prev
                    } else {
                        R.string.quick_setting_value_prev_next
                    }
                )
                pageKeySwapSummary.visibility = View.VISIBLE

                setPageKeyCardsEnabled(true)
            }
        }
    }

    private fun setPageKeyCardsEnabled(enabled: Boolean) {
        pageKeyRemapCard.isEnabled = enabled
        pageKeySwapCard.isEnabled = enabled
        pageKeyRemapCard.alpha = if (enabled) 1f else 0.5f
        pageKeySwapCard.alpha = if (enabled) 1f else 0.5f
    }

    private fun showPageKeyRemapDialog() {
        val targetPackage = currentTargetPackage ?: return
        showPageKeyToggleDialog(
            layoutResId = R.layout.dialog_page_key_remap_toggle,
            currentlyEnabled = settingsRepository.isPageKeyTapTargetPackage(targetPackage),
            onApply = ::applyPageKeyRemapToggle
        )
    }

    private fun showPageKeySwapDialog() {
        val targetPackage = currentTargetPackage ?: return
        showPageKeyToggleDialog(
            layoutResId = R.layout.dialog_page_key_swap_toggle,
            currentlyEnabled = settingsRepository.isPageKeySwapTargetPackage(targetPackage),
            onApply = ::applyPageKeySwapToggle
        )
    }

    private fun showPageKeyToggleDialog(
        layoutResId: Int,
        currentlyEnabled: Boolean,
        onApply: (Boolean) -> Boolean,
    ) {
        val targetPackage = currentTargetPackage ?: return
        if (targetPackage == BLOCKED_APP_PACKAGE_NAME) return

        val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val itemOn = dialogView.findViewById<RelativeLayout>(R.id.itemOn)
        val itemOff = dialogView.findViewById<RelativeLayout>(R.id.itemOff)
        val checkOn = dialogView.findViewById<ImageView>(R.id.checkOn)
        val checkOff = dialogView.findViewById<ImageView>(R.id.checkOff)

        fun setChecked(enabled: Boolean) {
            checkOn.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
            checkOff.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
            if (enabled) {
                checkOn.bringToFront()
            } else {
                checkOff.bringToFront()
            }
        }

        val onClick = View.OnClickListener { view ->
            val selectedEnabled = when (view.id) {
                R.id.itemOn -> true
                R.id.itemOff -> false
                else -> return@OnClickListener
            }

            if (selectedEnabled != currentlyEnabled) {
                if (!onApply(selectedEnabled)) {
                    bindCurrentAppToggles()
                    setChecked(currentlyEnabled)
                    return@OnClickListener
                }
            }

            bindCurrentAppToggles()
            dialog.dismiss()
        }

        itemOn.setOnClickListener(onClick)
        itemOff.setOnClickListener(onClick)
        setChecked(currentlyEnabled)

        dialog.show()
    }

    private fun applyPageKeyRemapToggle(enabled: Boolean): Boolean {
        val targetPackage = currentTargetPackage ?: return false
        if (targetPackage == BLOCKED_APP_PACKAGE_NAME) return false

        val updatedTargets = settingsRepository.getPageKeyTapTargetPackages().toMutableSet().apply {
            if (enabled) add(targetPackage) else remove(targetPackage)
        }

        if (!settingsRepository.setPageKeyTapTargetPackages(updatedTargets)) {
            Toast.makeText(this, R.string.labs_page_key_tap_apps_save_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val hasAnyTarget = updatedTargets.isNotEmpty() ||
            settingsRepository.getPageKeySwapTargetPackages().isNotEmpty()
        if (!settingsRepository.setPageKeyTapEnabled(hasAnyTarget)) {
            Toast.makeText(this, R.string.labs_page_key_tap_save_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        notifyPageKeyRemapStateChanged()
        return true
    }

    private fun applyPageKeySwapToggle(enabled: Boolean): Boolean {
        val targetPackage = currentTargetPackage ?: return false
        if (targetPackage == BLOCKED_APP_PACKAGE_NAME) return false

        val updatedTargets = settingsRepository.getPageKeySwapTargetPackages().toMutableSet().apply {
            if (enabled) add(targetPackage) else remove(targetPackage)
        }

        if (!settingsRepository.setPageKeySwapTargetPackages(updatedTargets)) {
            Toast.makeText(this, R.string.labs_page_key_tap_apps_save_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val hasAnyTarget = updatedTargets.isNotEmpty() ||
            settingsRepository.getPageKeyTapTargetPackages().isNotEmpty()
        if (!settingsRepository.setPageKeyTapEnabled(hasAnyTarget)) {
            Toast.makeText(this, R.string.labs_page_key_swap_save_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        notifyPageKeyRemapStateChanged()
        return true
    }

    private fun updateAutoRefreshSummary() {
        val enabled = settingsRepository.isAutoRefreshEnabled()
        val count = settingsRepository.getPagesPerRefresh()

        if (enabled) {
            autoRefreshSummary.text = getString(R.string.setting_summary_auto_refresh_on, count)
            autoRefreshSummary.alpha = 1f
        } else {
            autoRefreshSummary.text = getString(R.string.setting_summary_auto_refresh_off)
            autoRefreshSummary.alpha = 0.5f
        }
    }

    private fun notifyPageKeyRemapStateChanged() {
        sendBroadcast(Intent(KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED))
    }

    private fun updateEpdModeSummary() {
        epdModeDialogController.loadSelectedModeLabel { label ->
            epdModeSummary.text = label
        }
    }

    private fun updateSleepModeTimerSummary() {
        sleepModeTimerSummary.text = sleepModeTimerDialogController.getSelectedTimerLabel()
    }

    private fun requestQuickSettingsScreenshot() {
        val appContext = applicationContext
        window?.decorView?.alpha = 0f
        finish()
        overridePendingTransition(0, 0)

        Handler(Looper.getMainLooper()).postDelayed(
            {
                appContext.sendBroadcast(
                    Intent(KeyInputDetectingService.ACTION_TAKE_SCREENSHOT).apply {
                        setPackage(appContext.packageName)
                    }
                )
            },
            QUICK_SETTINGS_SCREENSHOT_DELAY_MS
        )
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE =
            "com.hidsquid.refreshpaper.extra.QUICK_SETTINGS_TARGET_PACKAGE"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
        private const val QUICK_SETTINGS_SCREENSHOT_DELAY_MS = 200L
    }
}
