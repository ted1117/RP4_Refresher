package com.hidsquid.refreshpaper

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Binder
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.lang.reflect.Method
import java.util.SortedMap
import java.util.TreeMap

class MainUtils {
    /**
     * 디스플레이 모드를 설정합니다.
     */
    fun setDisplayMode(paramInt: Int) {
        try {
            Log.d(TAG, "setDisplayMode 시작!")
            invokeMethod(setEPDDefaultModeMethod, 3)
            invokeMethod(setEPDDisplayModeMethod, paramInt)
            invokeMethod(setEPDModeMethod, -1)
        } catch (e: Exception) {
            Log.e(TAG, "리플렉션 오류: ", e)
        }
    }

    companion object {
        private const val TAG = "MainUtils"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"

        // [수정 1] val -> var로 변경 (값을 나중에 넣어야 하니까요)
        private var setEPDModeMethod: Method? = null
        private var setEPDFullRefreshMethod: Method? = null
        private var setEPDDefaultModeMethod: Method? = null
        private var setEPDDisplayModeMethod: Method? = null

        init {
            try {
                val surfaceControlClass = Class.forName("android.view.SurfaceControl")

                // 이제 빨간 줄이 사라질 겁니다 (var니까 재할당 가능)
                setEPDModeMethod =
                    surfaceControlClass.getMethod("setEPDMode", Int::class.javaPrimitiveType)
                setEPDFullRefreshMethod =
                    surfaceControlClass.getMethod("setEPDFullRefresh", Int::class.javaPrimitiveType)
                setEPDDefaultModeMethod =
                    surfaceControlClass.getMethod("setEPDDefaultMode", Int::class.javaPrimitiveType)
                setEPDDisplayModeMethod =
                    surfaceControlClass.getMethod("setEPDDisplayMode", Int::class.javaPrimitiveType)

                setAccessible(
                    setEPDModeMethod,
                    setEPDFullRefreshMethod,
                    setEPDDefaultModeMethod,
                    setEPDDisplayModeMethod
                )
            } catch (e: Exception) {
                Log.e(TAG, "초기화 오류: ", e)
            }
        }

        /**
         * [수정 2] 파라미터 타입을 Method? (Nullable)로 변경하고 안전하게 처리
         */
        private fun setAccessible(vararg methods: Method?) {
            for (method in methods) {
                // method가 null이 아닐 때만 실행 (?.)
                method?.isAccessible = true
            }
        }

        @JvmStatic
        fun refreshScreen(uniqueDrawingId: Int) {
            try {
                Log.d(TAG, "refreshScreen 시작!")
                invokeMethod(setEPDModeMethod, 2)
                invokeMethod(setEPDFullRefreshMethod, uniqueDrawingId)
                invokeMethod(setEPDModeMethod, -1)
            } catch (e: Exception) {
                Log.e(TAG, "리플렉션 오류: ", e)
            }
        }

        @Throws(Exception::class)
        private fun invokeMethod(method: Method?, param: Int) {
            // method가 null이 아니면 invoke 실행 (?.)
            method?.invoke(null, param)
        }

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

        @JvmStatic
        fun isPackageUsageStatsEnabled(context: Context): Boolean {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Binder.getCallingUid(),
                context.packageName
            )
            return (mode == AppOpsManager.MODE_ALLOWED)
        }

        @JvmStatic
        fun isBlockedAppInForeground(context: Context): Boolean {
            val foregroundApp: String? = getForegroundApp(context)
            return BLOCKED_APP_PACKAGE_NAME == foregroundApp
        }

        fun getForegroundApp(context: Context): String? {
            var currentApp: String? = null
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val appList =
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time)

            if (appList != null && !appList.isEmpty()) {
                val sortedMap: SortedMap<Long?, UsageStats?> = TreeMap()
                for (usageStats in appList) {
                    sortedMap[usageStats.lastTimeUsed] = usageStats
                }

                if (!sortedMap.isEmpty()) {
                    // [수정 3] !! 대신 안전 호출(?.) 사용
                    currentApp = sortedMap[sortedMap.lastKey()]?.packageName
                }
            }
            return currentApp
        }
    }
}