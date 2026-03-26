package com.hidsquid.refreshpaper.brightness

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository

class BrightnessDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val binder = BrightnessControlBinder(context, settingsRepository)

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_brightness_control, null)

        val dialog = AlertDialog.Builder(
            context,
            android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar
        )
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        binder.bind(dialogView)

        dialog.show()
    }
}
