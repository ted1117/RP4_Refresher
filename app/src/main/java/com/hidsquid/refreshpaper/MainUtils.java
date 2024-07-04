package com.hidsquid.refreshpaper;

import android.util.Log;
import java.lang.reflect.Method;

public class MainUtils {
    private static final String TAG = "MainUtils";

    private Method setEPDModeMethod;
    private Method setEPDFullRefreshMethod;
    private Method setEPDDefaultModeMethod;
    private Method setEPDDisplayModeMethod;

    public MainUtils() {
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            setEPDModeMethod = surfaceControlClass.getMethod("setEPDMode", int.class);
            setEPDFullRefreshMethod = surfaceControlClass.getMethod("setEPDFullRefresh", int.class);
            setEPDDefaultModeMethod = surfaceControlClass.getMethod("setEPDDefaultMode", int.class);
            setEPDDisplayModeMethod = surfaceControlClass.getMethod("setEPDDisplayMode", int.class);

            setAccessible(setEPDModeMethod, setEPDFullRefreshMethod, setEPDDefaultModeMethod, setEPDDisplayModeMethod);
        } catch (Exception e) {
            Log.e(TAG, "Initialization error: ", e);
        }
    }

    private void setAccessible(Method... methods) {
        for (Method method : methods) {
            method.setAccessible(true);
        }
    }

    public void refreshScreen(int uniqueDrawingId) {
        try {
            Log.d(TAG, "refreshScreen starts!");
            invokeMethod(setEPDModeMethod, 2);
            invokeMethod(setEPDFullRefreshMethod, uniqueDrawingId);
            invokeMethod(setEPDModeMethod, -1);
        } catch (Exception e) {
            Log.e(TAG, "Reflection error: ", e);
        }
    }

    public void setDisplayMode(int paramInt) {
        try {
            Log.d(TAG, "setDisplayMode starts!");
            invokeMethod(setEPDDefaultModeMethod, 3);
            invokeMethod(setEPDDisplayModeMethod, paramInt);
            invokeMethod(setEPDModeMethod, -1);
        } catch (Exception e) {
            Log.e(TAG, "Reflection error: ", e);
        }
    }

    private void invokeMethod(Method method, int param) throws Exception {
        if (method != null) {
            method.invoke(null, param);
        }
    }
}
