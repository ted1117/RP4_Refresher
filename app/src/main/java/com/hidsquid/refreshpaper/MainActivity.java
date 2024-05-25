package com.hidsquid.refreshpaper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView usageTextView;
    private Button accessibilityButton;
    private EditText numberInput;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usageTextView = findViewById(R.id.usageTextView);
        accessibilityButton = findViewById(R.id.accessibilityButton);
        numberInput = findViewById(R.id.numberInput);
        submitButton = findViewById(R.id.submitButton);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputText = numberInput.getText().toString();
                if (!inputText.isEmpty()) {
                    int number = Integer.parseInt(inputText);

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(numberInput.getWindowToken(), 0);

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
        updateUIBasedOnAccessibilityService();
    }

    public void onClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void updateUIBasedOnAccessibilityService() {
        if (isAccessibilityServiceEnabled(this)) {
            usageTextView.setVisibility(View.GONE);
            accessibilityButton.setVisibility(View.GONE);
            numberInput.setVisibility(View.VISIBLE);
            submitButton.setVisibility(View.VISIBLE);
        } else {
            usageTextView.setVisibility(View.VISIBLE);
            accessibilityButton.setVisibility(View.VISIBLE);
            numberInput.setVisibility(View.GONE);
            submitButton.setVisibility(View.GONE);
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
}