package com.hidsquid.refreshpaper.utils

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Opens the LSPosed manager. Its shell activity is not exported, so when no standalone manager
 * app exists we ask the Magisk module (root) to launch it through a request file.
 */
object LsposedLauncher {
    private const val MANAGER_PACKAGE = "org.lsposed.manager"
    private const val REQUEST_FILE = "launch_lsposed"
    private const val REQUEST_TMP_FILE = "launch_lsposed.tmp"
    private const val REQUEST_TIMEOUT_MS = 3_000L
    private const val POLL_INTERVAL_MS = 100L

    /** Blocking; call off the main thread. */
    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent) }.isSuccess
        }
        return requestLaunchFromModule(context)
    }

    private fun requestLaunchFromModule(context: Context): Boolean {
        return runCatching {
            val dir = context.createDeviceProtectedStorageContext().filesDir
            dir.mkdirs()
            val request = File(dir, REQUEST_FILE)
            val tmp = File(dir, REQUEST_TMP_FILE)
            val content = System.currentTimeMillis().toString()

            tmp.writeText(content)
            if (!tmp.renameTo(request)) {
                request.writeText(content)
                tmp.delete()
            }

            val deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!request.exists()) return@runCatching true
                Thread.sleep(POLL_INTERVAL_MS)
            }
            if (!request.exists()) return@runCatching true
            request.delete()
            false
        }.getOrDefault(false)
    }
}
