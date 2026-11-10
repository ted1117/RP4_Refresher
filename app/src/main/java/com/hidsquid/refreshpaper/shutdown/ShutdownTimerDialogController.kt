package com.hidsquid.refreshpaper.shutdown

import android.content.Context
import android.widget.Toast
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.ui.ChoiceDialog
import com.hidsquid.refreshpaper.ui.ChoiceItem

class ShutdownTimerDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val hourItems = listOf(
        ChoiceItem(0, R.id.itemOff, R.id.checkOff),
        ChoiceItem(1, R.id.item1h, R.id.check1h),
        ChoiceItem(3, R.id.item3h, R.id.check3h),
        ChoiceItem(6, R.id.item6h, R.id.check6h),
        ChoiceItem(12, R.id.item12h, R.id.check12h),
        ChoiceItem(24, R.id.item24h, R.id.check24h),
        ChoiceItem(48, R.id.item48h, R.id.check48h),
        ChoiceItem(72, R.id.item72h, R.id.check72h),
    )

    fun show(onSaved: () -> Unit) {
        val currentHours = settingsRepository.getShutdownTimerHours()
        val choiceDialog = ChoiceDialog(
            context,
            R.layout.dialog_shutdown_timer,
            hourItems,
            bringCheckedToFront = true,
        )
        choiceDialog.setChecked(currentHours)

        choiceDialog.show { selectedHours ->
            val saved = settingsRepository.setShutdownTimerHours(selectedHours)
            if (!saved) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_shutdown_timer_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@show
            }

            setChecked(selectedHours)
            onSaved()
            dismiss()
        }
    }

    fun getSelectedTimerLabel(): String {
        val hours = settingsRepository.getShutdownTimerHours()
        if (hours == 0) return context.getString(R.string.shutdown_timer_option_off)
        return context.resources.getQuantityString(R.plurals.setting_value_shutdown_timer_hours, hours, hours)
    }
}
