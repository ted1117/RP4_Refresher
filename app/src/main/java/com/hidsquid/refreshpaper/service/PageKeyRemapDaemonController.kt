package com.hidsquid.refreshpaper.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val foregroundPackageProvider: () -> String
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var daemonRunning = false

    @Volatile
    private var desiredDaemonRunning = false

    @Volatile
    private var desiredMode = REMAP_MODE_DISABLED

    @Volatile
    private var desiredPageUpLongPressEnabled = false

    @Volatile
    private var desiredPageDownLongPressEnabled = false

    @Volatile
    private var desiredLongPressTimeoutMs = ViewConfiguration.getLongPressTimeout()

    @Volatile
    private var lastWrittenState: String? = null

    @Volatile
    private var isClosed = false

    private val stateFile = resolveStateFile()
    private var debounceRunnable: Runnable? = null

    fun scheduleStateUpdate() {
        if (isClosed) return

        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (isClosed) return@Runnable
            desiredDaemonRunning = shouldKeepDaemonAlive()
            desiredMode = resolveCurrentMode()
            desiredPageUpLongPressEnabled = resolvePageLongPressEnabled(
                settingsRepository.getPageUpLongPressAction()
            )
            desiredPageDownLongPressEnabled = resolvePageLongPressEnabled(
                settingsRepository.getPageDownLongPressAction()
            )
            desiredLongPressTimeoutMs = ViewConfiguration.getLongPressTimeout()
            executor.execute {
                if (!isClosed) {
                    applyDaemonState()
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
        stopDaemonSync()
        executor.shutdownNow()
    }

    private fun shouldKeepDaemonAlive(): Boolean {
        val hasRemapTargets = settingsRepository.isPageKeyTapEnabled() && (
            settingsRepository.getPageKeyTapTargetPackages().isNotEmpty() ||
                settingsRepository.getPageKeySwapTargetPackages().isNotEmpty()
            )
        val hasPageLongPressAction =
            settingsRepository.getPageUpLongPressAction() != SettingsRepository.F1_ACTION_NONE ||
                settingsRepository.getPageDownLongPressAction() != SettingsRepository.F1_ACTION_NONE
        return hasRemapTargets || hasPageLongPressAction
    }

    private fun resolveCurrentMode(): Int {
        val packageName = foregroundPackageProvider()
        if (!shouldKeepDaemonAlive()) return REMAP_MODE_DISABLED
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

    private fun applyDaemonState() {
        val shouldRunDaemon = desiredDaemonRunning
        val remapMode = desiredMode
        val pageUpLongPressEnabled = desiredPageUpLongPressEnabled
        val pageDownLongPressEnabled = desiredPageDownLongPressEnabled
        val longPressTimeoutMs = desiredLongPressTimeoutMs

        if (!shouldRunDaemon) {
            writeStateFile(
                remapMode = REMAP_MODE_DISABLED,
                pageUpLongPressEnabled = false,
                pageDownLongPressEnabled = false,
                longPressTimeoutMs = longPressTimeoutMs
            )
            if (daemonRunning) {
                stopDaemonSync()
            }
            return
        }

        writeStateFile(
            remapMode = remapMode,
            pageUpLongPressEnabled = pageUpLongPressEnabled,
            pageDownLongPressEnabled = pageDownLongPressEnabled,
            longPressTimeoutMs = longPressTimeoutMs
        )

        if (!daemonRunning) {
            val started = startDaemon()
            daemonRunning = started
            if (!started) {
                Log.w(TAG, "Failed to start page key remap daemon")
                return
            }
        }

        writeStateFile(
            remapMode = remapMode,
            pageUpLongPressEnabled = pageUpLongPressEnabled,
            pageDownLongPressEnabled = pageDownLongPressEnabled,
            longPressTimeoutMs = longPressTimeoutMs
        )
    }

    private fun startDaemon(): Boolean {
        if (!ensureDaemonBinary()) {
            Log.w(TAG, "Remap daemon binary not found or not executable: $PAGE_KEY_REMAP_DAEMON_PATH")
            return false
        }

        val command = """
            PID_FILE="$PAGE_KEY_REMAP_PID_PATH"
            BIN_PATH="$PAGE_KEY_REMAP_DAEMON_PATH"
            STATE_PATH="${stateFile.absolutePath}"
            if [ -f "${'$'}PID_FILE" ]; then
                OLD_PID="${'$'}(cat "${'$'}PID_FILE" 2>/dev/null || true)"
                if [ -n "${'$'}OLD_PID" ] && kill -0 "${'$'}OLD_PID" 2>/dev/null; then
                    kill "${'$'}OLD_PID" 2>/dev/null || true
                    sleep 0.1
                fi
                rm -f "${'$'}PID_FILE"
            fi
            nohup "${'$'}BIN_PATH" --state-file "${'$'}STATE_PATH" >/dev/null 2>&1 &
            echo ${'$'}! > "${'$'}PID_FILE"
            sleep 0.1
            NEW_PID="${'$'}(cat "${'$'}PID_FILE" 2>/dev/null || true)"
            [ -n "${'$'}NEW_PID" ] && kill -0 "${'$'}NEW_PID" 2>/dev/null
        """.trimIndent()
        return runRootCommand(command, ROOT_CMD_TIMEOUT_MS)
    }

    private fun ensureDaemonBinary(): Boolean {
        val localBinary = File(context.filesDir, "page_key_remapd")
        val copiedFromAssets = runCatching {
            val assetPath = resolveDaemonAssetPath() ?: return@runCatching false
            context.assets.open(assetPath).use { input ->
                localBinary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            localBinary.setExecutable(true, false)
            true
        }.getOrDefault(false)

        if (!copiedFromAssets) return false

        val installCommand = """
            cp "${localBinary.absolutePath}" "$PAGE_KEY_REMAP_DAEMON_PATH"
            chmod 755 "$PAGE_KEY_REMAP_DAEMON_PATH"
            [ -x "$PAGE_KEY_REMAP_DAEMON_PATH" ]
        """.trimIndent()
        return runRootCommand(installCommand, ROOT_CMD_TIMEOUT_MS)
    }

    private fun resolveDaemonAssetPath(): String? {
        val candidatePaths = mutableListOf<String>()
        Build.SUPPORTED_ABIS?.forEach { abi ->
            candidatePaths.add("bin/$abi/page_key_remapd")
        }
        candidatePaths.add(ASSET_PAGE_KEY_REMAP_DAEMON)

        for (path in candidatePaths) {
            val exists = runCatching {
                context.assets.open(path).use { true }
            }.getOrDefault(false)
            if (exists) return path
        }

        Log.w(
            TAG,
            "No remap daemon asset found. expected one of: ${candidatePaths.joinToString()}"
        )
        return null
    }

    private fun stopDaemonSync() {
        val command = """
            PID_FILE="$PAGE_KEY_REMAP_PID_PATH"
            if [ -f "${'$'}PID_FILE" ]; then
                OLD_PID="${'$'}(cat "${'$'}PID_FILE" 2>/dev/null || true)"
                if [ -n "${'$'}OLD_PID" ] && kill -0 "${'$'}OLD_PID" 2>/dev/null; then
                    kill "${'$'}OLD_PID" 2>/dev/null || true
                    sleep 0.1
                fi
                rm -f "${'$'}PID_FILE"
            fi
            pkill -f "$PAGE_KEY_REMAP_DAEMON_PATH" 2>/dev/null || true
            exit 0
        """.trimIndent()
        runRootCommand(command, ROOT_CMD_TIMEOUT_MS)
        daemonRunning = false
    }

    private fun resolveStateFile(): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(baseDir, PAGE_KEY_REMAP_STATE_FILE_NAME)
        file.parentFile?.mkdirs()
        return file
    }

    private fun writeStateFile(
        remapMode: Int,
        pageUpLongPressEnabled: Boolean,
        pageDownLongPressEnabled: Boolean,
        longPressTimeoutMs: Int
    ): Boolean {
        val stateText = buildString {
            append(remapMode)
            append(' ')
            append(if (pageUpLongPressEnabled) 1 else 0)
            append(' ')
            append(if (pageDownLongPressEnabled) 1 else 0)
            append(' ')
            append(longPressTimeoutMs)
            append('\n')
        }

        if (stateFile.exists() && lastWrittenState == stateText) {
            return true
        }

        return runCatching {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(stateText)
            lastWrittenState = stateText
            true
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to write remap state file", throwable)
            false
        }
    }

    private fun runRootCommand(command: String, timeoutMs: Long): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false
            }
            process.exitValue() == 0
        }.getOrElse { throwable ->
            Log.w(TAG, "runRootCommand failed", throwable)
            false
        }
    }

    private companion object {
        private const val TAG = "PageKeyRemapDaemon"
        private const val REMAP_DAEMON_DEBOUNCE_MS = 200L
        private const val ROOT_CMD_TIMEOUT_MS = 3_000L
        private const val PAGE_KEY_REMAP_DAEMON_PATH = "/data/local/tmp/page_key_remapd"
        private const val PAGE_KEY_REMAP_PID_PATH = "/data/local/tmp/page_key_remapd.pid"
        private const val PAGE_KEY_REMAP_STATE_FILE_NAME = "page_key_remap_state"
        private const val REMAP_MODE_DISABLED = 0
        private const val REMAP_MODE_NORMAL = 1
        private const val REMAP_MODE_SWAPPED = 2
        private const val REMAP_MODE_SWAP_ONLY = 3
        private const val ASSET_PAGE_KEY_REMAP_DAEMON = "bin/page_key_remapd"
    }
}
