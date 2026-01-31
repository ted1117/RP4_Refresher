package com.hidsquid.refreshpaper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hidsquid.refreshpaper.MainUtils.Companion.isAccessibilityServiceEnabled
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        checkPermissionAndShowUI()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndShowUI()
    }

    private fun checkPermissionAndShowUI() {
        if (isAccessibilityServiceEnabled(this)) {
            showCards()
        } else {
            showPermissionRequestUI()
        }
    }

    // 접근성 설정 화면으로 이동
    fun onClick(v: View?) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun showPermissionRequestUI() {
        binding.layoutPermission.root.visibility = View.VISIBLE
        binding.layoutSettings.root.visibility = View.GONE
    }

    private fun showCards() {
        binding.layoutPermission.root.visibility = View.GONE
        binding.layoutSettings.root.visibility = View.VISIBLE
    }

    fun openAutoRefreshSettings(v: View?) {
        val intent = Intent(this@MainActivity, PageCountSettingsActivity::class.java)
        startActivity(intent)
    }

    fun openManualRefreshSettings(v: View?) {
        val intent = Intent(this@MainActivity, ManualRefreshSettingsActivity::class.java)
        startActivity(intent)
    }
}