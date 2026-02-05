package com.hidsquid.refreshpaper.epd

import android.util.Log
import android.view.View
import java.lang.reflect.Method

object EPDRefreshController {
    private const val TAG = "EPDRefreshController"

    private var setEPDModeMethod: Method? = null
    private var setEPDFullRefreshMethod: Method? = null

    init {
        try {
            val surfaceControlClass = Class.forName("android.view.SurfaceControl")

            setEPDModeMethod =
                surfaceControlClass.getMethod("setEPDMode", Int::class.javaPrimitiveType)
            setEPDFullRefreshMethod =
                surfaceControlClass.getMethod("setEPDFullRefresh", Int::class.javaPrimitiveType)

            setAccessible(
                setEPDModeMethod,
                setEPDFullRefreshMethod,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ", e)
        }
    }

    fun refresh(targetView: View, uniqueDrawingId: Int) {
        try {
            invokeMethod(setEPDModeMethod, 2)
            invokeMethod(setEPDFullRefreshMethod, uniqueDrawingId)
            targetView.postInvalidate()
            targetView.postOnAnimation {
                invokeMethod(setEPDModeMethod, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reflection error: ", e)
        }
    }

    @Throws(Exception::class)
    private fun invokeMethod(method: Method?, param: Int) {
        method?.invoke(null, param)
    }

    private fun setAccessible(vararg methods: Method?) {
        for (method in methods) {
            method?.isAccessible = true
        }
    }
}
