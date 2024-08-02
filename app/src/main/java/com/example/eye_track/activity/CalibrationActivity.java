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
import androidx.core.app.ActivityCompat;

import com.example.eye_track.R;
import com.example.eye_track.model.CalibrationUser;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//responsible for calibration step, including open front camera, preview and capture images
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

        //Start background thread for camera
        startBackgroundThread();

        // Initialize WebSocket
        initializeWebSocket();

        // as entering this activity, open front camera
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

        //control button to start capture and stop capture
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

    //initialize websocket connection
    private void initializeWebSocket() {
        new Thread(()->{
            try {
                webSocketClient = new WebSocketClient(new URI("ws://192.168.0.179:8080/")) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        //runOnUiThread(() -> Toast.makeText(CalibrationActivity.this, "WebSocket Connected", Toast.LENGTH_SHORT).show());
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

    //introduce specific thread to run camera and capture images
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        imageHandlerThread = new HandlerThread("ImageHandlerThread");
        imageHandlerThread.start();
        imageHandler = new Handler(imageHandlerThread.getLooper());
    }

    //stop background threads
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

    // open front camera
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1]; // 1 indicates front camera

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
                return;
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //some operations toward front camera
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

    //determine which views can get information from front camera
    //surface: screen, make users can preview content from front camera
    //imagereader: capture images which means read content from imagereader
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(320, 240);
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            imageReader = ImageReader.newInstance(320, 240, ImageFormat.JPEG, 10);
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

    // start capture
    private void startImageCapture() {
        isCapturing = true;
        btnCapture.setText("Stop Capture");
        captureExecutorService = Executors.newScheduledThreadPool(4);
        captureExecutorService.scheduleWithFixedDelay(this::captureStillPicture, 0, 33, TimeUnit.MILLISECONDS); // 每秒30帧
    }

    //stop capture
    private void stopImageCapture() {
        isCapturing = false;
        btnCapture.setText("Start Capture");
        if (captureExecutorService != null && !captureExecutorService.isShutdown()) {
            captureExecutorService.shutdownNow();
        }
    }

    //detailed operations while capturing images
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
                    Log.i("capture","start capturing");
                }
            }, imageHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //get image from imageReader
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

    // send image to websocket server
    private void sendImage(byte[] image) {
        Runnable sendTask = () -> {
            CalibrationUser user = new CalibrationUser("testUser1", image, 25, 1, new int[]{200, 256});
            Gson gson = new Gson();
            String json = gson.toJson(user);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            if (webSocketClient != null && webSocketClient.isOpen()) {
                Log.i("send","start sending");
                webSocketClient.send(jsonBytes);
            }
        };
        new Thread(sendTask).start();
    }

    //get privilege to use camera
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
