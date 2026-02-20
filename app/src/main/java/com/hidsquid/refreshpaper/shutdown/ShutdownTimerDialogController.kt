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

class ShutdownTimerDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val hourItems = listOf(
        1 to (R.id.item1h to R.id.check1h),
        3 to (R.id.item3h to R.id.check3h),
        6 to (R.id.item6h to R.id.check6h),
        12 to (R.id.item12h to R.id.check12h),
        24 to (R.id.item24h to R.id.check24h),
        48 to (R.id.item48h to R.id.check48h),
        72 to (R.id.item72h to R.id.check72h),
    )

    fun show(onSaved: () -> Unit) {
        val currentHours = settingsRepository.getShutdownTimerHours()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_shutdown_timer, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val checkViews = hourItems.map { (_, ids) ->
            dialogView.findViewById<ImageView>(ids.second)
        }

        fun setChecked(selectedHours: Int) {
            hourItems.forEachIndexed { index, (hours, _) ->
                val isSelected = hours == selectedHours
                checkViews[index].visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                if (isSelected) checkViews[index].bringToFront()
            }
        }

        val onClick = View.OnClickListener { view ->
            val selectedHours = when (view.id) {
                R.id.item1h -> 1
                R.id.item3h -> 3
                R.id.item6h -> 6
                R.id.item12h -> 12
                R.id.item24h -> 24
                R.id.item48h -> 48
                R.id.item72h -> 72
                else -> return@OnClickListener
            }

            val saved = settingsRepository.setShutdownTimerHours(selectedHours)
            if (!saved) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_shutdown_timer_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@OnClickListener
            }

            setChecked(selectedHours)
            onSaved()
            dialog.dismiss()
        }

        hourItems.forEach { (_, ids) ->
            dialogView.findViewById<RelativeLayout>(ids.first).setOnClickListener(onClick)
        }

        setChecked(currentHours)

        dialog.show()
        val widthPx = context.resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun getSelectedTimerLabel(): String {
        val hours = settingsRepository.getShutdownTimerHours()
        return context.resources.getQuantityString(R.plurals.setting_value_shutdown_timer_hours, hours, hours)
    }
}
