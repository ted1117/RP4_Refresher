package com.hidsquid.refreshpaper

import android.content.Context
import android.content.Intent
import com.hidsquid.refreshpaper.service.KeyInputDetectingService
import com.hidsquid.refreshpaper.ui.ChoiceDialog
import com.hidsquid.refreshpaper.ui.ChoiceItem

class PageCountDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val items = listOf(
        ChoiceItem(1, R.id.item1, R.id.check1),
        ChoiceItem(3, R.id.item3, R.id.check3),
        ChoiceItem(5, R.id.item5, R.id.check5),
        ChoiceItem(10, R.id.item10, R.id.check10),
        ChoiceItem(20, R.id.item20, R.id.check20),
        ChoiceItem(OFF, R.id.itemOff, R.id.checkOff),
    )

    fun show(onSaved: () -> Unit) {
        val isEnabled = settingsRepository.isAutoRefreshEnabled()
        val currentCount = settingsRepository.getPagesPerRefresh()

        val choiceDialog = ChoiceDialog(context, R.layout.dialog_page_count, items)
        choiceDialog.setChecked(
            when {
                !isEnabled -> OFF
                items.any { it.value == currentCount } -> currentCount
                else -> DEFAULT_COUNT
            }
        )

        choiceDialog.show { selected ->
            if (selected == OFF) {
                settingsRepository.setAutoRefreshEnabled(false)
            } else {
                settingsRepository.setAutoRefreshEnabled(true)
                settingsRepository.setPagesPerRefresh(selected)
            }

            context.sendBroadcast(Intent(KeyInputDetectingService.ACTION_PAGE_KEY_REMAP_STATE_CHANGED))
            onSaved()
            dismiss()
        }
    }

    private companion object {
        const val OFF = 0
        const val DEFAULT_COUNT = 5
    }
}
