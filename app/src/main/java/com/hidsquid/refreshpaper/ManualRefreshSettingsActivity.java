package com.hidsquid.refreshpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ManualRefreshSettingsActivity extends AppCompatActivity {
    private static final String TAG = "ManualRefreshSettings";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_MANUAL_REFRESH_ENABLED = "manual_refresh_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_refresh_settings);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manual_refresh_settings, menu);
        MenuItem toggleItem = menu.findItem(R.id.action_toggle);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch toggleSwitch = (Switch) toggleItem.getActionView();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, false);
        toggleSwitch.setChecked(isFeatureEnabled);

        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_KEY_MANUAL_REFRESH_ENABLED, isChecked);
            editor.apply();
            if (isChecked) {
                Toast.makeText(ManualRefreshSettingsActivity.this, "기능이 켜졌습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ManualRefreshSettingsActivity.this, "기능이 꺼졌습니다", Toast.LENGTH_SHORT).show();
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