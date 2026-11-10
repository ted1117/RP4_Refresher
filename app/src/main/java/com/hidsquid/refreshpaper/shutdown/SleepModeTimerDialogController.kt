package com.hidsquid.refreshpaper.shutdown

import android.content.Context
import android.widget.Toast
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.ui.ChoiceDialog
import com.hidsquid.refreshpaper.ui.ChoiceItem

class SleepModeTimerDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val timerItems = listOf(
        ChoiceItem(3, R.id.item3m, R.id.check3m),
        ChoiceItem(5, R.id.item5m, R.id.check5m),
        ChoiceItem(10, R.id.item10m, R.id.check10m),
        ChoiceItem(15, R.id.item15m, R.id.check15m),
        ChoiceItem(30, R.id.item30m, R.id.check30m),
        ChoiceItem(45, R.id.item45m, R.id.check45m),
        ChoiceItem(60, R.id.item60m, R.id.check60m),
        ChoiceItem(OFF_MINUTES, R.id.itemOff, R.id.checkOff),
    )

    fun show(onSaved: () -> Unit) {
        val currentMinutes = toDisplayMinutes(settingsRepository.getScreenOffTimeoutMillis())
        val checkedMinutes = if (timerItems.any { it.value == currentMinutes }) {
            currentMinutes
        } else {
            DEFAULT_DIALOG_MINUTES
        }
        val choiceDialog = ChoiceDialog(
            context,
            R.layout.dialog_sleep_mode_timer,
            timerItems,
            bringCheckedToFront = true,
        )
        choiceDialog.setChecked(checkedMinutes)

        choiceDialog.show { selectedMinutes ->
            val selectedMillis = if (selectedMinutes == OFF_MINUTES) {
                OFF_TIMEOUT_MILLIS
            } else {
                selectedMinutes * MILLIS_PER_MINUTE
            }

            val saved = settingsRepository.setScreenOffTimeoutMillis(selectedMillis)
            if (!saved) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_sleep_mode_timer_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@show
            }

            setChecked(selectedMinutes)
            onSaved()
            dismiss()
        }
    }

    fun getSelectedTimerLabel(): String {
        val minutes = toDisplayMinutes(settingsRepository.getScreenOffTimeoutMillis())
        if (minutes == OFF_MINUTES) return context.getString(R.string.sleep_mode_timer_option_off)

        return when (minutes) {
            3 -> context.getString(R.string.sleep_mode_timer_option_3m)
            5 -> context.getString(R.string.sleep_mode_timer_option_5m)
            10 -> context.getString(R.string.sleep_mode_timer_option_10m)
            15 -> context.getString(R.string.sleep_mode_timer_option_15m)
            30 -> context.getString(R.string.sleep_mode_timer_option_30m)
            45 -> context.getString(R.string.sleep_mode_timer_option_45m)
            60 -> context.getString(R.string.sleep_mode_timer_option_60m)
            else -> context.resources.getQuantityString(
                R.plurals.setting_value_sleep_mode_timer_minutes,
                minutes,
                minutes
            )
        }
    }

    private fun toDisplayMinutes(timeoutMillis: Int): Int {
        if (timeoutMillis >= OFF_TIMEOUT_MILLIS) return OFF_MINUTES
        if (timeoutMillis <= 0 || timeoutMillis % MILLIS_PER_MINUTE != 0) {
            return SettingsRepository.DEFAULT_SCREEN_OFF_TIMEOUT_MILLIS / MILLIS_PER_MINUTE
        }
        return timeoutMillis / MILLIS_PER_MINUTE
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000
        private const val OFF_MINUTES = -1
        private const val OFF_TIMEOUT_MILLIS = Int.MAX_VALUE
        private const val DEFAULT_DIALOG_MINUTES = 3
    }
}
