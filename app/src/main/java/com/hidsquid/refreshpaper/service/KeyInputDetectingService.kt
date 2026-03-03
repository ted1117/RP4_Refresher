package com.hidsquid.refreshpaper.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.IntentCompat
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BACK
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_BRIGHTNESS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_HOME_LAUNCHER
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_MANUAL_REFRESH
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_NONE
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_QUICK_SETTINGS
import com.hidsquid.refreshpaper.SettingsRepository.Companion.F1_ACTION_SCREENSHOT
import com.hidsquid.refreshpaper.SettingsRepository
import com.hidsquid.refreshpaper.StatusBarSettingsActivity
import com.hidsquid.refreshpaper.brightness.BrightnessActivity
import com.hidsquid.refreshpaper.epd.EPDRefreshController
import com.hidsquid.refreshpaper.overlay.OverlayController
import java.io.File
import kotlin.math.abs
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("AccessibilityPolicy")
class KeyInputDetectingService : AccessibilityService() {

    private var pageUpDownCount = 0
    private var triggerCount = 5

    private var currentPackageName: String = ""
    private var currentClassName: String = ""

    private var serviceBroadcastReceiver: ServiceBroadcastReceiver? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var overlayController: OverlayController
    private val daemonMainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val daemonExecutor = Executors.newSingleThreadExecutor()

    // 스크린샷 상태 추적 (개별 키의 DOWN 이벤트 소비 여부 기록)
    private var f1KeyTime = 0L
    private var pageDownKeyTime = 0L
    private var consumedF1Down = false
    private var consumedPageDownDown = false
    private var skipNextF1Action = false
    private var lastHandledF1ActionTime = 0L
    @Volatile
    private var remapDaemonRunning = false
    @Volatile
    private var desiredRemapDaemonRunning = false
    @Volatile
    private var desiredRemapEnabled = false
    @Volatile
    private var lastWrittenRemapEnabled: Boolean? = null
    private lateinit var remapStateFile: File
    private var remapStateDebounceRunnable: Runnable? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        overlayController = OverlayController(this)
        remapStateFile = resolveRemapStateFile()

        serviceBroadcastReceiver = ServiceBroadcastReceiver()
        registerReceiver(
            serviceBroadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_REFRESH_SCREEN)
                addAction(ACTION_SHOW_BRIGHTNESS_ACTIVITY)
                addAction(ACTION_RP400_GLOBAL_BUTTON)
                addAction(ACTION_PAGE_KEY_REMAP_STATE_CHANGED)
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        triggerCount = settingsRepository.getPagesPerRefresh()
        overlayController.attachOverlay()
        scheduleRemapDaemonStateUpdate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackageName = event.packageName?.toString().orEmpty()
            currentClassName = event.className?.toString().orEmpty()
            scheduleRemapDaemonStateUpdate()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun doFullRefresh() {
        val v = overlayController.getView() ?: run {
            Log.w(TAG, "doFullRefresh: overlayView is null")
            return
        }

        val id = overlayController.getUniqueDrawingId() ?: run {
            Log.w(TAG, "doFullRefresh: uniqueDrawingId is null")
            return
        }

        EPDRefreshController.refresh(v, id)
    }

