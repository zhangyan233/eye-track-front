package com.example.eyetracking.activity;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.eyetracking.R;

public class CalibrationActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibration);
    }

    public void goToCalibrationDirection(View view) {
        Intent intent = new Intent(this, CalibrationdirectorActivity.class);
        startActivity(intent);
    }


    public void backToInstruction(View view) {
        Intent intent = new Intent(this, InstructionActivity.class);
        startActivity(intent);
    }
}



