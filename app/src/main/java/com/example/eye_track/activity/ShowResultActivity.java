package com.example.eye_track.activity;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eye_track.R;
import com.example.eye_track.view.HeatmapView;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class ShowResultActivity extends AppCompatActivity {
    private static final Logger log = LoggerFactory.getLogger(ShowResultActivity.class);
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private HeatmapView heatmapView;
    private WebSocketClient webSocketClient;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_showresult);

        player=new SimpleExoPlayer.Builder(this).build();
        playerView=findViewById(R.id.playerView);
        playerView.setPlayer(player);
        heatmapView = findViewById(R.id.heatmapView);

        initializeWebSocket();
        runOnUiThread(()->playVideo());
    }

    private void initializeWebSocket() {
        new Thread(()->{
            try {
                webSocketClient = new WebSocketClient(new URI("ws://192.168.0.179:8080/")) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        send("request_coordinates");

                    }

                    @Override
                    public void onMessage(String message) {
                        // Here you might handle messages coming from the server if necessary
                        Map<String, Integer> newCoordinates = parseCoordinatesFromMessage(message);
                        runOnUiThread(() -> heatmapView.updateCoordinates(newCoordinates));
                    }

                    @Override
                    public void onError(Exception ex) {
                        //runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket error: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        //runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket closed: " + reason, Toast.LENGTH_SHORT).show());
                    }
                };
                webSocketClient.connect();
            } catch (Exception e) {
                Toast.makeText(this, "WebSocket setup error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }).start();

    }

    private void playVideo() {
        String videoUri = getIntent().getStringExtra("videoUrl");
        Log.i("URL",videoUri);
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUri));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        player.addListener(new ExoPlayer.Listener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(TAG, "Playback error: ", error);
            }
        });
    }

    private Map<String, Integer> parseCoordinatesFromMessage(String message) {
        Map<String, Integer> coordinates = new HashMap<>();
        try {
            JSONArray jsonArray = new JSONArray(message);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray coordArray = jsonArray.getJSONArray(i);
                String coord = coordArray.getDouble(0) + "," + coordArray.getDouble(1);
                if (coordinates.containsKey(coord)) {
                    coordinates.put(coord, coordinates.get(coord) + 1);
                } else {
                    coordinates.put(coord, 1);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return coordinates;
    }
}

