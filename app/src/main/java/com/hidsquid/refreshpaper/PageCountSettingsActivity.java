package com.hidsquid.refreshpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hidsquid.refreshpaper.databinding.ActivityPageCountSettingsBinding;

public class PageCountSettingsActivity extends AppCompatActivity {

    private ActivityPageCountSettingsBinding binding;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String PREF_KEY_PAGES_PER_REFRESH = "numberInput";

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

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(binding.numberInput.getWindowToken(), 0);

                Intent intent = new Intent(PageCountSettingsActivity.this, KeyInputDetectingService.class);
                intent.putExtra(KeyInputDetectingService.EXTRA_NUMBER, number);
                startService(intent);
                Toast.makeText(PageCountSettingsActivity.this, "입력된 숫자: " + number, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(PageCountSettingsActivity.this, "숫자를 입력하세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close the current activity and return to the previous one
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}