package com.example.eyetracking.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

import com.example.eyetracking.R;

public class GenderActivity extends AppCompatActivity {
    private LinearLayout ageInputLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gender); // This layout should include the age dialog button and the spinner

        // Setup the gender spinner
        Spinner genderSpinner = findViewById(R.id.genderSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(adapter);

        // Setup the button to show age input dialog
        Button showDialogButton = findViewById(R.id.buttonShowAgeDialog); // Ensure this button is in your gender.xml layout
        showDialogButton.setOnClickListener(v -> showAgeDialog());

        ageInputLayout = findViewById(R.id.ageInputLayout);
        Button submitButton = findViewById(R.id.ageSubmitButton);
        submitButton.setOnClickListener(v -> {
            EditText ageEditText = findViewById(R.id.ageEditText);
            String age = ageEditText.getText().toString();
            submitAgeToBackend(age);
            ageInputLayout.setVisibility(View.GONE);
        });
    }

    private void showAgeDialog() {
        ageInputLayout.setVisibility(View.VISIBLE);
    }

    private void submitAgeToBackend(String age) {
        // Here, implement the logic to submit age data to the backend
        // Could use OkHttpClient, Retrofit, etc. to make a POST request
    }

    public void goToInstruction(View view) {
        Intent intent = new Intent(this, InstructionActivity.class);
        startActivity(intent);
    }

    public void backToAgreement(View view) {
        Intent intent = new Intent(this, AgreementActivity.class);
        startActivity(intent);
    }
}
