package com.hidsquid.refreshpaper

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.hidsquid.refreshpaper.databinding.ActivityManualRefreshSettingsBinding

class ManualRefreshSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualRefreshSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualRefreshSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_manual_refresh_settings, menu)

        val toggleItem = menu.findItem(R.id.action_toggle)
        val toggleSwitch = toggleItem.actionView?.findViewById<MaterialSwitch>(R.id.switch_material)

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false)

        // [최적화] toggleSwitch가 null이 아닐 때만 블록 실행
        toggleSwitch?.apply {
            isChecked = isFeatureEnabled

            // 람다식으로 간결하게 변경
            setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit()
                    .putBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, isChecked)
                    .apply()

                // if문으로 메시지 분기 처리하여 코드 중복 제거
                val message = if (isChecked) "수동 새로고침이 켜졌습니다" else "수동 새로고침이 꺼졌습니다"
                Toast.makeText(this@ManualRefreshSettingsActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val PREFS_NAME = "MyPrefs"
        private const val PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled"
    }
}