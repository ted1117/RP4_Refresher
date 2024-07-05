package com.hidsquid.refreshpaper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hidsquid.refreshpaper.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (isAccessibilityServiceEnabled(this) && isPackageUsageStatsEnabled(this)) {
            showListView();
        } else {
            updateUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityServiceEnabled(this) && isPackageUsageStatsEnabled(this)) {
            showListView();
        } else {
            updateUI();
        }
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
            showListView();
        } else {
            binding.usageTextView.setVisibility(View.VISIBLE);
            binding.accessibilityButton.setVisibility(View.VISIBLE);
            binding.usageStatsButton.setVisibility(View.VISIBLE);
        }
    }

    private void showListView() {
        binding.usageTextView.setVisibility(View.GONE);
        binding.accessibilityButton.setVisibility(View.GONE);
        binding.usageStatsButton.setVisibility(View.GONE);

        String[] listItems = {"리프레시할 페이지 숫자 설정"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listItems);
        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        setContentView(listView);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                Intent intent = new Intent(MainActivity.this, PageCountSettingsActivity.class);
                startActivity(intent);
            }
        });
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
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Binder.getCallingUid(), context.getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }
}