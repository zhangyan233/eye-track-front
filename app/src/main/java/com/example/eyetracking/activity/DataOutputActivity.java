package com.example.eyetracking.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eyetracking.R;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DataOutputActivity extends Activity {
    private TextView videoInfoTextView; // TextView to display video info
    private Button btnExportData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dataoutput);

        // Assuming video information is passed as an intent extra
        String videoInfo = getIntent().getStringExtra("videoInfo");

        videoInfoTextView = findViewById(R.id.text_video_information); // Make sure ID matches in XML
        videoInfoTextView.setText(videoInfo); // Display the video information

        btnExportData = findViewById(R.id.btn_export_data_csv); // Correct ID for a button in XML

        btnExportData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportDataToCSV(videoInfo); // Pass video info to CSV export
            }
        });
    }

    private void exportDataToCSV(String videoInfo) {
        String csvHeader = "Video Information, User Results\n";
        String csvContent = videoInfo + ", Result for the video\n"; // Example, modify as needed

        File csvFile = new File(Environment.getExternalStorageDirectory(), "testResults.csv");
        try {
            FileWriter writer = new FileWriter(csvFile);
            writer.append(csvHeader);
            writer.append(csvContent);
            writer.flush();
            writer.close();
            Toast.makeText(this, "Data exported to CSV successfully.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error in exporting data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    public void goToExit(View view) {
        Intent intent = new Intent(this, Exit.class);
        startActivity(intent);
    }


    public void backToVideoPlay(View view) {
        Intent intent = new Intent(this, VideoPlayActivity.class);
        startActivity(intent);
    }
}
