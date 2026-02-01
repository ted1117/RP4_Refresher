package com.hidsquid.refreshpaper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hidsquid.refreshpaper.MainUtils.Companion.isAccessibilityServiceEnabled
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PREFS_NAME = "MyPrefs"
        private const val PREF_KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES = "numberInput"

        private const val EPD_MINIMIZE_AFTERIMAGE = 0
        private const val EPD_NORMAL = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        checkPermissionAndShowUI()
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndShowUI()
        // ✅ 시스템값이 단일 진실: 복귀할 때마다(리디에서 바꿨을 수도 있으니) 갱신
        updateEpdModeSummary()
    }

    private fun checkPermissionAndShowUI() {
        if (isAccessibilityServiceEnabled(this)) {
            binding.layoutPermission.root.visibility = View.GONE
            binding.layoutSettings.root.visibility = View.VISIBLE
        } else {
            binding.layoutPermission.root.visibility = View.VISIBLE
            binding.layoutSettings.root.visibility = View.GONE
        }
    }

    fun onClick(v: View?) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val layout = binding.layoutSettings

        layout.autoRefreshSwitch.isChecked = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
        layout.manualRefreshSwitch.isChecked = prefs.getBoolean(PREF_KEY_MANUAL_REFRESH, false)

        updateSummaryText()
        updateEpdModeSummary()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val layout = binding.layoutSettings

        val showDialogAction = View.OnClickListener { showPageCountDialog() }
        layout.autoRefreshCard.setOnClickListener(showDialogAction)
        layout.tvCurrentSetting.setOnClickListener(showDialogAction)

        layout.autoRefreshSwitch.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true

            val enabled = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
            if (enabled) {
                prefs.edit().putBoolean(PREF_KEY_AUTO_REFRESH, false).apply()
                updateSummaryText()
            } else {
                layout.autoRefreshSwitch.setOnCheckedChangeListener(null)
                layout.autoRefreshSwitch.isChecked = false
                attachAutoOffListener()
                showPageCountDialog()
            }
            true
        }

        attachAutoOffListener()

        layout.manualRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_MANUAL_REFRESH, isChecked).apply()
        }

        layout.manualRefreshCard.setOnClickListener {
            layout.manualRefreshSwitch.isChecked = !layout.manualRefreshSwitch.isChecked
        }

        layout.epdModeCard.setOnClickListener {
            showEpdDisplayModeDialog()
        }
    }

    private fun attachAutoOffListener() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val layout = binding.layoutSettings

        layout.autoRefreshSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                prefs.edit().putBoolean(PREF_KEY_AUTO_REFRESH, false).apply()
                updateSummaryText()
            }
        }
    }

    /* =========================
       EPD DISPLAY MODE
       - get: Framework API (non-root)
       - put: root(su)
       ========================= */

    private fun execSu(cmd: String): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            p.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun getDisplayMode(): Int = withContext(Dispatchers.IO) {
        // ✅ 단일 진실: 시스템에서 직접 읽기 (CLI 대신 Framework API)
        try {
            Settings.System.getInt(contentResolver, "display_mode")
        } catch (_: Throwable) {
            EPD_NORMAL
        }
    }

    private suspend fun setDisplayMode(mode: Int): Boolean = withContext(Dispatchers.IO) {
        // ✅ 쓰기만 su
        execSu("settings put system display_mode $mode")
    }

    private fun showEpdDisplayModeDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_epd_display_mode, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val itemMin = dialogView.findViewById<RelativeLayout>(R.id.item1)
        val itemNormal = dialogView.findViewById<RelativeLayout>(R.id.item3)
        val checkMin = dialogView.findViewById<ImageView>(R.id.check1)
        val checkNormal = dialogView.findViewById<ImageView>(R.id.check3)

        fun setChecked(mode: Int) {
            checkMin.visibility = if (mode == EPD_MINIMIZE_AFTERIMAGE) View.VISIBLE else View.GONE
            checkNormal.visibility = if (mode == EPD_NORMAL) View.VISIBLE else View.GONE
            checkMin.bringToFront()
            checkNormal.bringToFront()
        }

        // 초기엔 둘 다 숨김(깜빡임 방지)
        checkMin.visibility = View.GONE
        checkNormal.visibility = View.GONE

        dialog.setOnShowListener {
            lifecycleScope.launch {
                // ✅ 다이얼로그 뜬 다음에 1회만 읽어서 반영
                val mode = getDisplayMode()
                setChecked(if (mode == EPD_MINIMIZE_AFTERIMAGE) EPD_MINIMIZE_AFTERIMAGE else EPD_NORMAL)
            }
        }

        val onClick = View.OnClickListener { view ->
            val sysMode = when (view.id) {
                R.id.item1 -> EPD_MINIMIZE_AFTERIMAGE
                R.id.item3 -> EPD_NORMAL
                else -> EPD_NORMAL
            }

            lifecycleScope.launch {
                val ok = setDisplayMode(sysMode)
                if (!ok) {
                    Toast.makeText(this@MainActivity, "루트로 display_mode 설정 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // ✅ 반영 후 재조회해서 UI 확정
                val modeNow = getDisplayMode()
                setChecked(if (modeNow == EPD_MINIMIZE_AFTERIMAGE) EPD_MINIMIZE_AFTERIMAGE else EPD_NORMAL)
                updateEpdModeSummary()
                dialog.dismiss()
            }
        }

        itemMin.setOnClickListener(onClick)
        itemNormal.setOnClickListener(onClick)

        dialog.show()
        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateEpdModeSummary() {
        lifecycleScope.launch {
            val mode = getDisplayMode()
            binding.layoutSettings.tvEpdModeSetting.text = getString(
                if (mode == EPD_MINIMIZE_AFTERIMAGE) R.string.setting_value_display_mode_minimize_afterimage
                else R.string.setting_value_display_mode_normal
            )
        }
    }

    /* ========================= */

    private fun showPageCountDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
        val currentCount = prefs.getInt(PREF_KEY_PAGES, 5)

        val dialogView =
            LayoutInflater.from(this).inflate(R.layout.dialog_page_count, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val item1 = dialogView.findViewById<RelativeLayout>(R.id.item1)
        val item3 = dialogView.findViewById<RelativeLayout>(R.id.item3)
        val item5 = dialogView.findViewById<RelativeLayout>(R.id.item5)
        val item10 = dialogView.findViewById<RelativeLayout>(R.id.item10)
        val item20 = dialogView.findViewById<RelativeLayout>(R.id.item20)
        val itemOff = dialogView.findViewById<RelativeLayout>(R.id.itemOff)

        val check1 = dialogView.findViewById<ImageView>(R.id.check1)
        val check3 = dialogView.findViewById<ImageView>(R.id.check3)
        val check5 = dialogView.findViewById<ImageView>(R.id.check5)
        val check10 = dialogView.findViewById<ImageView>(R.id.check10)
        val check20 = dialogView.findViewById<ImageView>(R.id.check20)
        val checkOff = dialogView.findViewById<ImageView>(R.id.checkOff)

        fun setChecked(v: ImageView, sel: Boolean) {
            v.visibility = if (sel) View.VISIBLE else View.GONE
        }

        if (!isEnabled) {
            setChecked(checkOff, true)
        } else {
            when (currentCount) {
                1 -> setChecked(check1, true)
                3 -> setChecked(check3, true)
                5 -> setChecked(check5, true)
                10 -> setChecked(check10, true)
                20 -> setChecked(check20, true)
                else -> setChecked(check5, true)
            }
        }

        val onClick = View.OnClickListener { view ->
            val editor = prefs.edit()
            if (view.id == R.id.itemOff) {
                editor.putBoolean(PREF_KEY_AUTO_REFRESH, false)
            } else {
                editor.putBoolean(PREF_KEY_AUTO_REFRESH, true)

                val selectedCount = when (view.id) {
                    R.id.item1 -> 1
                    R.id.item3 -> 3
                    R.id.item5 -> 5
                    R.id.item10 -> 10
                    R.id.item20 -> 20
                    else -> 5
                }
                editor.putInt(PREF_KEY_PAGES, selectedCount)

                val intent = Intent(this, KeyInputDetectingService::class.java)
                intent.putExtra(KeyInputDetectingService.EXTRA_NUMBER, selectedCount)
                startService(intent)
            }
            editor.apply()
            updateSummaryText()
            dialog.dismiss()
        }

        item1.setOnClickListener(onClick)
        item3.setOnClickListener(onClick)
        item5.setOnClickListener(onClick)
        item10.setOnClickListener(onClick)
        item20.setOnClickListener(onClick)
        itemOff.setOnClickListener(onClick)

        dialog.show()
        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateSummaryText() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val layout = binding.layoutSettings

        val enabled = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
        val count = prefs.getInt(PREF_KEY_PAGES, 5)

        layout.autoRefreshSwitch.setOnCheckedChangeListener(null)
        layout.autoRefreshSwitch.isChecked = enabled
        attachAutoOffListener()

        if (enabled) {
            layout.tvCurrentSetting.text = getString(R.string.setting_summary_auto_refresh_on, count)
            layout.tvCurrentSetting.alpha = 1f
        } else {
            layout.tvCurrentSetting.text = getString(R.string.setting_summary_auto_refresh_off)
            layout.tvCurrentSetting.alpha = 0.5f
        }
    }
}