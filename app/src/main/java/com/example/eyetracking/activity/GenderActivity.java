package com.example.eyetracking.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

import com.example.eyetracking.R;

public class GenderActivity extends AppCompatActivity {
    private LinearLayout ageInputLayout;
    private EditText ageEditText;
    private String age;
    private String gender;
    SharedPreferences sharedPreferences;

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
        ageEditText = findViewById(R.id.ageEditText);
        submitButton.setOnClickListener(v -> {
            age = ageEditText.getText().toString();
            if (age.isEmpty()) {
                Toast.makeText(this, "Please enter your age", Toast.LENGTH_SHORT).show();
            } else {
                ageInputLayout.setVisibility(View.GONE);
            }
        });

        Button continueButton=findViewById(R.id.buttonToPage1);
        continueButton.setOnClickListener(v -> {
            gender = genderSpinner.getSelectedItem().toString();
            if (gender==null||gender.isEmpty()) {
                Toast.makeText(this, "Please choose your gender", Toast.LENGTH_SHORT).show();
            } else if(age==null||age.isEmpty()){
                Toast.makeText(this, "Please enter your age", Toast.LENGTH_SHORT).show();
            }else{
                saveInfo(age,gender);
                Intent intent = new Intent(this, InstructionActivity.class);
                startActivity(intent);
            }
        });
    }

    private void showAgeDialog() {
        ageInputLayout.setVisibility(View.VISIBLE);
    }

    private void saveInfo(String age,String gender) {
        // Here, implement the logic to submit age data to the backend
        // Could use OkHttpClient, Retrofit, etc. to make a POST request
        int ageNumber=Integer.parseInt(age);
        int genderNumber;
        if(gender.equals("Male")){
            genderNumber=1;
        }else if(gender.equals("Female")){
            genderNumber=2;
        }else{
            genderNumber=3;
        }
        sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("age", ageNumber);
        editor.putInt("gender", genderNumber);
        editor.apply();



    }

    public void backToAgreement(View view) {
        Intent intent = new Intent(this, AgreementActivity.class);
        startActivity(intent);
    }
}
