package com.hidsquid.refreshpaper

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.hidsquid.refreshpaper.databinding.ActivityPageCountSettingsBinding

class PageCountSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageCountSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageCountSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedInput = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5)
        binding.numberInput.setText(savedInput.toString())

        binding.submitButton.setOnClickListener {
            val inputText = binding.numberInput.text.toString()

            if (inputText.isNotEmpty()) {
                val number = inputText.toInt()

                sharedPreferences.edit()
                    .putInt(PREF_KEY_PAGES_PER_REFRESH, number)
                    .apply()

                val intent = Intent(this, KeyInputDetectingService::class.java).apply {
                    putExtra(KeyInputDetectingService.EXTRA_NUMBER, number)
                }
                startService(intent)

                Toast.makeText(this, "입력된 숫자: $number", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "숫자를 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        val isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false)
        setUIComponentsEnabled(isFeatureEnabled)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_page_count_settings, menu)

        val toggleItem = menu.findItem(R.id.action_toggle)
        val toggleSwitch = toggleItem.actionView?.findViewById<MaterialSwitch>(R.id.switch_material)

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false)

        toggleSwitch?.apply {
            isChecked = isFeatureEnabled
            setOnCheckedChangeListener { _, checked ->
                sharedPreferences.edit()
                    .putBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, checked)
                    .apply()

                setUIComponentsEnabled(checked)

                val message = if (checked) "자동 새로고침이 켜졌습니다" else "자동 새로고침이 꺼졌습니다"
                Toast.makeText(this@PageCountSettingsActivity, message, Toast.LENGTH_SHORT).show()
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

    private fun setUIComponentsEnabled(isEnabled: Boolean) {
        binding.numberInput.isEnabled = isEnabled
        binding.submitButton.isEnabled = isEnabled
    }

    companion object {
        private const val PREFS_NAME = "MyPrefs"
        private const val PREF_KEY_PAGES_PER_REFRESH = "numberInput"
        private const val PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
    }
}