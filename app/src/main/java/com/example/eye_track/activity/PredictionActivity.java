package com.example.eye_track.activity;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.eye_track.R;
import com.example.eye_track.model.PredictionUser;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

//responsible for prediction step, including get and play video from backend, and capture images
// if want to just capture images without playing videos and previewing camera content, just deprive the video part in this activity

public class PredictionActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final Logger log = LoggerFactory.getLogger(PredictionActivity.class);

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private Button showResult;
    private WebSocketClient webSocketClient;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    private boolean isCapturing=true;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    public String videoUrl;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        // transfer to next step: show results
        showResult=findViewById(R.id.show_result);
        showResult.setOnClickListener(v -> {
            isCapturing=false;
            Intent intent = new Intent(PredictionActivity.this, ShowResultActivity.class);
            intent.putExtra("videoUrl",videoUrl);
            startActivity(intent);
        });

        if (ActivityCompat.checkSelfPermission(PredictionActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PredictionActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

        ////when starting this activity, requiring video url from server, and play it
        playerView=findViewById(R.id.playerView);
        URI uri;
        try {
            uri = new URI("ws://192.168.0.179:8080/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                Log.i("WebSocket", "Opened");
                // Request video URL
                webSocketClient.send("RequestVideoURL");
            }

            @Override
            public void onMessage(String message) {
                Log.i("WebSocket", "Message received: " + message);
                if (message.startsWith("VideoURL:")) {
                    videoUrl = message.substring("VideoURL:".length());
                    log.info("------------------------"+videoUrl);
                    runOnUiThread(() -> initializePlayer(videoUrl));
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.i("WebSocket", "Closed");
            }

            @Override
            public void onError(Exception ex) {
                Log.e("WebSocket", "Error", ex);
            }
        };
        webSocketClient.connect();
        initialize();

    }

    //use a specific thread to control camera and start capture pictures
    private void initialize() {
        startBackgroundThread();
        startCameraCapture();

    }

    //initialize player
    private void initializePlayer(String videoUrl) {
        player = new SimpleExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .build();
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

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    //start camera
    private void startCameraCapture() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1]; // 使用前置摄像头
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            imageReader = ImageReader.newInstance(320, 240, ImageFormat.JPEG, 10);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // 请求相机权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                return;
            }

            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //operations attach to camera
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    //allow imagereader can get the content of front camera
    private void createCameraCaptureSession() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.i("Capture","******start capture*******");
                    captureSession = session;
                    startImageCapture();//start to capture which starting front camera and make imagereader can get the content of this camera
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("CameraCapture", "Configuration change");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startImageCapture() {
        isCapturing = true;
        executorService = Executors.newScheduledThreadPool(4);
        executorService.scheduleWithFixedDelay(this::captureStillPicture, 0, 33, TimeUnit.MILLISECONDS); // 每秒30帧
    }

    //details about how to capture
    private void captureStillPicture() {
        if (cameraDevice == null) return;
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            captureSession.capture(builder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //get image info from imageReader
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if(isCapturing){
                Image image = reader.acquireNextImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    sendImage(bytes);
                    image.close();
                }
            }
        }
    };

    //send image to websocket
    private void sendImage(byte[] image) {
        Runnable sendTask = () -> {
            PredictionUser user = new PredictionUser("testUser1", image, 25, 1);
            Gson gson = new Gson();
            String json = gson.toJson(user);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.i("send","********start send*********");
                webSocketClient.send(jsonBytes);
            }
        };
        new Thread(sendTask).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isCapturing = false;
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        executorService.shutdown();
        stopBackgroundThread();
        webSocketClient.close();
        player.release();
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



}
