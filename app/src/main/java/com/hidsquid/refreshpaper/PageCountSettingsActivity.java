package com.hidsquid.refreshpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hidsquid.refreshpaper.databinding.ActivityPageCountSettingsBinding;

public class PageCountSettingsActivity extends AppCompatActivity {

    private ActivityPageCountSettingsBinding binding;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_PAGES_PER_REFRESH = "numberInput";
    private static final String PREF_KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPageCountSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // TopAppBar 설정
        MaterialToolbar topAppBar = binding.topAppBar;
        setSupportActionBar(topAppBar);

        // TopAppBar에서 취소버튼 활성화
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // SharedPreferences 초기화
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedInput = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
        binding.numberInput.setText(String.valueOf(savedInput));

        binding.submitButton.setOnClickListener(v -> {
            String inputText = binding.numberInput.getText().toString();
            if (!inputText.isEmpty()) {
                int number = Integer.parseInt(inputText);

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PREF_KEY_PAGES_PER_REFRESH, number);
                editor.apply();

                Intent intent = new Intent(PageCountSettingsActivity.this, KeyInputDetectingService.class);
                intent.putExtra(KeyInputDetectingService.EXTRA_NUMBER, number);
                startService(intent);
                Toast.makeText(PageCountSettingsActivity.this, "입력된 숫자: " + number, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(PageCountSettingsActivity.this, "숫자를 입력하세요", Toast.LENGTH_SHORT).show();
            }
        });

        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false);
        setUIComponentsEnabled(isFeatureEnabled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_page_count_settings, menu);
        MenuItem toggleItem = menu.findItem(R.id.action_toggle);
        View actionView = toggleItem.getActionView();
        MaterialSwitch toggleSwitch = actionView.findViewById(R.id.switch_material);

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false);
        assert toggleSwitch != null;
        toggleSwitch.setChecked(isFeatureEnabled);

        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, isChecked);
            editor.apply();
            setUIComponentsEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(PageCountSettingsActivity.this, "자동 새로고침이 켜졌습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(PageCountSettingsActivity.this, "자동 새로고침이 꺼졌습니다", Toast.LENGTH_SHORT).show();
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close the current activity and return to the previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUIComponentsEnabled(boolean isEnabled) {
        binding.numberInput.setEnabled(isEnabled);
        binding.submitButton.setEnabled(isEnabled);
    }
}