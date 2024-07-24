package com.example.eye_track.activity;


import android.Manifest;
import android.annotation.SuppressLint;
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
import android.os.*;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.example.eye_track.model.User;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CalibrationActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private TextureView textureView;
    private Button btnCapture;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ScheduledExecutorService captureExecutorService;
    private boolean isCapturing = false;
    private WebSocketClient webSocketClient;
    private Handler imageHandler;
    private HandlerThread imageHandlerThread;

    private Handler previewHandler;
    private HandlerThread previewThread;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        textureView = findViewById(R.id.texture_view);
        btnCapture = findViewById(R.id.button_capture);

        //webExecutors=Executors.newSingleThreadExecutor();
        startBackgroundThread();


        // Initialize WebSocket
        initializeWebSocket();

        // Start background thread for camera

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                backgroundHandler.post(()->openCamera());
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCapturing) {
                    stopImageCapture();
                } else {
                    startImageCapture();
                }
            }
        });
    }

        private void initializeWebSocket() {
            new Thread(()->{
                try {
                    webSocketClient = new WebSocketClient(new URI("ws://192.168.0.179:8080/")) {
                        @Override
                        public void onOpen(ServerHandshake handshakedata) {
                            runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket Connected", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onMessage(String message) {
                            // Here you might handle messages coming from the server if necessary
                        }

                        @Override
                        public void onError(Exception ex) {
                            runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket error: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote) {
                            runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket closed: " + reason, Toast.LENGTH_SHORT).show());
                        }
                    };
                    webSocketClient.connect();
                } catch (Exception e) {
                    Toast.makeText(this, "WebSocket setup error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }).start();

    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        imageHandlerThread = new HandlerThread("ImageHandlerThread");
        imageHandlerThread.start();
        imageHandler = new Handler(imageHandlerThread.getLooper());


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

        if (imageHandlerThread != null) {
            imageHandlerThread.quitSafely();
            try {
                imageHandlerThread.join();
                imageHandlerThread = null;
                imageHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1]; // 1 indicates front camera
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            Size previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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
            createCameraPreviewSession();
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

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(320, 180);
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            imageReader = ImageReader.newInstance(320, 180, ImageFormat.JPEG, 10);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, imageHandler);

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startImageCapture() {
        isCapturing = true;
        btnCapture.setText("Stop Capture");
        captureExecutorService = Executors.newScheduledThreadPool(4);
        captureExecutorService.scheduleWithFixedDelay(this::captureStillPicture, 0, 33, TimeUnit.MILLISECONDS); // 每秒30帧
    }

    private void stopImageCapture() {
        isCapturing = false;
        btnCapture.setText("Start Capture");
        if (captureExecutorService != null && !captureExecutorService.isShutdown()) {
            captureExecutorService.shutdownNow();
        }
    }

    private void captureStillPicture() {
        if (cameraDevice == null || !isCapturing) return;

        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
             builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            captureSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }
            }, imageHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                sendImage(bytes);
            } finally {
                if (image != null) {
                    image.close();
                }
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopImageCapture();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        stopBackgroundThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            backgroundHandler.post(() -> openCamera());
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    backgroundHandler.post(() -> openCamera());
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }


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
        if (captureExecutorService != null) {
            captureExecutorService.shutdown();
        }
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        stopBackgroundThread();
    }


}
