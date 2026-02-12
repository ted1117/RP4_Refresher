package com.hidsquid.refreshpaper.brightness

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository

class BrightnessActivity : ComponentActivity() {
    private lateinit var binder: BrightnessControlBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_brightness_control)

        binder = BrightnessControlBinder(this, SettingsRepository(this))
        binder.bind(findViewById(android.R.id.content))

        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
        setFinishOnTouchOutside(true)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BrightnessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
