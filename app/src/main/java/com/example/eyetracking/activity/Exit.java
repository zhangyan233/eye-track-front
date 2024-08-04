package com.example.eyetracking.activity;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.eyetracking.R;

public class Exit extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exit);
    }


    public void exitApp(View view) {
        Intent intent = new Intent(this, StartActivity.class);
        startActivity(intent);
    }
}
