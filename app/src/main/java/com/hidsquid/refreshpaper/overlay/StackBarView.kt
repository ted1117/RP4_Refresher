package com.hidsquid.refreshpaper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.hidsquid.refreshpaper.R
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt

class StackBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var barCount: Int = 18
    private var progress: Float = 0.5f
    private var barGapPx: Float = dpToPx(6f)
    private var barThicknessPx: Float = 0f
    private var activeColor: Int = Color.BLACK
    private var inactiveColor: Int = "#D0D0D0".toColorInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var onProgressChanged: ((Float) -> Unit)? = null

    init {
        isClickable = true
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.StackBarView) {
                barCount = getInt(R.styleable.StackBarView_barCount, barCount).coerceAtLeast(1)
                progress = getFloat(R.styleable.StackBarView_progress, progress)
                barGapPx = getDimension(R.styleable.StackBarView_barGap, barGapPx)
                barThicknessPx = getDimension(R.styleable.StackBarView_barThickness, barThicknessPx)
                activeColor = getColor(R.styleable.StackBarView_activeColor, activeColor)
                inactiveColor = getColor(R.styleable.StackBarView_inactiveColor, inactiveColor)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val safeBarCount = max(1, barCount)
        val gaps = max(0, safeBarCount - 1)
        val totalGap = barGapPx * gaps
        val barSlotHeight = (contentHeight - totalGap) / safeBarCount
        if (barSlotHeight <= 0f) return

        val desiredThickness = if (barThicknessPx > 0f) barThicknessPx else barSlotHeight * 0.55f
        val barHeight = min(barSlotHeight, desiredThickness)

        val activeUnits = progress.coerceIn(0f, 1f) * safeBarCount.toFloat()
        val fullBars = floor(activeUnits).toInt().coerceIn(0, safeBarCount)
        val hasHalf = (activeUnits - fullBars) >= 0.5f && fullBars < safeBarCount

        for (i in 0 until safeBarCount) {
            val yFromBottom = i.toFloat()
            val top = paddingTop + (safeBarCount - 1 - yFromBottom) * (barSlotHeight + barGapPx) +
                (barSlotHeight - barHeight) / 2f
            val bottom = top + barHeight
            paint.color = inactiveColor
            canvas.drawRect(
                paddingLeft.toFloat(),
                top,
                (paddingLeft + contentWidth).toFloat(),
                bottom,
                paint
            )

            if (i < fullBars) {
                paint.color = activeColor
                canvas.drawRect(
                    paddingLeft.toFloat(),
                    top,
                    (paddingLeft + contentWidth).toFloat(),
                    bottom,
                    paint
                )
            } else if (i == fullBars && hasHalf) {
                paint.color = activeColor
                val halfTop = bottom - (barHeight / 2f)
                canvas.drawRect(
                    paddingLeft.toFloat(),
                    halfTop,
                    (paddingLeft + contentWidth).toFloat(),
                    bottom,
                    paint
                )
            }
        }
    }

    fun setProgress(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (next == progress) return
        progress = next
        invalidate()
    }

    fun setOnProgressChangedListener(listener: ((Float) -> Unit)?) {
        onProgressChanged = listener
    }

    fun setBarCount(value: Int) {
        val next = value.coerceAtLeast(1)
        if (next == barCount) return
        barCount = next
        invalidate()
    }

    fun getBarCount(): Int = barCount

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val contentHeight = height - paddingTop - paddingBottom
        if (contentHeight <= 0) return super.onTouchEvent(event)

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val y = event.y.coerceIn(paddingTop.toFloat(), (height - paddingBottom).toFloat())
                val ratio = 1f - ((y - paddingTop) / contentHeight.toFloat())
                val next = ratio.coerceIn(0f, 1f)
                if (next != progress) {
                    progress = next
                    invalidate()
                    onProgressChanged?.invoke(next)
                }
                return true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

}
