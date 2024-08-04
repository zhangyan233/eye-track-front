package com.example.eyetracking.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.eyetracking.R;

public class InstructionActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instruction);
    }


    public void goToCalibration(View view) {
        Intent intent = new Intent(this, CalibrationActivity.class);
        startActivity(intent);
    }

    public void backToGender(View view) {
        Intent intent = new Intent(this, GenderActivity.class);
        startActivity(intent);
    }
}

