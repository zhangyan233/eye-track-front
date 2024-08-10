package com.example.eyetracking.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.eyetracking.R;

public class VideoChooseActivity extends Activity {
    private Spinner videoSpinner;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.videochoose);

        videoSpinner = findViewById(R.id.videoSpinner);
        submitButton = findViewById(R.id.submitButton);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.video_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoSpinner.setAdapter(adapter);

        submitButton.setOnClickListener(v -> {
            String selectedVideo = videoSpinner.getSelectedItem().toString();
            int videoCount = getVideoCount(selectedVideo);

            Intent intent = new Intent(this, VideoPlayActivity.class);
            intent.putExtra("VIDEO_COUNT", videoCount);
            startActivity(intent);

            Toast.makeText(this, "You selected: " + selectedVideo + ", which corresponds to " + videoCount + " video(s)", Toast.LENGTH_LONG).show();
        });
    }

    private int getVideoCount(String selectedVideo) {
        if (selectedVideo.startsWith("Video ")) {
            try {
                int videoNumber = Integer.parseInt(selectedVideo.substring(6));
                if (videoNumber >= 1 && videoNumber <= 10) {
                    return videoNumber;
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public void goToVideoPlay(View view) {
        Intent intent = new Intent(this, VideoPlayActivity.class);
        startActivity(intent);
    }

    public void backToCalibration(View view) {
        Intent intent = new Intent(this, CalibrationActivity.class);
        startActivity(intent);
    }
}
