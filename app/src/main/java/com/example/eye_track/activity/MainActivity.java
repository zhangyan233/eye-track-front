package com.example.eye_track.activity;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.eye_track.R;
import com.example.eye_track.utils.FFmpegJNI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    private TextureView textureView;
    private boolean isCapturing = false;
    private boolean isCameraOpen=false;
    private Handler captureHandler;
    private Runnable captureRunnable;
    private Button btnOpenCamera;
    private Button btnCapture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private FFmpegJNI ffmpegJNI=new FFmpegJNI();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        btnOpenCamera= findViewById(R.id.button_open_camera);
        btnCapture = findViewById(R.id.button_capture);


        textureView.setSurfaceTextureListener(surfaceTextureListener);
        btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isCameraOpen) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                        return;
                    }
                    openCamera();
                    btnOpenCamera.setText("Close Camera");
                    isCameraOpen = true;
                } else {
                    closeCamera();
                    btnOpenCamera.setText("Open Camera");
                    isCameraOpen = false;
                }
            }
        });

        btnCapture.setOnClickListener(v -> {
            if (isCapturing) {
                stopCapture();
                btnCapture.setText("Start Capture");
            } else {
                startCapture();
                btnCapture.setText("Stop Capture");
            }
        });

//        ActivityCompat.requestPermissions(this, new String[]{
//                Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
//        }, CAMERA_PERMISSION_REQUEST_CODE);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                throw new RuntimeException("No front-facing camera found.");
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA
                }, CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }

            startBackgroundThread();
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
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

    private void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(360,240);
            Surface surface = new Surface(texture);

            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            imageReader = ImageReader.newInstance(360,240, ImageFormat.JPEG, 10);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

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
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Failed to start camera preview", Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCapture() {
        isCapturing = true;
        captureHandler = new Handler();
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCapturing) return;
                captureStillPicture();
                captureHandler.postDelayed(this, 30); // 每30毫秒拍照一次
            }
        };
        captureHandler.post(captureRunnable);
    }

    private void stopCapture() {
        isCapturing = false;
        if (captureHandler != null) {
            captureHandler.removeCallbacks(captureRunnable);
        }
    }

    private void captureStillPicture() {
        try {
            if (cameraDevice == null) return;

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            // 设置自动对焦模式
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 80); // 设置JPEG压缩质量

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            executor.execute(() -> {
                Image image = null;
                FileOutputStream output = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        //send to back side
                        //sendImageToServer(bytes);

                        File file = createImageFile(); // 确保此方法不依赖于主线程资源
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    if (output != null) {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    };


    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void sendImageToServer(byte[] bytes) {
        File tempFile = saveImageToTempFile(bytes);
        if (tempFile != null) {
            String rtspUrl = "rtsp://yourserver.com/stream";  // 替换为你的RTSP服务器地址
            String[] ffmpegCommand = {
                    "ffmpeg",
                    "-re",
                    "-i", tempFile.getAbsolutePath(),
                    "-vcodec", "libx264",
                    "-f", "rtsp",
                    rtspUrl
            };
            executor.execute(() -> {
                int result = ffmpegJNI.runFFmpegCommand(ffmpegCommand);
                if (result == 0) {
                    Log.i(TAG, "RTSP stream successful");
                } else {
                    Log.e(TAG, "RTSP stream failed");
                }
                tempFile.delete();  // 删除临时文件
            });
        }
    }

    private File saveImageToTempFile(byte[] bytes) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("image", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(bytes);
                fos.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            tempFile = null;
        }
        return tempFile;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown(); // 当不再需要时，关闭ExecutorService
    }

    @Override
    protected void onPause() {
        super.onPause();
        closePreview();
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    private void closeCamera() {
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
        closePreview();
    }

    private void closePreview() {
        if (captureSession != null) {
            try {
                // 首先停止正在进行的会话
                captureSession.stopRepeating();
                captureSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } finally {
                // 关闭会话
                captureSession.close();
                captureSession = null;
            }
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        // 移除TextureView
        ((ViewGroup) textureView.getParent()).removeView(textureView);

// 创建一个新的TextureView并重新添加到布局中
        textureView = new TextureView(this);
        textureView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ((ViewGroup) findViewById(R.id.main)).addView(textureView); // 假设你的父布局ID为rootLayout

    }

}
