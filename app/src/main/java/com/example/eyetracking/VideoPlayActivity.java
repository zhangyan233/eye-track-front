package com.example.eyetracking;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.VideoView;

public class VideoPlayActivity extends Activity {

    private VideoView videoView;
    private int videoCount;
    private int currentVideoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.videoplay);

        videoView = findViewById(R.id.videoView);
        Intent intent = getIntent();
        videoCount = intent.getIntExtra("VIDEO_COUNT", 1);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.test);
        videoView.setVideoURI(uri);
        videoView.requestFocus();
        videoView.start();

        videoView.setOnCompletionListener(mp -> {
            currentVideoIndex++;
            if (currentVideoIndex < videoCount) {
                videoView.start();
            } else {
                goToOutput();
            }
        });
    }

    private void goToOutput() {
        Intent intent = new Intent(this, Exit.class);
        startActivity(intent);
        finish();
    }
//
//    public void backToVideoChoose(View view) {
//        Intent intent = new Intent(this, VideoChooseActivity.class);
//        startActivity(intent);
//        finish();
//    }
}
