package com.example.eyetracking.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eyetracking.R;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start);

    }


    public void goToAgreement(View view) {
        Intent intent = new Intent(this, AgreementActivity.class);
        startActivity(intent);
    }
}
