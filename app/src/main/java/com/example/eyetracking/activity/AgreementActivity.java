package com.example.eyetracking.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eyetracking.R;

public class AgreementActivity extends AppCompatActivity {

    private CheckBox checkboxPrivacyPolicy;
    private CheckBox checkboxDataConsent;
    private TextView textWarning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.agreement);

        checkboxPrivacyPolicy = findViewById(R.id.checkbox_privacy_policy);
        checkboxDataConsent = findViewById(R.id.checkbox_data_consent);
        textWarning = findViewById(R.id.text_warning);
    }

    public void goToGender(View view) {
        if (checkboxPrivacyPolicy.isChecked() && checkboxDataConsent.isChecked()) {
            textWarning.setVisibility(View.GONE);
            // Continue to next activity
            Toast.makeText(this, "All options selected. Continuing...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, GenderActivity.class));
        } else {
            textWarning.setVisibility(View.VISIBLE);
        }
    }
}
