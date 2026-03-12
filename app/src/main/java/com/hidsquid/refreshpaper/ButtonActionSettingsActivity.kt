package com.hidsquid.refreshpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.hidsquid.refreshpaper.databinding.ActivityButtonActionSettingsBinding
import com.hidsquid.refreshpaper.service.KeyInputDetectingService

class ButtonActionSettingsActivity : Activity() {

    private lateinit var binding: ActivityButtonActionSettingsBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityButtonActionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        setupToolbar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateActionSummaries()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.quickButtonShortCard.setOnClickListener {
            showF1ActionDialog(
                currentAction = settingsRepository.getF1Action(),
                titleRes = R.string.dialog_title_f1_short_action
            ) { selectedAction ->
                if (!settingsRepository.setF1Action(selectedAction)) {
                    showSaveFailedToast()
                    return@showF1ActionDialog
                }
                updateActionSummaries()
            }
        }

        binding.quickButtonLongCard.setOnClickListener {
            showF1ActionDialog(
                currentAction = settingsRepository.getF1LongPressAction(settingsRepository.getF1Action()),
                titleRes = R.string.dialog_title_f1_long_action
            ) { selectedAction ->
                if (!settingsRepository.setF1LongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showF1ActionDialog
                }
                updateActionSummaries()
            }
        }

        binding.pageUpLongCard.setOnClickListener {
            showF1ActionDialog(
                currentAction = settingsRepository.getPageUpLongPressAction(),
                titleRes = R.string.dialog_title_page_up_long_action
            ) { selectedAction ->
                if (!settingsRepository.setPageUpLongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showF1ActionDialog
                }
                notifyPageKeyRemapStateChanged()
                updateActionSummaries()
            }
        }

