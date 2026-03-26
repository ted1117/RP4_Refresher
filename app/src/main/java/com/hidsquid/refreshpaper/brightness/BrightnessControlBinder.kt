package com.hidsquid.refreshpaper.brightness

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.overlay.StackBarView
import kotlin.math.max
import kotlin.math.roundToInt

class BrightnessControlBinder(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private var hasShownBrightnessZeroToast = false
    private var isInitialized = false

    fun bind(root: View) {
        val brightnessPlus = root.findViewById<TextView>(R.id.brightnessPlus)
        val brightnessMinus = root.findViewById<TextView>(R.id.brightnessMinus)
        val brightnessStack = root.findViewById<StackBarView>(R.id.brightnessStack)

        val colorPlus = root.findViewById<TextView>(R.id.brightnessColorPlus)
        val colorMinus = root.findViewById<TextView>(R.id.brightnessColorMinus)
        val colorStack = root.findViewById<StackBarView>(R.id.brightnessColorStack)

        var brightness = settingsRepository.getScreenBrightness()
        var color = settingsRepository.getScreenBrightnessColor()

        fun updateBrightness(value: Int) {
            brightness = value.coerceIn(0, SettingsRepository.MAX_SCREEN_BRIGHTNESS)
            settingsRepository.setScreenBrightness(brightness)
            val progress = brightness.toFloat() / SettingsRepository.MAX_SCREEN_BRIGHTNESS.toFloat()
            brightnessStack.setProgress(progress)
        }

        fun updateColor(value: Int) {
            color = value.coerceIn(0, SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR)
            settingsRepository.setScreenBrightnessColor(color)
            val progress = color.toFloat() / SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR.toFloat()
            colorStack.setProgress(progress)

            if (isInitialized && brightness <= 0 && color > 0 && !hasShownBrightnessZeroToast) {
                hasShownBrightnessZeroToast = true
                Toast.makeText(
                    context,
                    "색 온도는 밝기가 1 이상이어야 반영됩니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        fun stepSize(maxValue: Int, bars: Int): Int {
            val steps = max(1, bars) * 2
            return max(1, maxValue / steps)
        }

        val brightnessStep = stepSize(
            SettingsRepository.MAX_SCREEN_BRIGHTNESS,
            brightnessStack.getBarCount()
        )
        val colorStep = stepSize(
            SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR,
            colorStack.getBarCount()
        )

        brightnessPlus.setOnClickListener {
            updateBrightness(brightness + brightnessStep)
        }
        brightnessMinus.setOnClickListener {
            updateBrightness(brightness - brightnessStep)
        }

        colorPlus.setOnClickListener {
            updateColor(color + colorStep)
        }
        colorMinus.setOnClickListener {
            updateColor(color - colorStep)
        }

        brightnessStack.setOnProgressChangedListener { progress ->
            val next = (progress * SettingsRepository.MAX_SCREEN_BRIGHTNESS).roundToInt()
            updateBrightness(next)
        }

        colorStack.setOnProgressChangedListener { progress ->
            val next = (progress * SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR).roundToInt()
            updateColor(next)
        }

        updateBrightness(brightness)
        updateColor(color)
        isInitialized = true
    }
}
