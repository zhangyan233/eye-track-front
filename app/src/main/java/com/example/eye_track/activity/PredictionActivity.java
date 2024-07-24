package com.example.eye_track.activity;

import static android.content.ContentValues.TAG;

import android.Manifest;
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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.eye_track.R;
import com.example.eye_track.model.User;
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
import android.widget.TextView;
import android.widget.Toast;

public class PredictionActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final Logger log = LoggerFactory.getLogger(PredictionActivity.class);

    private PlayerView playerView;
    private TextureView textureView;
    private SimpleExoPlayer player;
    private WebSocketClient webSocketClient;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    private boolean isCapturing;
        private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    private final BlockingQueue<byte[]> imageQueue = new LinkedBlockingQueue<byte[]>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        if (ActivityCompat.checkSelfPermission(PredictionActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PredictionActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

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
                    String videoUrl = message.substring("VideoURL:".length());
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

    private void initialize() {
        startBackgroundThread();
        startCameraCapture();

    }

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

    private void startCameraCapture() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1]; // 使用前置摄像头
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
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

    private void createCameraCaptureSession() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    startImageCapture();
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
        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleWithFixedDelay(this::captureStillPicture, 0, 33, TimeUnit.MILLISECONDS); // 每秒30帧
    }

    private void captureStillPicture() {
        if (cameraDevice == null) return;
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            captureSession.capture(builder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                sendImage(bytes);
                image.close();
            }
        }
    };

    private void sendImage(byte[] image) {
        Runnable sendTask = () -> {
            User user = new User("testUser1", image, 25, 1);
            Gson gson = new Gson();
            String json = gson.toJson(user);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.i("send","开始发送了");
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
