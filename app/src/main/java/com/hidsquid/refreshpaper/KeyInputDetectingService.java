package com.hidsquid.refreshpaper;


import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.lang.reflect.Method;

public class KeyInputDetectingService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";
    private int pageUpDownCount = 0;
    private static final int TRIGGER_COUNT = 5;
    private static final int LONG_PRESS_THRESHOLD = 300;

    private long keyDownTime;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
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
                    refreshScreen(1);
                    pageUpDownCount = 0; // reset count after triggering
                }
                else {
                    Toast.makeText(this, String.valueOf(pageUpDownCount), Toast.LENGTH_SHORT).show();
                }
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
            setEPDFullRefresh.invoke(null, 12345);
            setEPDMode.invoke(null, -1);

        } catch (Exception e) {
            Log.e(TAG, "Reflection error: ", e);
        }
    }
}
