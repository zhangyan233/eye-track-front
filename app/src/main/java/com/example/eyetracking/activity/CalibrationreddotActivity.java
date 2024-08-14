

package com.example.eyetracking.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.eyetracking.R;
import com.example.eyetracking.model.CalibrationUser;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eyetracking.R;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CalibrationreddotActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private WebSocketClient webSocketClient;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    private boolean isCapturing=true;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);
    public int[] faceDetectModes;
    //red dot
    private static final int DOT_DISPLAY_DURATION = 4000; // 4秒
    private View[] redDots;
    private int currentDotIndex = 0;
    private int age;
    private int gender;
    SharedPreferences sharedPreferences ;
    private int[] coordinates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibrationreddot);
        sharedPreferences=getSharedPreferences("MySharedPref", MODE_PRIVATE);
        age=sharedPreferences.getInt("age",20);
        gender=sharedPreferences.getInt("gender",1);


        if (ActivityCompat.checkSelfPermission(CalibrationreddotActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CalibrationreddotActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

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
            }

            @Override
            public void onMessage(String message) {
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

        //Inital reddots and dots sequence
        initializeRedDots();
        startDotSequence();
    }

    private void initialize() {
        startBackgroundThread();
        startCameraCapture();
    }
    //Initial Reddots
    private void initializeRedDots() {
        redDots = new View[]{
                findViewById(R.id.dot_top_left),
                findViewById(R.id.dot_top_center),
                findViewById(R.id.dot_top_right),
                findViewById(R.id.dot_center_right),
                findViewById(R.id.dot_center),
                findViewById(R.id.dot_center_left),
                findViewById(R.id.dot_bottom_left),
                findViewById(R.id.dot_bottom_center),
                findViewById(R.id.dot_bottom_right)
        };


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
                    Log.i("Capture", "******start capture*******");
                    captureSession = session;
                    //startImageCapture();//start to capture which starting front camera and make imagereader can get the content of this camera
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

    //details about how to capture
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
            CalibrationUser user = new CalibrationUser("testUser1", image, age, gender,
                    coordinates);
            Log.i("coordinates","x: "+coordinates[0]+" y: "+coordinates[1]);
            Gson gson = new Gson();
            String json = gson.toJson(user);

            // Adding a prefix identifier to the message
            String prefixedJson = "C" + json;
            byte[] jsonBytes = prefixedJson.getBytes(StandardCharsets.UTF_8);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.i("send","start sending");
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
        stopImageCapture();
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

    public void startRecording(View view) {
        // Your recording logic here
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
    }



    //reddot

    private void startDotSequence() {
        if (currentDotIndex < redDots.length) {
            redDots[currentDotIndex].setVisibility(View.VISIBLE);
            coordinates=new int[2];
            redDots[currentDotIndex].getLocationOnScreen(coordinates);
            // Start image capture when the red dot is displayed
            startImageCapture();


            new Handler().postDelayed(() -> {
                redDots[currentDotIndex].setVisibility(View.INVISIBLE);
                stopImageCapture();
                currentDotIndex++;
                startDotSequence();
            }, DOT_DISPLAY_DURATION);
        } else {
            goToVideoChoose();
        }
    }
    //Auto jump into VideoChoose Activity
    private void goToVideoChoose() {
        Intent intent = new Intent(CalibrationreddotActivity.this, VideoChooseActivity.class);
        startActivity(intent);
        finish();
    }
}