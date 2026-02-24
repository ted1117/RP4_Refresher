package com.hidsquid.refreshpaper.shutdown

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository

class SleepModeTimerDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val timerItems = listOf(
        3 to (R.id.item3m to R.id.check3m),
        5 to (R.id.item5m to R.id.check5m),
        10 to (R.id.item10m to R.id.check10m),
        15 to (R.id.item15m to R.id.check15m),
        30 to (R.id.item30m to R.id.check30m),
        45 to (R.id.item45m to R.id.check45m),
        60 to (R.id.item60m to R.id.check60m),
        OFF_MINUTES to (R.id.itemOff to R.id.checkOff),
    )

    fun show(onSaved: () -> Unit) {
        val currentMinutes = toDisplayMinutes(settingsRepository.getScreenOffTimeoutMillis())
        val checkedMinutes = if (timerItems.any { it.first == currentMinutes }) {
            currentMinutes
        } else {
            DEFAULT_DIALOG_MINUTES
        }
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_sleep_mode_timer, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val checkViews = timerItems.map { (_, ids) ->
            dialogView.findViewById<ImageView>(ids.second)
        }

        fun setChecked(selectedMinutes: Int) {
            timerItems.forEachIndexed { index, (minutes, _) ->
                val isSelected = minutes == selectedMinutes
                checkViews[index].visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                if (isSelected) checkViews[index].bringToFront()
            }
        }

        val onClick = View.OnClickListener { view ->
            val selectedMinutes = when (view.id) {
                R.id.item3m -> 3
                R.id.item5m -> 5
                R.id.item10m -> 10
                R.id.item15m -> 15
                R.id.item30m -> 30
                R.id.item45m -> 45
                R.id.item60m -> 60
                R.id.itemOff -> OFF_MINUTES
                else -> return@OnClickListener
            }

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
                return@OnClickListener
            }

            setChecked(selectedMinutes)
            onSaved()
            dialog.dismiss()
        }

        timerItems.forEach { (_, ids) ->
            dialogView.findViewById<RelativeLayout>(ids.first).setOnClickListener(onClick)
        }

        setChecked(checkedMinutes)

        dialog.show()
        val widthPx = context.resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
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
