package com.hidsquid.refreshpaper.brightness

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.overlay.StackBarView
import kotlin.math.roundToInt

class BrightnessDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_brightness_control, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setDimAmount(0f)

        val brightnessPlus = dialogView.findViewById<TextView>(R.id.brightnessPlus)
        val brightnessMinus = dialogView.findViewById<TextView>(R.id.brightnessMinus)
        val colorPlus = dialogView.findViewById<TextView>(R.id.colorPlus)
        val colorMinus = dialogView.findViewById<TextView>(R.id.colorMinus)
        val brightnessStack = dialogView.findViewById<StackBarView>(R.id.brightnessStack)
        val colorStack = dialogView.findViewById<StackBarView>(R.id.colorStack)

        var brightness = settingsRepository.getScreenBrightness()
        var color = settingsRepository.getScreenBrightnessColor()
        var isInitialized = false
        var hasShownBrightnessZeroToast = false

        fun applyBrightness(value: Int) {
            val clamped = value.coerceIn(0, SettingsRepository.MAX_SCREEN_BRIGHTNESS)
            brightness = clamped
            settingsRepository.setScreenBrightness(brightness)
            val progress = brightness.toFloat() / SettingsRepository.MAX_SCREEN_BRIGHTNESS.toFloat()
            brightnessStack.setProgress(progress)
        }

        fun applyColor(value: Int) {
            if (isInitialized && brightness <= 0 && value > 0 && !hasShownBrightnessZeroToast) {
                Toast.makeText(
                    context,
                    "색 온도는 밝기가 1 이상이어야 반영됩니다",
                    Toast.LENGTH_SHORT
                ).show()
                hasShownBrightnessZeroToast = true
            }
            val clamped = value.coerceIn(0, SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR)
            color = clamped
            settingsRepository.setScreenBrightnessColor(color)
            val progress = color.toFloat() / SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR.toFloat()
            colorStack.setProgress(progress)
        }

        fun stepByHalfBar(
            currentValue: Int,
            maxValue: Int,
            barCount: Int,
            deltaHalfBars: Int
        ): Int {
            if (barCount <= 0 || maxValue <= 0) return currentValue
            val currentBars = (currentValue.toFloat() / maxValue.toFloat()) * barCount.toFloat()
            val snappedBars = (currentBars * 2f).roundToInt() / 2f
            val nextBars = (snappedBars + 0.5f * deltaHalfBars).coerceIn(0f, barCount.toFloat())
            return (nextBars / barCount.toFloat() * maxValue.toFloat()).roundToInt()
        }

        fun attachStackHandlers(
            view: View,
            maxValue: Int,
            barCountProvider: () -> Int,
            currentValueProvider: () -> Int,
            onValue: (Int) -> Unit
        ) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        val height = (v.height - v.paddingTop - v.paddingBottom).coerceAtLeast(1)
                        val y = (event.y - v.paddingTop).coerceIn(0f, height.toFloat())
                        val progress = 1f - (y / height.toFloat())
                        val raw = (progress * maxValue).roundToInt()
                        val snapped = stepByHalfBar(
                            raw,
                            maxValue,
                            barCountProvider(),
                            0
                        )
                        onValue(snapped)
                        true
                    }
                    else -> false
                }
            }

            view.setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_SCROLL) {
                    val delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    if (delta == 0f) return@setOnGenericMotionListener false
                    val direction = if (delta > 0) 1 else -1
                    val next = stepByHalfBar(
                        currentValueProvider(),
                        maxValue,
                        barCountProvider(),
                        direction
                    )
                    onValue(next)
                    true
                } else {
                    false
                }
            }
        }

        brightnessPlus.setOnClickListener {
            val next = stepByHalfBar(
                brightness,
                SettingsRepository.MAX_SCREEN_BRIGHTNESS,
                brightnessStack.getBarCount(),
                1
            )
            applyBrightness(next)
        }
        brightnessMinus.setOnClickListener {
            val next = stepByHalfBar(
                brightness,
                SettingsRepository.MAX_SCREEN_BRIGHTNESS,
                brightnessStack.getBarCount(),
                -1
            )
            applyBrightness(next)
        }
        colorPlus.setOnClickListener {
            val next = stepByHalfBar(
                color,
                SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR,
                colorStack.getBarCount(),
                1
            )
            applyColor(next)
        }
        colorMinus.setOnClickListener {
            val next = stepByHalfBar(
                color,
                SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR,
                colorStack.getBarCount(),
                -1
            )
            applyColor(next)
        }

        attachStackHandlers(
            brightnessStack,
            SettingsRepository.MAX_SCREEN_BRIGHTNESS,
            { brightnessStack.getBarCount() },
            { brightness },
            ::applyBrightness
        )
        attachStackHandlers(
            colorStack,
            SettingsRepository.MAX_SCREEN_BRIGHTNESS_COLOR,
            { colorStack.getBarCount() },
            { color },
            ::applyColor
        )

        applyBrightness(brightness)
        applyColor(color)
        isInitialized = true

        dialog.show()
    }
}
