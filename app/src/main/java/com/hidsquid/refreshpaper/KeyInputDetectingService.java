package com.hidsquid.refreshpaper;


import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class KeyInputDetectingService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";
    private static final String BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper";
    private int pageUpDownCount = 0;
    private int TRIGGER_COUNT = 5;
    private static final int LONG_PRESS_THRESHOLD = 300;

    private long keyDownTime;
    public static final String EXTRA_NUMBER = "EXTRA_NUMBER";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_PAGES_PER_REFRESH = "numberInput";
    private MainUtils mainUtils;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        mainUtils = new MainUtils();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        TRIGGER_COUNT = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
        Log.d(TAG, "onServiceConnected - TRIGGER_COUNT: " + TRIGGER_COUNT);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            TRIGGER_COUNT = intent.getIntExtra(EXTRA_NUMBER, 5);
            Log.d(TAG, "Received number: " + TRIGGER_COUNT);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(PREF_KEY_PAGES_PER_REFRESH, TRIGGER_COUNT);
            editor.apply();
        }
        else {
            TRIGGER_COUNT = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
            Log.d(TAG, "SharedPreference: " + TRIGGER_COUNT);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isBlockedAppInForeground()) {
            Log.d(TAG, "Blocked app is in foreground, ignoring key event.");
            return false;
        }

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                keyDownTime = System.currentTimeMillis();
            }
        }

        if (action == KeyEvent.ACTION_UP) {
            long keyPressDuration = System.currentTimeMillis() - keyDownTime;

            if (keyPressDuration >= LONG_PRESS_THRESHOLD) {
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                pageUpDownCount++;

                Log.d(TAG, "PageUp/PageDown pressed. Count: " + pageUpDownCount);

                if (pageUpDownCount >= TRIGGER_COUNT) {
                    mainUtils.refreshScreen(1);
                    pageUpDownCount = 0;
                }
//                else {
//                    Toast.makeText(this, String.valueOf(pageUpDownCount), Toast.LENGTH_SHORT).show();
//                }
            }

        }
        return super.onKeyEvent(event);
    }

    private void refreshScreen(int uniqueDrawingId) {
        try {
            Log.d(TAG, "refreshScreen Starts!");
            // SurfaceControl 클래스 로드
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");

            Method setEPDMode = surfaceControlClass.getMethod("setEPDMode", int.class);
            Method setEPDFullRefresh = surfaceControlClass.getMethod("setEPDFullRefresh", int.class);

            setEPDMode.setAccessible(true);
            setEPDFullRefresh.setAccessible(true);

            setEPDMode.invoke(null, 2);
            setEPDFullRefresh.invoke(null, uniqueDrawingId);
            setEPDMode.invoke(null, -1);

        } catch (Exception e) {
            Log.e(TAG, "Reflection error: ", e);
        }
    }

    private boolean isBlockedAppInForeground() {
        String foregroundApp = getForegroundApp();
        return BLOCKED_APP_PACKAGE_NAME.equals(foregroundApp);
    }

    private String getForegroundApp() {
        String currentApp = null;
        UsageStatsManager usm = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
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