        binding.pageDownLongCard.setOnClickListener {
            showF1ActionDialog(
                currentAction = settingsRepository.getPageDownLongPressAction(),
                titleRes = R.string.dialog_title_page_down_long_action
            ) { selectedAction ->
                if (!settingsRepository.setPageDownLongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showF1ActionDialog
                }
                notifyPageKeyRemapStateChanged()
                updateActionSummaries()
            }
        }
    }

    private fun updateActionSummaries() {
        binding.tvQuickButtonShortSetting.text = getString(
            getF1ActionLabelRes(settingsRepository.getF1Action())
        )
        binding.tvQuickButtonLongSetting.text = getString(
            getF1ActionLabelRes(
                settingsRepository.getF1LongPressAction(settingsRepository.getF1Action())
            )
        )
        binding.tvPageUpLongSetting.text = getString(
            getF1ActionLabelRes(settingsRepository.getPageUpLongPressAction())
        )
        binding.tvPageDownLongSetting.text = getString(
            getF1ActionLabelRes(settingsRepository.getPageDownLongPressAction())
        )
    }

    private fun showF1ActionDialog(
        currentAction: Int,
        titleRes: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_f1_action, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<TextView>(R.id.dialogTitle).setText(titleRes)

        val itemBack = dialogView.findViewById<RelativeLayout>(R.id.itemBack)
        val itemScreenshot = dialogView.findViewById<RelativeLayout>(R.id.itemScreenshot)
        val itemQuickSettings = dialogView.findViewById<RelativeLayout>(R.id.itemQuickSettings)
        val itemBrightness = dialogView.findViewById<RelativeLayout>(R.id.itemBrightness)
        val itemManualRefresh = dialogView.findViewById<RelativeLayout>(R.id.itemManualRefresh)
        val itemPageKeySwap = dialogView.findViewById<RelativeLayout>(R.id.itemPageKeySwap)
        val itemHomeLauncher = dialogView.findViewById<RelativeLayout>(R.id.itemHomeLauncher)
        val itemNone = dialogView.findViewById<RelativeLayout>(R.id.itemNone)

        val checkBack = dialogView.findViewById<ImageView>(R.id.checkBack)
        val checkScreenshot = dialogView.findViewById<ImageView>(R.id.checkScreenshot)
        val checkQuickSettings = dialogView.findViewById<ImageView>(R.id.checkQuickSettings)
        val checkBrightness = dialogView.findViewById<ImageView>(R.id.checkBrightness)
        val checkManualRefresh = dialogView.findViewById<ImageView>(R.id.checkManualRefresh)
        val checkPageKeySwap = dialogView.findViewById<ImageView>(R.id.checkPageKeySwap)
        val checkHomeLauncher = dialogView.findViewById<ImageView>(R.id.checkHomeLauncher)
        val checkNone = dialogView.findViewById<ImageView>(R.id.checkNone)

        fun setChecked(selectedAction: Int) {
            checkBack.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_BACK) View.VISIBLE else View.GONE
            checkScreenshot.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_SCREENSHOT) View.VISIBLE else View.GONE
            checkQuickSettings.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_QUICK_SETTINGS) View.VISIBLE else View.GONE
            checkBrightness.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_BRIGHTNESS) View.VISIBLE else View.GONE
            checkManualRefresh.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_MANUAL_REFRESH) View.VISIBLE else View.GONE
            checkPageKeySwap.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_PAGE_KEY_SWAP) View.VISIBLE else View.GONE
            checkHomeLauncher.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_HOME_LAUNCHER) View.VISIBLE else View.GONE
            checkNone.visibility =
                if (selectedAction == SettingsRepository.F1_ACTION_NONE) View.VISIBLE else View.GONE
        }

        setChecked(currentAction)

        val onClick = View.OnClickListener { view ->
            val selectedAction = when (view.id) {
                R.id.itemBack -> SettingsRepository.F1_ACTION_BACK
                R.id.itemScreenshot -> SettingsRepository.F1_ACTION_SCREENSHOT
                R.id.itemQuickSettings -> SettingsRepository.F1_ACTION_QUICK_SETTINGS
                R.id.itemBrightness -> SettingsRepository.F1_ACTION_BRIGHTNESS
                R.id.itemManualRefresh -> SettingsRepository.F1_ACTION_MANUAL_REFRESH
                R.id.itemPageKeySwap -> SettingsRepository.F1_ACTION_PAGE_KEY_SWAP
                R.id.itemHomeLauncher -> SettingsRepository.F1_ACTION_HOME_LAUNCHER
                R.id.itemNone -> SettingsRepository.F1_ACTION_NONE
                else -> SettingsRepository.F1_ACTION_BACK
            }

            onSelected(selectedAction)
            dialog.dismiss()
        }

        itemBack.setOnClickListener(onClick)
        itemScreenshot.setOnClickListener(onClick)
        itemQuickSettings.setOnClickListener(onClick)
        itemBrightness.setOnClickListener(onClick)
        itemManualRefresh.setOnClickListener(onClick)
        itemPageKeySwap.setOnClickListener(onClick)
        itemHomeLauncher.setOnClickListener(onClick)
        itemNone.setOnClickListener(onClick)

        dialog.show()
    }

    private fun getF1ActionLabelRes(action: Int): Int {
        return when (action) {
            SettingsRepository.F1_ACTION_BACK -> R.string.setting_value_f1_action_back
            SettingsRepository.F1_ACTION_SCREENSHOT -> R.string.setting_value_f1_action_screenshot
            SettingsRepository.F1_ACTION_QUICK_SETTINGS -> R.string.setting_value_f1_action_quick_settings
            SettingsRepository.F1_ACTION_BRIGHTNESS -> R.string.setting_value_f1_action_brightness
            SettingsRepository.F1_ACTION_MANUAL_REFRESH -> R.string.setting_value_f1_action_manual_refresh
            SettingsRepository.F1_ACTION_PAGE_KEY_SWAP -> R.string.setting_value_f1_action_page_key_swap
            SettingsRepository.F1_ACTION_HOME_LAUNCHER -> R.string.setting_value_f1_action_home_launcher
            SettingsRepository.F1_ACTION_NONE -> R.string.setting_value_f1_action_none
            else -> R.string.setting_value_f1_action_none
        }
    }

    private fun showSaveFailedToast() {
        Toast.makeText(this, R.string.button_action_save_failed, Toast.LENGTH_SHORT).show()
    }

    private fun notifyPageKeyRemapStateChanged() {
        sendBroadcast(Intent(KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED))
    }
}
