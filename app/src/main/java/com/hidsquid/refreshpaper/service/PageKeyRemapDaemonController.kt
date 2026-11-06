package com.hidsquid.refreshpaper.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.ViewConfiguration
import com.hidsquid.refreshpaper.SettingsRepository
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PageKeyRemapDaemonController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val blockedPackageName: String,
    private val foregroundPackageProvider: () -> String,
    private val touchForegroundPackageProvider: () -> String
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var desiredNeeded = false

    @Volatile
    private var desiredMode = REMAP_MODE_DISABLED

    @Volatile
    private var desiredPageUpLongPressEnabled = false

    @Volatile
    private var desiredPageDownLongPressEnabled = false

    @Volatile
    private var desiredTouchTrigger = 0

    @Volatile
    private var desiredLongPressTimeoutMs = ViewConfiguration.getLongPressTimeout()

    @Volatile
    private var lastWrittenState: String? = null

    @Volatile
    private var isClosed = false

    private val stateDir = File(
        context.createDeviceProtectedStorageContext().filesDir,
        PAGE_KEY_REMAP_STATE_DIR_NAME
    )
    private var debounceRunnable: Runnable? = null

    fun scheduleStateUpdate() {
        if (isClosed) return

        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (isClosed) return@Runnable
            desiredNeeded = RemapDaemonStatus.isNeeded(settingsRepository)
            desiredMode = resolveCurrentMode()
            desiredPageUpLongPressEnabled = resolvePageLongPressEnabled(
                settingsRepository.getPageUpLongPressAction()
            )
            desiredPageDownLongPressEnabled = resolvePageLongPressEnabled(
                settingsRepository.getPageDownLongPressAction()
            )
            desiredTouchTrigger = resolveTouchTrigger()
            desiredLongPressTimeoutMs = ViewConfiguration.getLongPressTimeout()
            executor.execute {
                if (!isClosed) {
                    applyState()
                }
            }
        }
        debounceRunnable = runnable
        mainHandler.postDelayed(runnable, REMAP_DAEMON_DEBOUNCE_MS)
    }

    fun shutdown() {
        isClosed = true
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null
        executor.execute {
            writeStateFile(REMAP_MODE_DISABLED, false, false, ViewConfiguration.getLongPressTimeout(), 0)
        }
        executor.shutdown()
        runCatching { executor.awaitTermination(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS) }
    }

    private fun resolveCurrentMode(): Int {
        val packageName = foregroundPackageProvider()
        if (!RemapDaemonStatus.isNeeded(settingsRepository)) return REMAP_MODE_DISABLED
        if (!settingsRepository.isPageKeyTapEnabled()) return REMAP_MODE_DISABLED
        if (packageName.isBlank()) return REMAP_MODE_DISABLED
        if (packageName == blockedPackageName) return REMAP_MODE_DISABLED

        val isVolumeRemap = settingsRepository.isPageKeyTapTargetPackage(packageName)
        val isSwap = settingsRepository.isPageKeySwapTargetPackage(packageName)

        return when {
            isVolumeRemap && isSwap -> REMAP_MODE_SWAPPED
            isVolumeRemap -> REMAP_MODE_NORMAL
            isSwap -> REMAP_MODE_SWAP_ONLY
            else -> REMAP_MODE_DISABLED
        }
    }

    private fun resolvePageLongPressEnabled(selectedAction: Int): Boolean {
        if (selectedAction == SettingsRepository.F1_ACTION_NONE) return false

        val packageName = foregroundPackageProvider()
        if (packageName.isBlank()) return false
        if (packageName == blockedPackageName) return false
        return true
    }

    private fun resolveTouchTrigger(): Int {
        if (!settingsRepository.isAutoRefreshEnabled()) return 0
        val packageName = touchForegroundPackageProvider()
        if (packageName.isBlank()) return 0
        if (packageName == blockedPackageName) return 0
        if (!settingsRepository.isDaemonTouchRefreshTargetPackage(packageName)) return 0
        return settingsRepository.getPagesPerRefresh().coerceAtLeast(1)
    }

    private fun applyState() {
        if (!desiredNeeded) {
            writeStateFile(
                remapMode = REMAP_MODE_DISABLED,
                pageUpLongPressEnabled = false,
                pageDownLongPressEnabled = false,
                longPressTimeoutMs = desiredLongPressTimeoutMs,
                touchTrigger = 0
            )
            return
        }

        writeStateFile(
            remapMode = desiredMode,
            pageUpLongPressEnabled = desiredPageUpLongPressEnabled,
            pageDownLongPressEnabled = desiredPageDownLongPressEnabled,
            longPressTimeoutMs = desiredLongPressTimeoutMs,
            touchTrigger = desiredTouchTrigger
        )
    }

    private fun writeStateFile(
        remapMode: Int,
        pageUpLongPressEnabled: Boolean,
        pageDownLongPressEnabled: Boolean,
        longPressTimeoutMs: Int,
        touchTrigger: Int
    ): Boolean {
        val stateText = buildString {
            append(STATE_PROTOCOL_VERSION)
            append(' ')
            append(Process.myPid())
            append(' ')
            append(remapMode)
            append(' ')
            append(if (pageUpLongPressEnabled) 1 else 0)
            append(' ')
            append(if (pageDownLongPressEnabled) 1 else 0)
            append(' ')
            append(longPressTimeoutMs)
            append(' ')
            append(touchTrigger)
            append('\n')
        }

        val stateFile = File(stateDir, PAGE_KEY_REMAP_STATE_FILE_NAME)
        if (stateFile.exists() && lastWrittenState == stateText) {
            return true
        }

        return runCatching {
            stateDir.mkdirs()
            val tmpFile = File(stateDir, PAGE_KEY_REMAP_STATE_TMP_FILE_NAME)
            tmpFile.writeText(stateText)
            if (!tmpFile.renameTo(stateFile)) {
                Log.w(TAG, "Failed to rename remap state file, writing directly")
                stateFile.writeText(stateText)
                tmpFile.delete()
            }
            lastWrittenState = stateText
            true
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to write remap state file", throwable)
            false
        }
    }

    private companion object {
        private const val TAG = "PageKeyRemapDaemon"
        private const val REMAP_DAEMON_DEBOUNCE_MS = 200L
        private const val SHUTDOWN_WAIT_MS = 500L
        private const val STATE_PROTOCOL_VERSION = 3
        private const val PAGE_KEY_REMAP_STATE_DIR_NAME = "page_key_remap"
        private const val PAGE_KEY_REMAP_STATE_FILE_NAME = "state"
        private const val PAGE_KEY_REMAP_STATE_TMP_FILE_NAME = "state.tmp"
        private const val REMAP_MODE_DISABLED = 0
        private const val REMAP_MODE_NORMAL = 1
        private const val REMAP_MODE_SWAPPED = 2
        private const val REMAP_MODE_SWAP_ONLY = 3
    }
}
