package com.yf.camera;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.effects.OverlayEffect;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK_LOGO = 71001;

    private PreviewView previewView;
    private TextView tvTimePreview;
    private TextView tvLocationPreview;
    private TextView tvCoordPreview;
    private TextView tvProjectPreview;
    private ImageView ivLogo;
    private ImageButton btnAlbum;
    private Button btnPhoto, btnVideo, btnZoomReset;
    private ImageButton btnSettings;
    private ImageButton btnFlash;
    private ImageButton btnSwitchCamera;
    private TextView tvZoomRatio;
    private LinearLayout zoomPanel;
    private LinearLayout watermarkContainer;
    private LinearLayout locationTextArea;
    private View focusIndicator;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ScaleGestureDetector previewScaleGestureDetector;
    private boolean previewGestureHasScale = false;
    private boolean previewPointerMode = false;
    private float previewDownX = 0f;
    private float previewDownY = 0f;
    private final Runnable hideZoomPanelRunnable = new Runnable() {
        @Override public void run() {
            hideZoomPanel();
        }
    };
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    private final WatermarkData watermarkData = new WatermarkData();
    private final WatermarkPainter watermarkPainter = new WatermarkPainter();
    private TimeTicker timeTicker;
    private YFLocationManager locationManager;
    private HandlerThread overlayThread;
    private Handler overlayHandler;
    private OverlayEffect overlayEffect;
    private boolean originalSize = false;
    private Bitmap logoBitmap;
    private boolean torchEnabled = false;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private MediaActionSound mediaActionSound;
    private SoundPool soundPool;
    private int soundUiLow;
    private int soundUiHigh;
    private int toneSequence = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        // 拍摄页预览使用 FILL_CENTER，避免 FIT_CENTER 在竖屏控件比例不完全匹配时出现黑边。
        // 注意：这里只影响屏幕预览显示，不重新启用 ViewPort 裁剪；照片/视频输出仍保持完整常见画幅。
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        tvTimePreview = findViewById(R.id.tvTimePreview);
        tvLocationPreview = findViewById(R.id.tvLocationPreview);
        tvCoordPreview = findViewById(R.id.tvCoordPreview);
        tvProjectPreview = findViewById(R.id.tvProjectPreview);
        watermarkContainer = findViewById(R.id.watermarkContainer);
        locationTextArea = findViewById(R.id.locationTextArea);
        focusIndicator = findViewById(R.id.focusIndicator);
        ivLogo = findViewById(R.id.ivLogo);
        btnAlbum = findViewById(R.id.btnAlbum);
        btnPhoto = findViewById(R.id.btnPhoto);
        btnVideo = findViewById(R.id.btnVideo);
        btnSettings = findViewById(R.id.btnSettings);
        btnFlash = findViewById(R.id.btnFlash);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        tvZoomRatio = findViewById(R.id.tvZoomRatio);
        zoomPanel = findViewById(R.id.zoomPanel);
        btnZoomReset = findViewById(R.id.btnZoomReset);
        cameraExecutor = Executors.newSingleThreadExecutor();
        initCameraSounds();

        loadWatermarkSettingsFromPrefs();
        disableManualTimeOverride();
        loadLogoFromPrefs();
        updateLatestAlbumThumbnail();

        // 新版拍摄页只展示自动时间和自动定位，不再提供点击修改时间/定位入口。
        disableWatermarkTextManualClick();
        btnAlbum.setOnClickListener(v -> startActivity(new Intent(this, AlbumActivity.class)));
        btnPhoto.setOnClickListener(v -> takePhoto());
        btnVideo.setOnClickListener(v -> toggleRecord());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnSwitchCamera.setOnClickListener(v -> switchCameraLens());
        if (btnZoomReset != null) {
            btnZoomReset.setOnClickListener(v -> resetZoomToOne());
        }
        updateZoomIndicator(1f);
        hideZoomPanel();
        updateFlashButtonState(false);
        updateSwitchCameraButtonState();
        setupPreviewPinchZoom();

        PermissionHelper.requestCameraPermissions(this, () -> {
            startClockAndLocation();
            startCamera();
        });
    }

    private void startClockAndLocation() {
        updatePreviewWatermark();
        timeTicker = new TimeTicker(mainHandler, watermarkData, this::updatePreviewWatermark);
        timeTicker.start();
        locationManager = new YFLocationManager(this, mainHandler, watermarkData, this::updatePreviewWatermark);
        locationManager.start();
    }

    private void disableWatermarkTextManualClick() {
        tvTimePreview.setOnClickListener(null);
        tvTimePreview.setClickable(false);
        tvTimePreview.setFocusable(false);

        locationTextArea.setOnClickListener(null);
        locationTextArea.setClickable(false);
        locationTextArea.setFocusable(false);

        tvLocationPreview.setOnClickListener(null);
        tvLocationPreview.setClickable(false);
        tvLocationPreview.setFocusable(false);

        tvCoordPreview.setOnClickListener(null);
        tvCoordPreview.setClickable(false);
        tvCoordPreview.setFocusable(false);
    }


    private void initCameraSounds() {
        try {
            mediaActionSound = new MediaActionSound();
            mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
        } catch (Throwable ignored) {
            mediaActionSound = null;
        }

        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(attrs)
                    .build();
            soundUiLow = soundPool.load(this, R.raw.record_tone_low, 1);
            soundUiHigh = soundPool.load(this, R.raw.record_tone_high, 1);
        } catch (Throwable ignored) {
            soundPool = null;
            soundUiLow = 0;
            soundUiHigh = 0;
        }
    }

    private void playPhotoShutterSound() {
        try {
            if (mediaActionSound != null) {
                mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
            }
        } catch (Throwable ignored) {}
    }

    private void playRecordStartSound() {
        // 录像开始：更现代、更柔和的双音，间隔 200ms。
        playSoundPair(soundUiLow, soundUiHigh);
    }

    private void playRecordStopSound() {
        // 录像结束：反向双音，间隔 200ms，方便区分开始/结束。
        playSoundPair(soundUiHigh, soundUiLow);
    }

    private void playSoundPair(int firstSound, int secondSound) {
        if (soundPool == null || firstSound == 0 || secondSound == 0) return;
        final int seq = ++toneSequence;
        try {
            soundPool.play(firstSound, 0.55f, 0.55f, 1, 0, 1.0f);
            mainHandler.postDelayed(() -> {
                if (seq != toneSequence) return;
                try {
                    if (soundPool != null) {
                        soundPool.play(secondSound, 0.55f, 0.55f, 1, 0, 1.0f);
                    }
                } catch (Throwable ignored) {}
            }, 200);
        } catch (Throwable ignored) {}
    }

    private void updatePreviewWatermark() {
        tvTimePreview.setText(watermarkData.getTimeLine());
        tvLocationPreview.setText(watermarkData.getLocationLine());
        tvCoordPreview.setText(watermarkData.getCoordLine());
        String projectInfo = watermarkData.getProjectInfoPreview();
        tvProjectPreview.setText(projectInfo);
        tvProjectPreview.setVisibility(projectInfo.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindUseCases();
            } catch (Exception e) {
                Toast.makeText(this, "相机启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder()
                // 统一使用常见 16:9 画幅。手机竖屏保存时就是 9:16。
                // 预览显示用 FILL_CENTER 去掉黑边；输出不使用 ViewPort 裁剪。
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                // 和视频/预览保持 16:9，竖屏输出即 9:16。
                // 这里只指定比例，不使用 ViewPort 裁剪，避免成片画面被切掉。
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(92)
                .build();

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);
        if (previewView.getDisplay() != null) {
            int rotation = previewView.getDisplay().getRotation();
            imageCapture.setTargetRotation(rotation);
            videoCapture.setTargetRotation(rotation);
        }

        if (overlayThread != null) overlayThread.quitSafely();
        overlayThread = new HandlerThread("yf-camera-overlay");
        overlayThread.start();
        overlayHandler = new Handler(overlayThread.getLooper());

        // 预览界面已经有原生 UI 水印，不再把 CameraX Overlay 画到 PREVIEW，
        // 否则竖屏设备上可能出现一份被旋转的重复水印。Overlay 只用于录像成片。
        int targets = CameraEffect.VIDEO_CAPTURE;
        overlayEffect = new OverlayEffect(targets, 0, overlayHandler, throwable -> runOnUiThread(() ->
                Toast.makeText(this, "水印渲染异常: " + throwable.getMessage(), Toast.LENGTH_SHORT).show()
        ));
        overlayEffect.setOnDrawListener(frame -> {
            Canvas canvas = frame.getOverlayCanvas();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            watermarkPainter.draw(canvas, watermarkData.buildText());
            return true;
        });

        UseCaseGroup.Builder groupBuilder = new UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .addUseCase(videoCapture)
                .addEffect(overlayEffect);

        // 重要：不要再 setViewPort(previewView.getViewPort())。
        // ViewPort 会按预览控件的可见区域去裁切 ImageCapture/VideoCapture，
        // 在顶部 headerBar、底部按钮栏、水印区域不断调整后，部分机型会出现成片画面被裁剪的问题。
        // 现在改为：预览/拍照固定常见 16:9；PreviewView 用 FILL_CENTER 去掉黑边；
        // 成片保持完整画幅，只在完整画面上绘制水印，不靠 ViewPort 裁剪画幅。

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        camera = cameraProvider.bindToLifecycle(this, selector, groupBuilder.build());
        torchEnabled = false;
        updateFlashButtonState(false);
        updateSwitchCameraButtonState();
        camera.getCameraInfo().getZoomState().observe(this, state -> {
            if (state != null) updateZoomIndicator(state.getZoomRatio());
        });
    }


    private void toggleFlash() {
        if (camera == null) {
            Toast.makeText(this, "相机未启动", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, "当前摄像头不支持闪光灯", Toast.LENGTH_SHORT).show();
            updateFlashButtonState(false);
            return;
        }
        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        updateFlashButtonState(torchEnabled);
        Toast.makeText(this, torchEnabled ? "闪光灯已开启" : "闪光灯已关闭", Toast.LENGTH_SHORT).show();
    }

    private void updateFlashButtonState(boolean enabled) {
        if (btnFlash == null) return;
        boolean hasFlash = camera != null && camera.getCameraInfo().hasFlashUnit();
        torchEnabled = enabled && hasFlash;
        btnFlash.setEnabled(hasFlash);
        btnFlash.setAlpha(hasFlash ? (torchEnabled ? 1.0f : 0.62f) : 0.25f);
        btnFlash.setSelected(torchEnabled);
        btnFlash.setContentDescription(torchEnabled ? "关闭闪光灯" : "开启闪光灯");
    }


    private void switchCameraLens() {
        if (cameraProvider == null) {
            Toast.makeText(this, "相机未启动", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeRecording != null) {
            Toast.makeText(this, "录像中不能切换镜头", Toast.LENGTH_SHORT).show();
            return;
        }

        int targetFacing = lensFacing == CameraSelector.LENS_FACING_BACK
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;

        if (!hasCameraLens(targetFacing)) {
            Toast.makeText(this, targetFacing == CameraSelector.LENS_FACING_FRONT
                    ? "未检测到前置摄像头"
                    : "未检测到后置摄像头", Toast.LENGTH_SHORT).show();
            updateSwitchCameraButtonState();
            return;
        }

        torchEnabled = false;
        lensFacing = targetFacing;
        bindUseCases();
        hideZoomPanel();
        Toast.makeText(this, lensFacing == CameraSelector.LENS_FACING_FRONT
                ? "已切换到前置镜头"
                : "已切换到后置镜头", Toast.LENGTH_SHORT).show();
    }

    private boolean hasCameraLens(int facing) {
        if (cameraProvider == null) return false;
        try {
            CameraSelector selector = new CameraSelector.Builder()
                    .requireLensFacing(facing)
                    .build();
            return cameraProvider.hasCamera(selector);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateSwitchCameraButtonState() {
        if (btnSwitchCamera == null) return;
        boolean canSwitch = cameraProvider != null
                && hasCameraLens(CameraSelector.LENS_FACING_BACK)
                && hasCameraLens(CameraSelector.LENS_FACING_FRONT);
        btnSwitchCamera.setEnabled(canSwitch);
        btnSwitchCamera.setAlpha(canSwitch ? 0.88f : 0.30f);
        btnSwitchCamera.setContentDescription(lensFacing == CameraSelector.LENS_FACING_BACK
                ? "切换到前置镜头"
                : "切换到后置镜头");
    }

    private void setupPreviewPinchZoom() {
        previewScaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                previewGestureHasScale = true;
                previewPointerMode = true;
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (camera == null) return false;
                previewGestureHasScale = true;
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                if (zoomState == null) return false;
                float current = zoomState.getZoomRatio();
                float target = current * (float) Math.pow(detector.getScaleFactor(), 2.6f);
                target = Math.max(zoomState.getMinZoomRatio(), Math.min(target, zoomState.getMaxZoomRatio()));
                camera.getCameraControl().setZoomRatio(target);
                updateZoomIndicator(target);
                if (Math.abs(target - 1f) > 0.03f) {
                    showZoomPanelTemporarily();
                } else {
                    hideZoomPanel();
                }
                return true;
            }
        });
        previewView.setOnTouchListener((v, event) -> {
            if (previewScaleGestureDetector != null) {
                previewScaleGestureDetector.onTouchEvent(event);
            }

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                previewDownX = event.getX();
                previewDownY = event.getY();
                previewGestureHasScale = false;
                previewPointerMode = false;
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                previewPointerMode = true;
            } else if (action == MotionEvent.ACTION_UP) {
                float dx = event.getX() - previewDownX;
                float dy = event.getY() - previewDownY;
                float moveLimit = dp(16);
                boolean isSingleTap = !previewPointerMode
                        && !previewGestureHasScale
                        && Math.abs(dx) < moveLimit
                        && Math.abs(dy) < moveLimit;
                if (isSingleTap) {
                    focusAt(event.getX(), event.getY());
                }
                previewPointerMode = false;
                previewGestureHasScale = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                previewPointerMode = false;
                previewGestureHasScale = false;
            }
            return true;
        });
    }

    private void focusAt(float x, float y) {
        if (camera == null || previewView == null) return;
        showFocusIndicator(x, y);
        try {
            MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y);
            FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                    FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build();
            camera.getCameraControl().startFocusAndMetering(action);
        } catch (Throwable t) {
            Toast.makeText(this, "对焦失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFocusIndicator(float x, float y) {
        if (focusIndicator == null) return;
        int size = dp(72);
        focusIndicator.setVisibility(View.VISIBLE);
        // event 坐标是相对于 PreviewView 的；对焦框是 rootMain 的子控件，
        // 需要叠加 PreviewView 在父布局中的偏移量，否则会出现点击位置和白框位置不一致。
        float rootX = previewView.getX() + x;
        float rootY = previewView.getY() + y;
        focusIndicator.setX(rootX - size / 2f);
        focusIndicator.setY(rootY - size / 2f);
        focusIndicator.setScaleX(1.35f);
        focusIndicator.setScaleY(1.35f);
        focusIndicator.setAlpha(1f);
        focusIndicator.animate().cancel();
        focusIndicator.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0.95f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> focusIndicator.animate()
                        .alpha(0f)
                        .setStartDelay(650)
                        .setDuration(220)
                        .withEndAction(() -> focusIndicator.setVisibility(View.GONE))
                        .start())
                .start();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void resetZoomToOne() {
        if (camera == null) return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        float target = 1f;
        if (zoomState != null) {
            target = Math.max(zoomState.getMinZoomRatio(), Math.min(1f, zoomState.getMaxZoomRatio()));
        }
        camera.getCameraControl().setZoomRatio(target);
        updateZoomIndicator(target);
        hideZoomPanel();
        Toast.makeText(this, "已恢复 1x 变焦", Toast.LENGTH_SHORT).show();
    }

    private void updateZoomIndicator(float ratio) {
        if (tvZoomRatio == null) return;
        if (Float.isNaN(ratio) || Float.isInfinite(ratio) || ratio <= 0f) ratio = 1f;
        tvZoomRatio.setText(String.format(Locale.CHINA, "%.1fx", ratio));
    }

    private void showZoomPanelTemporarily() {
        if (zoomPanel == null) return;
        mainHandler.removeCallbacks(hideZoomPanelRunnable);
        toneSequence++;
        if (zoomPanel.getVisibility() != View.VISIBLE) {
            zoomPanel.setAlpha(0f);
            zoomPanel.setVisibility(View.VISIBLE);
            zoomPanel.animate().cancel();
            zoomPanel.animate().alpha(1f).setDuration(140).start();
        } else {
            zoomPanel.animate().cancel();
            zoomPanel.setAlpha(1f);
        }
        mainHandler.postDelayed(hideZoomPanelRunnable, 3000);
    }

    private void hideZoomPanel() {
        if (zoomPanel == null) return;
        mainHandler.removeCallbacks(hideZoomPanelRunnable);
        toneSequence++;
        if (zoomPanel.getVisibility() != View.VISIBLE) return;
        zoomPanel.animate().cancel();
        zoomPanel.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> {
                    if (zoomPanel != null) {
                        zoomPanel.setVisibility(View.GONE);
                        zoomPanel.setAlpha(1f);
                    }
                })
                .start();
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        playPhotoShutterSound();
        File tempFile = new File(getCacheDir(), "yf_temp_photo.jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(tempFile).build();
        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                try {
                    Uri dst = MediaSaveHelper.createImageUri(MainActivity.this);
                    if (dst == null) throw new IllegalStateException("创建图片文件失败");
                    PhotoWatermarkUtil.addWatermarkAndSave(MainActivity.this, tempFile, dst, watermarkData.buildText(), originalSize, logoBitmap);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "图片已保存到 DCIM/YFCamera", Toast.LENGTH_SHORT).show();
                        updateLatestAlbumThumbnail();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "拍照保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } finally { tempFile.delete(); }
            }
            @Override public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "拍照失败: " + exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void toggleRecord() {
        if (activeRecording != null) {
            playRecordStopSound();
            activeRecording.stop();
            activeRecording = null;
            setRecordingButtonState(false);
            return;
        }
        if (videoCapture == null) return;

        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = MediaSaveHelper.createVideoContentValues();

        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                resolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build();

        PendingRecording pendingRecording = videoCapture.getOutput().prepareRecording(this, outputOptions);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingRecording = pendingRecording.withAudioEnabled();
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                playRecordStartSound();
                setRecordingButtonState(true);
                Toast.makeText(this, "开始录像", Toast.LENGTH_SHORT).show();
            } else if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize f = (VideoRecordEvent.Finalize) event;
                Uri outputUri = f.getOutputResults().getOutputUri();
                if (outputUri != null && outputUri != Uri.EMPTY) {
                    MediaSaveHelper.finishVideo(this, outputUri);
                }
                activeRecording = null;
                setRecordingButtonState(false);
                if (f.hasError()) {
                    String cause = f.getCause() == null ? "" : "，原因: " + f.getCause().getMessage();
                    Toast.makeText(this, "录像失败: " + videoErrorName(f.getError()) + cause, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "视频已保存到 DCIM/YFCamera", Toast.LENGTH_SHORT).show();
                    updateLatestAlbumThumbnail();
                }
            }
        });
    }

    private void setRecordingButtonState(boolean recording) {
        if (btnVideo == null) return;
        if (recording) {
            btnVideo.setText("");
            btnVideo.setBackgroundResource(R.drawable.bg_record_stop_button);
            btnVideo.setContentDescription("停止录像");
        } else {
            btnVideo.setText("录");
            btnVideo.setBackgroundResource(R.drawable.bg_record_button);
            btnVideo.setContentDescription("开始录像");
        }
    }

    private void loadWatermarkSettingsFromPrefs() {
        originalSize = AppSettings.isOriginalSize(this);
        watermarkData.projectName = AppSettings.getProjectName(this);
        watermarkData.photographerName = AppSettings.getPhotographerName(this);
    }

    private void disableManualTimeOverride() {
        watermarkData.manualTimeEnabled = false;
        AppSettings.prefs(this)
                .edit()
                .remove(AppSettings.KEY_MANUAL_TIME_ENABLED)
                .remove(AppSettings.KEY_MANUAL_TIME_TEXT)
                .apply();
    }

    private void openLogoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_PICK_LOGO);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开图片选择器: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadLogoFromPrefs() {
        SharedPreferences sp = AppSettings.prefs(this);
        String saved = sp.getString(AppSettings.KEY_LOGO_URI, null);
        Bitmap bitmap = null;
        if (AppSettings.LOGO_PRESET_YFCAMERA.equals(saved)) {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.yfcamera_watermark_logo);
        } else if (saved != null && saved.length() > 0) {
            bitmap = decodeLogo(Uri.parse(saved));
        }
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_watermark_logo);
        }
        logoBitmap = bitmap;
        watermarkPainter.setLogoBitmap(bitmap);
        if (ivLogo != null) ivLogo.setImageBitmap(bitmap);
    }

    private Bitmap decodeLogo(Uri uri) {
        InputStream in = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) in.close();

            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sample > 1200) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }

    private void updateLatestAlbumThumbnail() {
        if (btnAlbum == null) return;
        MediaItem latest = MediaSaveHelper.queryLatestMedia(this);
        if (latest == null) {
            btnAlbum.setImageResource(R.drawable.app_logo);
            return;
        }
        Bitmap thumb = ThumbnailUtil.loadThumbnail(this, latest.uri, 180);
        if (thumb != null) btnAlbum.setImageBitmap(thumb);
        else btnAlbum.setImageResource(R.drawable.app_logo);
    }

    private String videoErrorName(int error) {
        switch (error) {
            case VideoRecordEvent.Finalize.ERROR_NONE: return "成功";
            case VideoRecordEvent.Finalize.ERROR_UNKNOWN: return "未知错误(1)";
            case VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED: return "文件大小限制(2)";
            case VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE: return "存储空间不足(3)";
            case VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE: return "相机源中断(4)";
            case VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS: return "输出参数无效(5)";
            case VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED: return "编码失败(6)";
            case VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR: return "录制器错误(7)";
            case VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA: return "没有有效视频数据(8)";
            case VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED: return "录制时长限制(9)";
            case VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED: return "Recording对象被回收(10)";
            default: return "未知错误码(" + error + ")";
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_LOGO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Throwable ignored) {}
            Bitmap bitmap = decodeLogo(uri);
            if (bitmap == null) {
                Toast.makeText(this, "PNG 图标读取失败", Toast.LENGTH_LONG).show();
                return;
            }
            logoBitmap = bitmap;
            watermarkPainter.setLogoBitmap(bitmap);
            ivLogo.setImageBitmap(bitmap);
            AppSettings.prefs(this).edit().putString(AppSettings.KEY_LOGO_URI, uri.toString()).apply();
            Toast.makeText(this, "水印图标已更换", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        loadWatermarkSettingsFromPrefs();
        loadLogoFromPrefs();
        updatePreviewWatermark();
        updateLatestAlbumThumbnail();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (activeRecording != null) { activeRecording.stop(); activeRecording = null; }
        mainHandler.removeCallbacks(hideZoomPanelRunnable);
        toneSequence++;
        if (timeTicker != null) timeTicker.stop();
        if (locationManager != null) locationManager.release();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (overlayEffect != null) { overlayEffect.clearOnDrawListener(); overlayEffect.close(); }
        if (overlayThread != null) overlayThread.quitSafely();
        if (mediaActionSound != null) { mediaActionSound.release(); mediaActionSound = null; }
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
