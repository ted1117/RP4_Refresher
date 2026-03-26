package com.hidsquid.refreshpaper.device

import android.content.Context
import android.util.Log
import com.hidsquid.refreshpaper.ModulePrefs

class DeviceSecurityController(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    @Throws(SecurityException::class)
    fun setSecureBypass(enable: Boolean) {
        prefs.edit().putBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, enable).commit()
        Log.d(TAG, "Screenshot setting changed successfully: $enable")
    }

    fun isSecureBypassEnabled(): Boolean {
        return try {
            prefs.getBoolean(ModulePrefs.KEY_SECURE_BYPASS_ENABLED, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read secure bypass setting", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeviceSecurity"
    }
}
