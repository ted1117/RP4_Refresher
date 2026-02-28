package com.hidsquid.refreshpaper.brightness

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository

class BrightnessActivity : ComponentActivity() {
    private lateinit var binder: BrightnessControlBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_brightness_control)

        binder = BrightnessControlBinder(this, SettingsRepository(this))
        binder.bind(findViewById(android.R.id.content))

        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        window.setLayout(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
        setFinishOnTouchOutside(true)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BrightnessActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
