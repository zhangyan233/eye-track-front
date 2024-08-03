package com.example.eyetracking;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
