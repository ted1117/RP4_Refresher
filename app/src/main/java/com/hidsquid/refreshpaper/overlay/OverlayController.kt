package com.hidsquid.refreshpaper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.hidsquid.refreshpaper.R
import java.lang.reflect.Method

class OverlayController(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var uniqueDrawingId: Int? = null

    private val getUniqueDrawingIdMethod: Method? =
        runCatching { View::class.java.getMethod("getUniqueDrawingId").apply { isAccessible = true } }
            .recoverCatching { View::class.java.getDeclaredMethod("getUniqueDrawingId").apply { isAccessible = true } }
            .getOrNull()

    fun attachOverlay() {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching {
            windowManager?.addView(overlayView, params)
        }.onFailure { 
            Log.e(TAG, "Failed to add overlay view", it)
            return
        }

        overlayView?.visibility = View.VISIBLE
        overlayView?.alpha = 0f

        overlayView?.postOnAnimation {
            if (uniqueDrawingId == null) {
                uniqueDrawingId = readUniqueDrawingId(overlayView)
                Log.d(TAG, "uniqueDrawingId(init) = $uniqueDrawingId")
            }
        }
    }

    fun detachOverlay() {
        runCatching {
            overlayView?.let { windowManager?.removeView(it) }
        }
        overlayView = null
        windowManager = null
    }

    fun getUniqueDrawingId(): Int? = uniqueDrawingId

    fun getView(): View? = overlayView

    @Deprecated(
        message = "invalidate 기반 리프레시로 대체",
        level = DeprecationLevel.WARNING
    )
    fun poke() {
        val v = overlayView ?: return
        v.alpha = 0.01f
        v.invalidate()
        v.postOnAnimation {
            v.alpha = 0f
            v.invalidate()
        }
    }

    private fun readUniqueDrawingId(v: View?): Int? {
        val view = v ?: return null
        val m = getUniqueDrawingIdMethod ?: return null
        return runCatching {
            val n = m.invoke(view) as? Number ?: return@runCatching null
            val id = n.toLong().toInt()
            id.takeIf { it > 0 }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OverlayController"
    }
}
