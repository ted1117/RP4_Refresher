package com.hidsquid.refreshpaper;

import static com.hidsquid.refreshpaper.MainUtils.isAccessibilityServiceEnabled;
import static com.hidsquid.refreshpaper.MainUtils.isPackageUsageStatsEnabled;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.hidsquid.refreshpaper.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // TopAppBar 설정
        MaterialToolbar topAppBar = binding.topAppBar;
        setSupportActionBar(topAppBar);

        if (isAccessibilityServiceEnabled(this) && isPackageUsageStatsEnabled(this)) {
            showCards();
        } else {
            updateUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityServiceEnabled(this) && isPackageUsageStatsEnabled(this)) {
            showCards();
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
            showCards();
        } else {
            binding.usageTextView.setVisibility(View.VISIBLE);
            binding.accessibilityButton.setVisibility(View.VISIBLE);
            binding.usageStatsButton.setVisibility(View.VISIBLE);
        }
    }

    private void showCards() {
        binding.usageTextView.setVisibility(View.GONE);
        binding.accessibilityButton.setVisibility(View.GONE);
        binding.usageStatsButton.setVisibility(View.GONE);
        binding.autoRefreshCard.setVisibility(View.VISIBLE);
        binding.manualRefreshCard.setVisibility(View.VISIBLE);
    }

    public void openAutoRefreshSettings(View v) {
        Intent intent = new Intent(MainActivity.this, PageCountSettingsActivity.class);
        startActivity(intent);
    }

    public void openManualRefreshSettings(View v) {
        Intent intent = new Intent(MainActivity.this, ManualRefreshSettingsActivity.class);
        startActivity(intent);
    }

    @Deprecated
    private void showListView() {
        binding.usageTextView.setVisibility(View.GONE);
        binding.accessibilityButton.setVisibility(View.GONE);
        binding.usageStatsButton.setVisibility(View.GONE);

        String[] listItems = {"자동 새로고침 설정", "수동 새로고침 설정"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listItems);

        binding.listView.setAdapter(adapter);
        binding.listView.setVisibility(View.VISIBLE);

        binding.listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                Intent intent = new Intent(MainActivity.this, PageCountSettingsActivity.class);
                startActivity(intent);
            } else if (position == 1) {
                Intent intent = new Intent(MainActivity.this, ManualRefreshSettingsActivity.class);
                startActivity(intent);
            }
        });
    }
}