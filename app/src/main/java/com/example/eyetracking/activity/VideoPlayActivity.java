package com.example.eyetracking.activity;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.example.eyetracking.model.PredictionUser;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.example.eyetracking.R;

import org.slf4j.LoggerFactory;


public class VideoPlayActivity extends Activity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

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
    private List<String> videoUrls = new ArrayList<>();
    public int[] faceDetectModes;
    private int age;
    private int gender;
    SharedPreferences sharedPreferences ;

    private int videoCount;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.videoplay);
        sharedPreferences=getSharedPreferences("MySharedPref", MODE_PRIVATE);
        age=sharedPreferences.getInt("age",20);
        gender=sharedPreferences.getInt("gender",1);
        videoCount=sharedPreferences.getInt("videoCount",1);

        if (ActivityCompat.checkSelfPermission(VideoPlayActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(VideoPlayActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

        playerView=findViewById(R.id.playerView);
        URI uri;
        try {
            uri = new URI("ws://192.168.0.179:8080/");
            //uri =new URI("ws://172.20.10.4:8000/ws/dispatcher/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                Log.i("WebSocket", "Opened");
                Log.i("videoCount",""+videoCount);
                // Request video URL
                webSocketClient.send("RequestVideoURL:"+videoCount);
            }

            @Override
            public void onMessage(String message) {
                Log.i("WebSocket", "Message received: " + message);
                try {
                     //解析 JSON 数据
                    JSONObject jsonResponse = new JSONObject(message);
                    JSONArray urlsArray = jsonResponse.getJSONArray("video_urls");

                    // 清空旧的 URL 列表
                    videoUrls.clear();

                    // 将解析的 URL 添加到列表中
                    for (int i = 0; i < urlsArray.length(); i++) {
                        videoUrls.add(urlsArray.getString(i).trim());
                    }
//                    String[] urls = message.substring("VideoURLs:".length()).split(",");
//                    for (String url : urls) {
//                        videoUrls.add(url.trim());
//                    }
//                    runOnUiThread(() -> initializePlayer(videoUrls));

                    // 在主线程更新 UI
                    runOnUiThread(() -> initializePlayer(videoUrls));
                } catch (JSONException e) {
                    Log.e("WebSocket", "Error parsing JSON response", e);
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
    }

    private void initializePlayer(List<String> videoUrls) {
        player = new SimpleExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        for (String videoUrl : videoUrls) {
            MediaItem mediaItem = new MediaItem.Builder().setUri(videoUrl).setMimeType(MimeTypes.VIDEO_MP4).build();
            player.addMediaItem(mediaItem);
        }

        player.prepare();
        player.play();

        player.addListener(new ExoPlayer.Listener() {
            private boolean isCaptured=false;
            @Override
            //Automatic jump to exit page.
            public void onPlaybackStateChanged(int playbackState) {
                if(playbackState==ExoPlayer.STATE_READY&&player.getCurrentWindowIndex()==0&&!isCaptured){
                    startCameraCapture();
                    isCaptured=true;
                }

                if (playbackState == ExoPlayer.STATE_ENDED) {
                    stopImageCapture();
                    new Handler().postDelayed(() -> {
                        Intent intent = new Intent(VideoPlayActivity.this, Exit.class);
                        startActivity(intent);
                        finish();
                    }, 2000); // 2 seconds delay

                }
            }


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
            faceDetectModes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);

            imageReader = ImageReader.newInstance(640, 360, ImageFormat.JPEG, 10);
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

    private void stopImageCapture() {
        isCapturing = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void captureStillPicture() {
        if (!isCapturing||cameraDevice == null) return;
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 3); // 设置曝光补偿
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 800);

            if (faceDetectModes != null && faceDetectModes.length > 0) {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
            }

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
                    runOnUiThread(()->{
                        int videoIndex=player.getCurrentWindowIndex();
                        long relativeTime=player.getCurrentPosition();
                        sendImage(bytes,videoIndex,relativeTime);
                    });
                    image.close();
                }
            }
        }
    };

    //send image to websocket
    private void sendImage(byte[] image,int videoIndex,long relativeTime) {
        Runnable sendTask = () -> {
            String base64Image= Base64.encodeToString(image,Base64.DEFAULT);
            PredictionUser user = new PredictionUser(videoUrls,base64Image,relativeTime,videoIndex);
            Gson gson = new Gson();
            String json = gson.toJson(user);

            String prefixedJson = "P" + json;
            byte[] jsonBytes = prefixedJson.getBytes(StandardCharsets.UTF_8);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.i("send","********start send*********");
                webSocketClient.send(prefixedJson);
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
        stopImageCapture();
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
