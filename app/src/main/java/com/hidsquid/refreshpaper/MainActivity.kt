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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hidsquid.refreshpaper.MainUtils.Companion.isAccessibilityServiceEnabled
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PREFS_NAME = "MyPrefs"
        private const val PREF_KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val PREF_KEY_MANUAL_REFRESH = "manual_refresh_enabled"
        private const val PREF_KEY_PAGES = "numberInput"
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

    private fun showPageCountDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
        val currentCount = prefs.getInt(PREF_KEY_PAGES, 5)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_page_count, null)
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

        fun setChecked(target: ImageView, selected: Boolean) {
            target.visibility = if (selected) View.VISIBLE else View.INVISIBLE
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

        val onOptionClicked = View.OnClickListener { view ->
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

        item1.setOnClickListener(onOptionClicked)
        item3.setOnClickListener(onOptionClicked)
        item5.setOnClickListener(onOptionClicked)
        item10.setOnClickListener(onOptionClicked)
        item20.setOnClickListener(onOptionClicked)
        itemOff.setOnClickListener(onOptionClicked)

        dialog.show()

        val widthPx = resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateSummaryText() {
        val layout = binding.layoutSettings
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean(PREF_KEY_AUTO_REFRESH, false)
        val count = prefs.getInt(PREF_KEY_PAGES, 5)

        layout.autoRefreshSwitch.setOnCheckedChangeListener(null)
        layout.autoRefreshSwitch.isChecked = isEnabled
        attachAutoOffListener()

        if (isEnabled) {
            layout.tvCurrentSetting.text = "${count} 페이지마다 새로고침"
            layout.tvCurrentSetting.alpha = 1.0f
        } else {
            layout.tvCurrentSetting.text = "사용 안 함"
            layout.tvCurrentSetting.alpha = 0.5f
        }
    }
}