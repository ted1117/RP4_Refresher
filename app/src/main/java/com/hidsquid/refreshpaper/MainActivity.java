package com.hidsquid.refreshpaper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hidsquid.refreshpaper.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_PAGES_PER_REFRESH = "numberInput";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedInput = sharedPreferences.getInt(PREF_KEY_PAGES_PER_REFRESH, 5);
        Log.d("MainActivity", "PREF_KEY_PAGES_PER_REFRESH" + PREF_KEY_PAGES_PER_REFRESH);
        binding.numberInput.setText(String.valueOf(savedInput));

        binding.submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputText = binding.numberInput.getText().toString();
                if (!inputText.isEmpty()) {
                    int number = Integer.parseInt(inputText);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt(PREF_KEY_PAGES_PER_REFRESH, number);
                    editor.apply();

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(binding.numberInput.getWindowToken(), 0);

                    Intent intent = new Intent(MainActivity.this, KeyInputDetectingService.class);
                    intent.putExtra(KeyInputDetectingService.EXTRA_NUMBER, number);
                    startService(intent);
                    Toast.makeText(MainActivity.this, "입력된 숫자: " + number, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "숫자를 입력하세요", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    public void onClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    public void setUsageStatsEnabled(View v) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    private void updateUI() {
        if (isAccessibilityServiceEnabled(this) && isPackageUsageStatsEnabled(this)) {
            binding.usageTextView.setVisibility(View.GONE);
            binding.accessibilityButton.setVisibility(View.GONE);
            binding.usageStatsButton.setVisibility(View.GONE);
            binding.refreshPagesTextView.setVisibility(View.VISIBLE);
            binding.numberInput.setVisibility(View.VISIBLE);
            binding.submitButton.setVisibility(View.VISIBLE);
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo enabledService : enabledServices) {
            if (enabledService.getResolveInfo().serviceInfo.packageName.equals(context.getPackageName()) &&
                    enabledService.getResolveInfo().serviceInfo.name.equals(KeyInputDetectingService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPackageUsageStatsEnabled(Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(APP_OPS_SERVICE);
        int mode = AppOpsManager.MODE_DEFAULT;
        mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Binder.getCallingUid(), context.getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }
}