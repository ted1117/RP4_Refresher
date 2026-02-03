package com.hidsquid.refreshpaper.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.hidsquid.refreshpaper.service.KeyInputDetectingService

object AccessibilityUtils {
    @JvmStatic
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            if (enabledService.resolveInfo.serviceInfo.packageName == context.packageName &&
                enabledService.resolveInfo.serviceInfo.name == KeyInputDetectingService::class.java.name
            ) {
                return true
            }
        }
        return false
    }
}
