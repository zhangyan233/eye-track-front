package com.example.eye_track.activity;


import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.media.ImageReader;
import android.os.*;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.eye_track.R;
import com.google.common.util.concurrent.ListenableFuture;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private PreviewView previewView;
    private boolean isCapturing = false;
    private boolean isCameraOpen=false;
    private Button btnOpenCamera;
    private Button btnCapture;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private WebSocketClient webSocketClient;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService imageProcessingExecutor;
    private ExecutorService cameraExecutor=Executors.newSingleThreadExecutor();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.preview);
        btnOpenCamera= findViewById(R.id.button_open_camera);
        btnCapture = findViewById(R.id.button_capture);

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }



        //previewView.setSurfaceTextureListener(surfaceTextureListener);
        btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCameraOpen) {
                    startCamera();
                } else {
                    closeCamera();
                }
            }
        });

        btnCapture.setOnClickListener(v -> {
            if (isCapturing) {
                stopCapture();
            } else {
                startCapture();
            }
        });

        setupWebSocket();
    }

    private void setupWebSocket() {
        try {
            webSocketClient = new WebSocketClient(new URI("ws://10.0.2.2:8080/")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket Connected", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onMessage(String message) {
                    // Here you might handle messages coming from the server if necessary
                }

                @Override
                public void onError(Exception ex) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket error: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket closed: " + reason, Toast.LENGTH_SHORT).show());
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            Toast.makeText(this, "WebSocket setup error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        isCameraOpen=true;
        btnOpenCamera.setText("Close Camera");
        imageProcessingExecutor=Executors.newSingleThreadExecutor();
        cameraExecutor.execute(()->{
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build();

                    imageCapture = new ImageCapture.Builder().setTargetResolution(new Size(320,240))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
                } catch (Exception e) {
                    Log.e("CameraXApp", "Failed to bind camera preview: "  + e.getMessage());
                }
            }, ContextCompat.getMainExecutor(this));
        });

    }

    private void startCapture() {
        isCapturing=true;
        btnCapture.setText("Stop Capture");
        if(executorService.isShutdown()){
           executorService=Executors.newScheduledThreadPool(1);
        }
        executorService.scheduleWithFixedDelay(() -> {
            if (isCapturing && imageCapture != null) {
                imageCapture.takePicture(imageProcessingExecutor, new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        sendImageToServer(bytes);
                        image.close();
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        Log.e("CameraXApp", "Error capturing images: " + exception.getMessage());
                    }
                });
            }
        }, 0, 30, TimeUnit.MILLISECONDS);
    }

    private void sendImageToServer(byte[] imageData) {
        imageProcessingExecutor.execute(()->{
            String base64Image = Base64.encodeToString(imageData, Base64.DEFAULT);
            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(base64Image);
                Log.d("WebSocket", "Sending image data");
            }
        });

    }


    private void stopCapture() {
        isCapturing = false;
        btnCapture.setText("Start Capture");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // 立即关闭所有任务
        }

        if(imageProcessingExecutor!=null && !imageProcessingExecutor.isShutdown()){
            imageProcessingExecutor.shutdownNow();
        }

        closeCamera();
    }

    private void closeCamera() {
        isCameraOpen=false;
        btnOpenCamera.setText("Open Camera");
        cameraExecutor.execute(() -> {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider.unbindAll();
                    imageCapture = null;
                } catch (Exception e) {
                    Log.e("CameraXApp", "Failed to unbind camera: " + e.getMessage());
                }
            }, ContextCompat.getMainExecutor(this));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if(imageProcessingExecutor!=null){
            imageProcessingExecutor.shutdownNow();
        }
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        closeCamera();// 当不再需要时，关闭ExecutorService
    }

}
