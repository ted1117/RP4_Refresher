package com.hidsquid.refreshpaper;

import static com.hidsquid.refreshpaper.MainUtils.isBlockedAppInForeground;
import static com.hidsquid.refreshpaper.MainUtils.refreshScreen;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 특정 조건에 따라 키 입력을 감지하고 화면을 새로고침하는 서비스입니다.
 */
public class KeyInputDetectingService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";
    private int pageUpDownCount = 0;
    private int TRIGGER_COUNT = 5;
    private final int uniqueDrawingId = 1;
    private static final int LONG_PRESS_THRESHOLD = 300;
    public static final String EXTRA_NUMBER = "EXTRA_NUMBER";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_PAGES_PER_REFRESH = "numberInput";
    private static final String PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled";
    private static final String PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled";
    public static final String ACTION_REFRESH_SCREEN = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN";

    private WindowManager windowManager;
    private View overlayView;
    private Handler handler;
    private ScreenRefreshBroadcastReceiver screenRefreshBroadcastReceiver;


    @Override
    public void onCreate() {
        super.onCreate();
        screenRefreshBroadcastReceiver = new ScreenRefreshBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_SCREEN);
        registerReceiver(screenRefreshBroadcastReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        TRIGGER_COUNT = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
        Log.d(TAG, "onServiceConnected - TRIGGER_COUNT: " + TRIGGER_COUNT);

        // 수동 리프레시를 위해 투명 오버레이 생성
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);

        overlayView = inflater.inflate(R.layout.overlay_layout, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(overlayView, params);
        overlayView.setVisibility(View.GONE);

        handler = new Handler(Looper.getMainLooper());
    }

    /**
     * 자동으로 새로고침할 단위 페이지 수를 TRIGGER_COUNT에 할당합니다.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            TRIGGER_COUNT = intent.getIntExtra(EXTRA_NUMBER, 5);
            Log.d(TAG, "Received number: " + TRIGGER_COUNT);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(PREF_KEY_PAGES_PER_REFRESH, TRIGGER_COUNT);
            editor.apply();
        } else {
            TRIGGER_COUNT = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
            Log.d(TAG, "SharedPreference: " + TRIGGER_COUNT);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            return handleKeyDown(event);
        }

        return super.onKeyEvent(event);
    }

    /**
     * 키 코드에 따라 자동·수동으로 화면을 새로고침 합니다.
     *
     * @param event 키 이벤트
     * @return 이벤트가 처리되면 true, 그렇지 않으면 false
     */
    private boolean handleKeyDown(KeyEvent event) {
        int keyCode = event.getKeyCode();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isAutoRefreshEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false);
        boolean isManualRefreshEnabled = sharedPreferences.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false);

        if (!isBlockedAppInForeground(this) && isAutoRefreshEnabled) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    pageUpDownCount++;
                    if (pageUpDownCount >= TRIGGER_COUNT) {
                        refreshScreen(uniqueDrawingId);
                        pageUpDownCount = 0;
                    }
                    return false;
            }
        }

        if (isManualRefreshEnabled) {
            if (keyCode == KeyEvent.KEYCODE_F4) {
                refreshScreen(uniqueDrawingId);
                showOverlay();
                return true;
            }
        }
        return super.onKeyEvent(event);
    }

    /**
     * 일시로 오버레이를 표시합니다.
     */
    public void showOverlay() {
        overlayView.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> overlayView.setVisibility(View.GONE), 100);
    }

    /**
     * 수동 새로고침 인텐트를 수신합니다.
     */
    public class ScreenRefreshBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_REFRESH_SCREEN.equals(intent.getAction())) {
                Log.d(TAG, "Screen refresh broadcast received");
                refreshScreen(uniqueDrawingId);
                showOverlay();
            }
        }
    }


}