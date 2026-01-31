package com.hidsquid.refreshpaper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hidsquid.refreshpaper.MainUtils.Companion.isAccessibilityServiceEnabled
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    // !! 없이 안전하게 쓰기 위해 lateinit 사용
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TopAppBar 설정
        setSupportActionBar(binding.topAppBar)

        checkPermissionAndShowUI()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndShowUI()
    }

    /**
     * 이제 '접근성 권한' 하나만 확인하면 됩니다.
     * UsageStats(앱 사용 기록) 권한 체크 로직은 삭제했습니다.
     */
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

    // UsageStats 버튼 클릭 메서드는 이제 필요 없어서 삭제했습니다.

    private fun showPermissionRequestUI() {
        // 권한이 없을 때: 안내 멘트와 버튼 보이기
        binding.usageTextView.visibility = View.VISIBLE
        binding.accessibilityButton.visibility = View.VISIBLE

        // [삭제 예정] UsageStats 버튼은 이제 필요 없으므로 숨김 처리
        binding.usageStatsButton.visibility = View.GONE

        // 설정 카드들은 숨김
        binding.autoRefreshCard.visibility = View.GONE
        binding.manualRefreshCard.visibility = View.GONE
    }

    private fun showCards() {
        // 권한이 있을 때: 설정 카드들 보이기
        binding.usageTextView.visibility = View.GONE
        binding.accessibilityButton.visibility = View.GONE
        binding.usageStatsButton.visibility = View.GONE

        binding.autoRefreshCard.visibility = View.VISIBLE
        binding.manualRefreshCard.visibility = View.VISIBLE
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