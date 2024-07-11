package com.hidsquid.refreshpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        // Enable the back button in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedInput = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
        binding.numberInput.setText(String.valueOf(savedInput));

        // Submit button click listener
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

        // Disable UI components if the feature is off
        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false);
        setUIComponentsEnabled(isFeatureEnabled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_page_count_settings, menu);
        MenuItem toggleItem = menu.findItem(R.id.action_toggle);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch toggleSwitch = (Switch) toggleItem.getActionView();

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFeatureEnabled = sharedPreferences.getBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, false);
        assert toggleSwitch != null;
        toggleSwitch.setChecked(isFeatureEnabled);

        toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PREF_KEY_AUTO_REFRESH_ENABLED, isChecked);
            editor.apply();
            setUIComponentsEnabled(isChecked);
//            if (isChecked) {
//                showToast("기능이 켜졌습니다");
//                startService(new Intent(this, KeyInputDetectingService.class));
//            } else {
//                showToast("기능이 꺼졌습니다");
//                stopService(new Intent(this, KeyInputDetectingService.class));
//            }
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

    private void showToast(String message) {
        Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        // Change background color of the toast
        toast.getView().setBackground(new ColorDrawable(Color.GRAY));
        // Change text color of the toast
        TextView text = toast.getView().findViewById(android.R.id.message);
        text.setTextColor(Color.WHITE);
        toast.show();
    }
}