package com.hidsquid.refreshpaper.device

import android.content.Context
import android.provider.Settings
import android.util.Log

class DeviceSecurityController(private val context: Context) {

    private val contentResolver = context.contentResolver

    @Throws(SecurityException::class)
    fun setSecureBypass(enable: Boolean) {
        val value = if (enable) 1 else 0

        Settings.Global.putInt(contentResolver, SETTING_KEY, value)
        Log.d(TAG, "Screenshot setting changed successfully: $enable")
    }

    fun isSecureBypassEnabled(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, SETTING_KEY, 1) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read secure bypass setting", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeviceSecurity"
        private const val SETTING_KEY = "secure_bypass_on"
    }
}
