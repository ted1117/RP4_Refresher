package com.hidsquid.refreshpaper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Binder;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public final class MainUtils {
    private static final String TAG = "MainUtils";
    private static final String BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper";

    private static Method setEPDModeMethod;
    private static Method setEPDFullRefreshMethod;
    private static Method setEPDDefaultModeMethod;
    private static Method setEPDDisplayModeMethod;

    static {
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            setEPDModeMethod = surfaceControlClass.getMethod("setEPDMode", int.class);
            setEPDFullRefreshMethod = surfaceControlClass.getMethod("setEPDFullRefresh", int.class);
            setEPDDefaultModeMethod = surfaceControlClass.getMethod("setEPDDefaultMode", int.class);
            setEPDDisplayModeMethod = surfaceControlClass.getMethod("setEPDDisplayMode", int.class);

            setAccessible(setEPDModeMethod, setEPDFullRefreshMethod, setEPDDefaultModeMethod, setEPDDisplayModeMethod);
        } catch (Exception e) {
            Log.e(TAG, "초기화 오류: ", e);
        }
    }

    /**
     * 주어진 메소드들을 접근 가능하도록 설정합니다.
     *
     * @param methods 접근 가능하도록 설정할 메소드들
     */
    private static void setAccessible(Method... methods) {
        for (Method method : methods) {
            method.setAccessible(true);
        }
    }

    /**
     * 화면을 새로고침 합니다.
     *
     * @param uniqueDrawingId 새로고침 할 액티비티의 루트 뷰 uniqueDrawingId
     */
    public static void refreshScreen(int uniqueDrawingId) {
        try {
            Log.d(TAG, "refreshScreen 시작!");
            invokeMethod(setEPDModeMethod, 2);
            invokeMethod(setEPDFullRefreshMethod, uniqueDrawingId);
            invokeMethod(setEPDModeMethod, -1);
        } catch (Exception e) {
            Log.e(TAG, "리플렉션 오류: ", e);
        }
    }

    /**
     * 디스플레이 모드를 설정합니다.
     *
     * @param paramInt 설정할 디스플레이 모드
     */
    public void setDisplayMode(int paramInt) {
        try {
            Log.d(TAG, "setDisplayMode 시작!");
            invokeMethod(setEPDDefaultModeMethod, 3);
            invokeMethod(setEPDDisplayModeMethod, paramInt);
            invokeMethod(setEPDModeMethod, -1);
        } catch (Exception e) {
            Log.e(TAG, "리플렉션 오류: ", e);
        }
    }

    /**
     * 주어진 메소드를 호출합니다.
     *
     * @param method 호출할 메소드
     * @param param 메소드에 전달할 매개변수
     * @throws Exception 메소드 호출 중 발생한 예외
     */
    private static void invokeMethod(Method method, int param) throws Exception {
        if (method != null) {
            method.invoke(null, param);
        }
    }

    /**
     * 접근성 서비스가 활성화되어 있는지 확인합니다.
     *
     * @param context 확인할 컨텍스트
     * @return 접근성 서비스가 활성화되어 있으면 true, 그렇지 않으면 false
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo enabledService : enabledServices) {
            if (enabledService.getResolveInfo().serviceInfo.packageName.equals(context.getPackageName()) &&
                    enabledService.getResolveInfo().serviceInfo.name.equals(KeyInputDetectingService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 패키지 사용 통계가 활성화되어 있는지 확인합니다.
     *
     * @param context 확인할 컨텍스트
     * @return 패키지 사용 통계가 활성화되어 있으면 true, 그렇지 않으면 false
     */
    public static boolean isPackageUsageStatsEnabled(Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Binder.getCallingUid(), context.getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    /**
     * 차단된 앱이 foreground에 있는지 확인합니다.
     *
     * @param context 확인할 컨텍스트
     * @return 차단된 앱이 foreground에 있으면 true, 그렇지 않으면 false
     */
    public static boolean isBlockedAppInForeground(Context context) {
        String foregroundApp = getForegroundApp(context);
        return BLOCKED_APP_PACKAGE_NAME.equals(foregroundApp);
    }

    /**
     * 현재 foreground 앱의 패키지 이름을 가져옵니다.
     *
     * @param context 확인할 컨텍스트
     * @return foreground 앱의 패키지 이름
     */
    public static String getForegroundApp(Context context) {
        String currentApp = null;
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time);

        if (appList != null && !appList.isEmpty()) {
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : appList) {
                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }

            if (!sortedMap.isEmpty()) {
                currentApp = sortedMap.get(sortedMap.lastKey()).getPackageName();
            }
        }

        return currentApp;
    }
}