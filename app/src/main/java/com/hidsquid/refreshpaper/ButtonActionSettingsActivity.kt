package com.hidsquid.refreshpaper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.hidsquid.refreshpaper.databinding.ActivityButtonActionSettingsBinding
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.service.RemapDaemonStatus
import com.hidsquid.refreshpaper.ui.ChoiceDialog
import com.hidsquid.refreshpaper.ui.ChoiceItem

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
            showButtonActionDialog(
                currentAction = settingsRepository.getF1Action(),
                titleRes = R.string.dialog_title_f1_action
            ) { selectedAction ->
                if (!settingsRepository.setF1Action(selectedAction)) {
                    showSaveFailedToast()
                    return@showButtonActionDialog
                }
                updateActionSummaries()
            }
        }

        binding.quickButtonLongCard.setOnClickListener {
            showButtonActionDialog(
                currentAction = settingsRepository.getF1LongPressAction(settingsRepository.getF1Action()),
                titleRes = R.string.dialog_title_f1_action
            ) { selectedAction ->
                if (!settingsRepository.setF1LongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showButtonActionDialog
                }
                updateActionSummaries()
            }
        }

        binding.pageUpLongCard.setOnClickListener {
            showButtonActionDialog(
                currentAction = settingsRepository.getPageUpLongPressAction(),
                titleRes = R.string.dialog_title_page_up_long_action
            ) { selectedAction ->
                if (!settingsRepository.setPageUpLongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showButtonActionDialog
                }
                notifyPageKeyRemapStateChanged()
                updateActionSummaries()
            }
        }

        binding.pageDownLongCard.setOnClickListener {
            showButtonActionDialog(
                currentAction = settingsRepository.getPageDownLongPressAction(),
                titleRes = R.string.dialog_title_page_down_long_action
            ) { selectedAction ->
                if (!settingsRepository.setPageDownLongPressAction(selectedAction)) {
                    showSaveFailedToast()
                    return@showButtonActionDialog
                }
                notifyPageKeyRemapStateChanged()
                updateActionSummaries()
            }
        }
    }

    private fun updateActionSummaries() {
        binding.tvQuickButtonShortSetting.text = getString(
            getButtonActionLabelRes(settingsRepository.getF1Action())
        )
        binding.tvQuickButtonLongSetting.text = getString(
            getButtonActionLabelRes(
                settingsRepository.getF1LongPressAction(settingsRepository.getF1Action())
            )
        )
        binding.tvPageUpLongSetting.text = getString(
            getButtonActionLabelRes(settingsRepository.getPageUpLongPressAction())
        )
        binding.tvPageDownLongSetting.text = getString(
            getButtonActionLabelRes(settingsRepository.getPageDownLongPressAction())
        )

        val showWarning = RemapDaemonStatus.isNeeded(settingsRepository) &&
            !RemapDaemonStatus.isRunning(this)
        binding.pageKeyDaemonWarning.visibility = if (showWarning) View.VISIBLE else View.GONE
    }

    private val buttonActionChoices = listOf(
        ChoiceItem(ButtonAction.BACK, R.id.itemBack, R.id.checkBack),
        ChoiceItem(ButtonAction.SCREENSHOT, R.id.itemScreenshot, R.id.checkScreenshot),
        ChoiceItem(ButtonAction.QUICK_SETTINGS, R.id.itemQuickSettings, R.id.checkQuickSettings),
        ChoiceItem(ButtonAction.BRIGHTNESS, R.id.itemBrightness, R.id.checkBrightness),
        ChoiceItem(ButtonAction.MANUAL_REFRESH, R.id.itemManualRefresh, R.id.checkManualRefresh),
        ChoiceItem(ButtonAction.PAGE_KEY_SWAP, R.id.itemPageKeySwap, R.id.checkPageKeySwap),
        ChoiceItem(ButtonAction.HOME_LAUNCHER, R.id.itemHomeLauncher, R.id.checkHomeLauncher),
        ChoiceItem(ButtonAction.NONE, R.id.itemNone, R.id.checkNone),
    )

    private fun showButtonActionDialog(
        currentAction: ButtonAction,
        titleRes: Int,
        onSelected: (ButtonAction) -> Unit
    ) {
        val choiceDialog = ChoiceDialog(
            this,
            R.layout.dialog_button_action,
            buttonActionChoices,
            uncheckedVisibility = View.GONE,
        )
        choiceDialog.view.findViewById<TextView>(R.id.dialogTitle).setText(titleRes)
        choiceDialog.setChecked(currentAction)

        choiceDialog.show { selectedAction ->
            onSelected(selectedAction)
            dismiss()
        }
    }

    private fun getButtonActionLabelRes(action: ButtonAction): Int {
        return when (action) {
            ButtonAction.BACK -> R.string.setting_value_button_action_back
            ButtonAction.SCREENSHOT -> R.string.setting_value_button_action_screenshot
            ButtonAction.QUICK_SETTINGS -> R.string.setting_value_button_action_quick_settings
            ButtonAction.BRIGHTNESS -> R.string.setting_value_button_action_brightness
            ButtonAction.MANUAL_REFRESH -> R.string.setting_value_button_action_manual_refresh
            ButtonAction.PAGE_KEY_SWAP -> R.string.setting_value_button_action_page_key_swap
            ButtonAction.HOME_LAUNCHER -> R.string.setting_value_button_action_home_launcher
            ButtonAction.NONE -> R.string.setting_value_button_action_none
        }
    }

    private fun showSaveFailedToast() {
        Toast.makeText(this, R.string.button_action_save_failed, Toast.LENGTH_SHORT).show()
    }

    private fun notifyPageKeyRemapStateChanged() {
        sendBroadcast(Intent(KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED))
    }
}
