package com.hidsquid.refreshpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hidsquid.refreshpaper.databinding.ActivityManualRefreshSettingsBinding;
import com.hidsquid.refreshpaper.databinding.ActivityPageCountSettingsBinding;

public class ManualRefreshSettingsActivity extends AppCompatActivity {
    private static final String TAG = "ManualRefreshSettings";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled";

    private ActivityManualRefreshSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManualRefreshSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // TopAppBar 설정
        MaterialToolbar topAppBar = binding.topAppBar;
        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manual_refresh_settings, menu);
        MenuItem toggleItem = menu.findItem(R.id.action_toggle);
        View actionView = toggleItem.getActionView();
        MaterialSwitch toggleSwitch = actionView.findViewById(R.id.switch_material);

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false);
        toggleSwitch.setChecked(isFeatureEnabled);

        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, isChecked);
            editor.apply();
            if (isChecked) {
                Toast.makeText(ManualRefreshSettingsActivity.this, "수동 새로고침이 켜졌습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ManualRefreshSettingsActivity.this, "수동 새로고침이 꺼졌습니다", Toast.LENGTH_SHORT).show();
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}