    private fun launchConfiguredHomeLauncher(): Boolean {
        val configuredComponent = settingsRepository.getHomeLauncherComponent()
        val defaultComponent =
            ComponentName.unflattenFromString(SettingsRepository.DEFAULT_HOME_LAUNCHER_COMPONENT)
                ?: return false
        val targetComponent = ComponentName.unflattenFromString(configuredComponent) ?: defaultComponent

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            component = targetComponent
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch configured home: $configuredComponent", e)
            if (targetComponent == defaultComponent) return false

            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        component = defaultComponent
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                true
            }.getOrElse { fallbackError ->
                Log.e(TAG, "Failed to launch default home launcher", fallbackError)
                false
            }
        }
    }

    private fun handleScreenshotChord(event: KeyEvent): Boolean {
        if (!settingsRepository.isScreenshotChordEnabled()) {
            return false
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_F1 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        f1KeyTime = event.eventTime
                        if (pageDownKeyTime > 0L && abs(f1KeyTime - pageDownKeyTime) <= SCREENSHOT_CHORD_DELAY_MS) {
                            consumedF1Down = true
                            skipNextF1Action = true
                            triggerScreenshot()
                            return true
                        }
                        consumedF1Down = false
                        return false
                    }
                    KeyEvent.ACTION_UP -> {
                        f1KeyTime = 0L
                        val consume = consumedF1Down
                        consumedF1Down = false
                        return consume
                    }
                }
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        pageDownKeyTime = event.eventTime
                        if (f1KeyTime > 0L && abs(pageDownKeyTime - f1KeyTime) <= SCREENSHOT_CHORD_DELAY_MS) {
                            consumedPageDownDown = true
                            skipNextF1Action = true
                            triggerScreenshot()
                            return true
                        }
                        consumedPageDownDown = false
                        return false
                    }
                    KeyEvent.ACTION_UP -> {
                        pageDownKeyTime = 0L
                        val consume = consumedPageDownDown
                        consumedPageDownDown = false
                        return consume
                    }
                }
            }
        }

        return false
    }

    private fun triggerScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun countAutoRefreshIfNeeded(): Boolean {
        if (!settingsRepository.isAutoRefreshEnabled()) return false
        if (isBlockedForegroundApp()) return false

        pageUpDownCount++
        if (pageUpDownCount >= triggerCount) {
            doFullRefresh()
            pageUpDownCount = 0
        }
        return true
    }

    private fun isAutoRefreshTriggerKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN -> true
            else -> false
        }
    }

    private fun runConfiguredF1Action(selectedAction: Int): Boolean {
        return when (selectedAction) {
            F1_ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            F1_ACTION_SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            F1_ACTION_QUICK_SETTINGS -> launchQuickSettingsDialog()
            F1_ACTION_BRIGHTNESS -> launchBrightnessDialog()
            F1_ACTION_MANUAL_REFRESH -> triggerManualRefresh()
            F1_ACTION_HOME_LAUNCHER -> launchConfiguredHomeLauncher()
            F1_ACTION_NONE -> true
            else -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun handleF1ShortPress(): Boolean {
        val now = SystemClock.uptimeMillis()

        if (skipNextF1Action) {
            skipNextF1Action = false
            return true
        }

        if (isBlockedForegroundApp()) {
            return false
        }

        if (now - lastHandledF1ActionTime <= F1_ACTION_DEBOUNCE_MS) {
            return true
        }
        lastHandledF1ActionTime = now

        val selectedAction = settingsRepository.getF1Action()
        return runConfiguredF1Action(selectedAction)
    }

    private fun handleF1LongPress(): Boolean {
        if (isBlockedForegroundApp()) {
            return false
        }
        val selectedAction = settingsRepository.getF1LongPressAction(settingsRepository.getF1Action())
        return runConfiguredF1Action(selectedAction)
    }

    private fun triggerManualRefresh(): Boolean {
        doFullRefresh()
        return true
    }

    private fun launchQuickSettingsDialog(): Boolean {
        return runCatching {
            startActivity(
                Intent(this, StatusBarSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            true
        }.getOrElse {
            Log.e(TAG, "Failed to launch quick settings dialog", it)
            false
        }
    }

    private fun launchBrightnessDialog(): Boolean {
        return runCatching {
            BrightnessActivity.start(applicationContext)
            true
        }.getOrElse {
            Log.e(TAG, "Failed to launch brightness dialog", it)
            false
        }
    }

    private fun isBlockedForegroundApp(): Boolean {
        return currentPackageName.isBlank() || currentPackageName == BLOCKED_APP_PACKAGE_NAME
    }

    private fun handleRp400GlobalButton(intent: Intent) {
        val keyEvent = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_KEY_EVENT,
            KeyEvent::class.java
        ) ?: return

        if (keyEvent.keyCode != KeyEvent.KEYCODE_F1) return

        val isLongPress = keyEvent.action == KeyEvent.ACTION_MULTIPLE ||
            keyEvent.repeatCount > 0 ||
            (keyEvent.flags and KeyEvent.FLAG_LONG_PRESS) != 0 ||
            (keyEvent.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0

        Log.d(
            TAG,
            "[RP400] action=${keyEvent.action} repeat=${keyEvent.repeatCount} " +
                "flags=0x${keyEvent.flags.toString(16)} long=$isLongPress"
        )

        if (isLongPress) {
            handleF1LongPress()
            return
        }

        if (keyEvent.action == KeyEvent.ACTION_UP) {
            handleF1ShortPress()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // 스크린샷
        if (handleScreenshotChord(event)) return true

        val keyCode = event.keyCode

        if (event.action != KeyEvent.ACTION_UP) return super.onKeyEvent(event)

        // HOME 키
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return if (launchConfiguredHomeLauncher()) true else super.onKeyEvent(event)
        }

        // 자동 리프레시
        val isManualRefreshEnabled = settingsRepository.isManualRefreshEnabled()
        if (isAutoRefreshTriggerKey(keyCode) && countAutoRefreshIfNeeded()) {
            return false
        }

        // 수동 리프레시
        if (isManualRefreshEnabled && keyCode == KeyEvent.KEYCODE_F4) {
            triggerManualRefresh()
        }

        return super.onKeyEvent(event)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_NUMBER)) {
            triggerCount = intent.getIntExtra(EXTRA_NUMBER, 5)
            settingsRepository.setPagesPerRefresh(triggerCount)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        overlayController.detachOverlay()
        stopRemapDaemonSync()
        daemonExecutor.shutdownNow()

        runCatching {
            serviceBroadcastReceiver?.let { unregisterReceiver(it) }
        }
        serviceBroadcastReceiver = null
    }

    inner class ServiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_SCREEN -> doFullRefresh()
                ACTION_SHOW_BRIGHTNESS_ACTIVITY -> launchBrightnessDialog()
                ACTION_RP400_GLOBAL_BUTTON -> handleRp400GlobalButton(intent)
                ACTION_PAGE_KEY_REMAP_STATE_CHANGED -> scheduleRemapDaemonStateUpdate()
            }
        }
    }

    private fun shouldKeepRemapDaemonAlive(): Boolean {
        if (!settingsRepository.isPageKeyTapEnabled()) return false
        if (settingsRepository.getPageKeyTapTargetPackages().isEmpty()) return false
        return true
    }

    private fun shouldEnableRemapNow(): Boolean {
        if (!shouldKeepRemapDaemonAlive()) return false
        if (currentPackageName.isBlank()) return false
        if (currentPackageName == BLOCKED_APP_PACKAGE_NAME) return false
        if (!settingsRepository.isPageKeyTapTargetPackage(currentPackageName)) return false
        return true
    }

    private fun scheduleRemapDaemonStateUpdate() {
        remapStateDebounceRunnable?.let { daemonMainHandler.removeCallbacks(it) }
        remapStateDebounceRunnable = Runnable {
            desiredRemapDaemonRunning = shouldKeepRemapDaemonAlive()
            desiredRemapEnabled = shouldEnableRemapNow()
            daemonExecutor.execute { applyRemapDaemonState() }
        }
        remapStateDebounceRunnable?.let {
            daemonMainHandler.postDelayed(it, REMAP_DAEMON_DEBOUNCE_MS)
        }
    }

    private fun applyRemapDaemonState() {
        val shouldRunDaemon = desiredRemapDaemonRunning
        val shouldEnableRemap = desiredRemapEnabled

        if (!shouldRunDaemon) {
            writeRemapStateFile(false)
            if (remapDaemonRunning) {
                stopRemapDaemonSync()
            }
            return
        }

        writeRemapStateFile(shouldEnableRemap)

        if (!remapDaemonRunning) {
            val started = startRemapDaemon()
            remapDaemonRunning = started
            if (!started) {
                Log.w(TAG, "Failed to start page key remap daemon")
                return
            }
        }

        writeRemapStateFile(shouldEnableRemap)
    }

    private fun startRemapDaemon(): Boolean {
        if (!ensureRemapDaemonBinary()) {
            Log.w(TAG, "Remap daemon binary not found or not executable: $PAGE_KEY_REMAP_DAEMON_PATH")
            return false
        }

        val command = """
            PID_FILE="$PAGE_KEY_REMAP_PID_PATH"
            BIN_PATH="$PAGE_KEY_REMAP_DAEMON_PATH"
            STATE_PATH="${remapStateFile.absolutePath}"
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

    private fun ensureRemapDaemonBinary(): Boolean {
        val localBinary = File(filesDir, "page_key_remapd")
        val copiedFromAssets = runCatching {
            val assetPath = resolveRemapDaemonAssetPath() ?: return@runCatching false
            assets.open(assetPath).use { input ->
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

    private fun resolveRemapDaemonAssetPath(): String? {
        val candidatePaths = mutableListOf<String>()
        Build.SUPPORTED_ABIS?.forEach { abi ->
            candidatePaths.add("bin/$abi/page_key_remapd")
        }
        candidatePaths.add(ASSET_PAGE_KEY_REMAP_DAEMON)

        for (path in candidatePaths) {
            val exists = runCatching {
                assets.open(path).use { true }
            }.getOrDefault(false)
            if (exists) return path
        }

        Log.w(
            TAG,
            "No remap daemon asset found. expected one of: ${candidatePaths.joinToString()}"
        )
        return null
    }

    private fun stopRemapDaemonSync() {
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
        remapDaemonRunning = false
    }

    private fun resolveRemapStateFile(): File {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val stateFile = File(baseDir, PAGE_KEY_REMAP_STATE_FILE_NAME)
        stateFile.parentFile?.mkdirs()
        return stateFile
    }

    private fun writeRemapStateFile(enabled: Boolean): Boolean {
        if (::remapStateFile.isInitialized &&
            remapStateFile.exists() &&
            lastWrittenRemapEnabled == enabled
        ) {
            return true
        }

        return runCatching {
            if (!::remapStateFile.isInitialized) {
                remapStateFile = resolveRemapStateFile()
            }
            remapStateFile.parentFile?.mkdirs()
            remapStateFile.writeText(if (enabled) "1\n" else "0\n")
            lastWrittenRemapEnabled = enabled
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

    companion object {
        private const val TAG = "KeyInputService"
        private const val SCREENSHOT_CHORD_DELAY_MS = 250L
        const val EXTRA_NUMBER: String = "EXTRA_NUMBER"
        const val ACTION_REFRESH_SCREEN: String = "com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN"
        const val ACTION_SHOW_BRIGHTNESS_ACTIVITY: String =
            "com.hidsquid.refreshpaper.ACTION_SHOW_BRIGHTNESS_ACTIVITY"
        const val ACTION_RP400_GLOBAL_BUTTON: String = "ridi.intent.action.GLOBAL_BUTTON"
        const val ACTION_PAGE_KEY_REMAP_STATE_CHANGED: String =
            "com.hidsquid.refreshpaper.ACTION_PAGE_KEY_REMAP_STATE_CHANGED"
        private const val F1_ACTION_DEBOUNCE_MS = 120L
        private const val REMAP_DAEMON_DEBOUNCE_MS = 200L
        private const val ROOT_CMD_TIMEOUT_MS = 3_000L
        private const val PAGE_KEY_REMAP_DAEMON_PATH = "/data/local/tmp/page_key_remapd"
        private const val PAGE_KEY_REMAP_PID_PATH = "/data/local/tmp/page_key_remapd.pid"
        private const val PAGE_KEY_REMAP_STATE_FILE_NAME = "page_key_remap_state"
        private const val ASSET_PAGE_KEY_REMAP_DAEMON = "bin/page_key_remapd"
        private const val BLOCKED_APP_PACKAGE_NAME = "com.ridi.paper"
    }
}
