package com.example.eyetracking;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CalibrationdirectorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibrationdirector);
    }
    public void startRecording(View view) {
        // Your recording logic here
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
    }

    public void goToCalibrationreddot(View view) {
        Intent intent = new Intent(this, CalibrationreddotActivity.class);
        startActivity(intent);
    }


    public void backToCalibration(View view) {
        Intent intent = new Intent(this, CalibrationActivity.class);
        startActivity(intent);
    }
}


