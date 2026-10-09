package com.yf.camera;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class MediaViewerActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_MIME = "extra_mime";
    public static final String EXTRA_NAME = "extra_name";

    public static final String EXTRA_URIS = "extra_uris";
    public static final String EXTRA_MIMES = "extra_mimes";
    public static final String EXTRA_NAMES = "extra_names";
    public static final String EXTRA_INDEX = "extra_index";

    private final ArrayList<String> uriTexts = new ArrayList<>();
    private final ArrayList<String> mimeTypes = new ArrayList<>();
    private final ArrayList<String> displayNames = new ArrayList<>();
    private int currentIndex = 0;

    private Uri uri;
    private String mimeType;
    private String displayName;

    private ImageView ivFullImage;
    private VideoView vvFullVideo;
    private TextView tvViewerTitle;
    private View currentMediaView;
    private View videoControlsPanel;
    private SeekBar seekVideo;
    private TextView tvVideoTime;
    private Button btnVideoPlayPause;
    private Button btnVideoRotate;
    private Button btnVideoScreenshot;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector swipeGestureDetector;
    private float mediaScale = 1f;
    private boolean userSeeking = false;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            updateVideoProgress();
            progressHandler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        ivFullImage = findViewById(R.id.ivFullImage);
        vvFullVideo = findViewById(R.id.vvFullVideo);
        tvViewerTitle = findViewById(R.id.tvViewerTitle);
        videoControlsPanel = findViewById(R.id.videoControlsPanel);
        seekVideo = findViewById(R.id.seekVideo);
        tvVideoTime = findViewById(R.id.tvVideoTime);
        btnVideoPlayPause = findViewById(R.id.btnVideoPlayPause);
        btnVideoRotate = findViewById(R.id.btnVideoRotate);
        btnVideoScreenshot = findViewById(R.id.btnVideoScreenshot);
        Button btnBack = findViewById(R.id.btnViewerBack);
        Button btnShare = findViewById(R.id.btnViewerShare);

        btnBack.setOnClickListener(v -> finish());
        btnShare.setOnClickListener(v -> showShareOptions());
        btnVideoPlayPause.setOnClickListener(v -> toggleVideoPlayPause());
        btnVideoRotate.setOnClickListener(v -> toggleOrientation());
        btnVideoScreenshot.setOnClickListener(v -> captureCurrentVideoFrame());
        setupSeekBar();

        readIntentMediaList();
        if (uriTexts.isEmpty()) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (currentIndex < 0 || currentIndex >= uriTexts.size()) currentIndex = 0;

        setupViewerGestures();
        loadCurrentMedia(true, 0);
    }

    private void readIntentMediaList() {
        ArrayList<String> uris = getIntent().getStringArrayListExtra(EXTRA_URIS);
        ArrayList<String> mimes = getIntent().getStringArrayListExtra(EXTRA_MIMES);
        ArrayList<String> names = getIntent().getStringArrayListExtra(EXTRA_NAMES);
        if (uris != null && !uris.isEmpty()) {
            uriTexts.addAll(uris);
            if (mimes != null) mimeTypes.addAll(mimes);
            if (names != null) displayNames.addAll(names);
            currentIndex = getIntent().getIntExtra(EXTRA_INDEX, 0);
            return;
        }

        String uriText = getIntent().getStringExtra(EXTRA_URI);
        if (uriText != null && uriText.trim().length() > 0) {
            uriTexts.add(uriText);
            String mime = getIntent().getStringExtra(EXTRA_MIME);
            String name = getIntent().getStringExtra(EXTRA_NAME);
            mimeTypes.add(mime == null ? "" : mime);
            displayNames.add(name == null ? "" : name);
            currentIndex = 0;
        }
    }

    private void setupSeekBar() {
        seekVideo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && vvFullVideo != null) {
                    vvFullVideo.seekTo(progress);
                    updateVideoProgressText(progress, Math.max(vvFullVideo.getDuration(), seekBar.getMax()));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (vvFullVideo != null) vvFullVideo.seekTo(seekBar.getProgress());
            }
        });
    }

    private void setupViewerGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                mediaScale *= detector.getScaleFactor();
                mediaScale = Math.max(1f, Math.min(mediaScale, 5f));
                applyMediaScale();
                return true;
            }
        });

        swipeGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isVideo()) {
                    toggleVideoControls();
                    return true;
                }
                return false;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                mediaScale = mediaScale > 1.01f ? 1f : 2.2f;
                applyMediaScale();
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < 90 || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                if (mediaScale > 1.15f) return false;
                if (dx < 0) showNext();
                else showPrevious();
                return true;
            }
        });

        View.OnTouchListener listener = (v, event) -> {
            if (scaleGestureDetector != null) scaleGestureDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1 && swipeGestureDetector != null) {
                swipeGestureDetector.onTouchEvent(event);
            }
            return true;
        };
        ivFullImage.setOnTouchListener(listener);
        vvFullVideo.setOnTouchListener(listener);
    }

    private void loadCurrentMedia(boolean resetScale, int direction) {
        String uriText = safeGet(uriTexts, currentIndex);
        if (uriText == null || uriText.trim().length() == 0) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
            return;
        }
        uri = Uri.parse(uriText);
        mimeType = safeGet(mimeTypes, currentIndex);
        displayName = safeGet(displayNames, currentIndex);
        if (mimeType == null) mimeType = "";
        if (displayName == null) displayName = "";

        if (resetScale) mediaScale = 1f;
        applyMediaScale();
        updateTitle();
        hideVideoControls();
        stopProgressLoop();

        vvFullVideo.stopPlayback();
        if (isVideo()) {
            ivFullImage.setVisibility(View.GONE);
            vvFullVideo.setVisibility(View.VISIBLE);
            currentMediaView = vvFullVideo;
            vvFullVideo.setMediaController(null);
            vvFullVideo.setVideoURI(uri);
            vvFullVideo.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                int duration = Math.max(1, vvFullVideo.getDuration());
                seekVideo.setMax(duration);
                seekVideo.setProgress(0);
                updateVideoProgressText(0, duration);
                applyMediaScale();
                vvFullVideo.start();
                btnVideoPlayPause.setText("暂停");
                startProgressLoop();
            });
            vvFullVideo.setOnCompletionListener(mp -> {
                btnVideoPlayPause.setText("播放");
                updateVideoProgress();
                showVideoControls();
            });
            animateMediaSwitch(direction);
            vvFullVideo.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "视频打开失败", Toast.LENGTH_SHORT).show();
                return true;
            });
        } else {
            vvFullVideo.setVisibility(View.GONE);
            ivFullImage.setVisibility(View.VISIBLE);
            currentMediaView = ivFullImage;
            ivFullImage.setImageURI(uri);
            applyMediaScale();
            animateMediaSwitch(direction);
        }
    }

    private void animateMediaSwitch(int direction) {
        if (currentMediaView == null || direction == 0) return;
        float offset = Math.max(48f, currentMediaView.getWidth() * 0.10f);
        currentMediaView.setAlpha(0.55f);
        currentMediaView.setTranslationX(direction > 0 ? offset : -offset);
        currentMediaView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180L)
                .start();
    }

    private String safeGet(ArrayList<String> list, int index) {
        if (list == null || index < 0 || index >= list.size()) return "";
        return list.get(index);
    }

    private void updateTitle() {
        String name = displayName == null || displayName.length() == 0 ? "查看" : displayName;
        if (uriTexts.size() > 1) {
            tvViewerTitle.setText((currentIndex + 1) + "/" + uriTexts.size() + "  " + name);
        } else {
            tvViewerTitle.setText(name);
        }
    }

    private void applyMediaScale() {
        if (ivFullImage != null) {
            ivFullImage.setScaleX(mediaScale);
            ivFullImage.setScaleY(mediaScale);
        }
        if (vvFullVideo != null) {
            vvFullVideo.setScaleX(mediaScale);
            vvFullVideo.setScaleY(mediaScale);
        }
    }

    private void showNext() {
        if (uriTexts.size() <= 1) return;
        if (currentIndex >= uriTexts.size() - 1) {
            Toast.makeText(this, "已经是最后一个", Toast.LENGTH_SHORT).show();
            return;
        }
        currentIndex++;
        loadCurrentMedia(true, 1);
    }

    private void showPrevious() {
        if (uriTexts.size() <= 1) return;
        if (currentIndex <= 0) {
            Toast.makeText(this, "已经是第一个", Toast.LENGTH_SHORT).show();
            return;
        }
        currentIndex--;
        loadCurrentMedia(true, -1);
    }

    private boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video");
    }

    private void toggleVideoControls() {
        if (videoControlsPanel == null) return;
        if (videoControlsPanel.getVisibility() == View.VISIBLE) hideVideoControls();
        else showVideoControls();
    }

    private void showVideoControls() {
        if (videoControlsPanel == null || !isVideo()) return;
        videoControlsPanel.setAlpha(0f);
        videoControlsPanel.setVisibility(View.VISIBLE);
        videoControlsPanel.animate().alpha(1f).setDuration(150L).start();
        updateVideoProgress();
    }

    private void hideVideoControls() {
        if (videoControlsPanel != null) videoControlsPanel.setVisibility(View.GONE);
    }

    private void toggleVideoPlayPause() {
        if (!isVideo() || vvFullVideo == null) return;
        if (vvFullVideo.isPlaying()) {
            vvFullVideo.pause();
            btnVideoPlayPause.setText("播放");
        } else {
            vvFullVideo.start();
            btnVideoPlayPause.setText("暂停");
            startProgressLoop();
        }
        updateVideoProgress();
    }

    private void updateVideoProgress() {
        if (!isVideo() || vvFullVideo == null || seekVideo == null) return;
        int duration = Math.max(1, vvFullVideo.getDuration());
        int pos = Math.max(0, vvFullVideo.getCurrentPosition());
        if (seekVideo.getMax() != duration) seekVideo.setMax(duration);
        if (!userSeeking) seekVideo.setProgress(Math.min(pos, duration));
        updateVideoProgressText(pos, duration);
        if (btnVideoPlayPause != null) btnVideoPlayPause.setText(vvFullVideo.isPlaying() ? "暂停" : "播放");
    }

    private void updateVideoProgressText(int posMs, int durationMs) {
        if (tvVideoTime == null) return;
        tvVideoTime.setText(formatMs(posMs) + " / " + formatMs(durationMs));
    }

    private String formatMs(int ms) {
        int totalSeconds = Math.max(0, ms / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    private void startProgressLoop() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressLoop() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    private void toggleOrientation() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            btnVideoRotate.setText("横屏");
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            btnVideoRotate.setText("竖屏");
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (btnVideoRotate != null) {
            btnVideoRotate.setText(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "竖屏" : "横屏");
        }
    }

    private void captureCurrentVideoFrame() {
        if (!isVideo() || uri == null) return;
        final int pos = vvFullVideo == null ? 0 : vvFullVideo.getCurrentPosition();
        Toast.makeText(this, "正在截取当前画面...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Bitmap bitmap = null;
            Uri outUri = null;
            try {
                retriever.setDataSource(this, uri);
                bitmap = retriever.getFrameAtTime(pos * 1000L, MediaMetadataRetriever.OPTION_CLOSEST);
                if (bitmap == null) throw new IllegalStateException("未获取到视频帧");
                outUri = MediaSaveHelper.createImageUri(this);
                if (outUri == null) throw new IllegalStateException("创建截图文件失败");
                OutputStream os = getContentResolver().openOutputStream(outUri);
                if (os == null) throw new IllegalStateException("打开截图文件失败");
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                } finally {
                    os.close();
                }
                MediaSaveHelper.finishImage(this, outUri);
                runOnUiThread(() -> Toast.makeText(this, "截图已保存到 DCIM/YFCamera", Toast.LENGTH_SHORT).show());
            } catch (Throwable t) {
                final String msg = t.getMessage() == null ? "未知错误" : t.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "截图失败: " + msg, Toast.LENGTH_LONG).show());
            } finally {
                try { retriever.release(); } catch (Throwable ignored) {}
                if (bitmap != null) bitmap.recycle();
            }
        }).start();
    }

    private void showShareOptions() {
        String[] options = new String[] { "预留分享通道（未来开发）", "微信分享给朋友" };
        new AlertDialog.Builder(this)
                .setTitle("选择分享方式")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Toast.makeText(this, "这个分享通道先留空，后续开发", Toast.LENGTH_SHORT).show();
                    } else {
                        shareToWechat();
                    }
                })
                .show();
    }

    private void shareToWechat() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType != null && mimeType.length() > 0 ? mimeType : (isVideo() ? "video/*" : "image/*"));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        if (uri != null) intent.setClipData(ClipData.newUri(getContentResolver(), "YFCamera", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage("com.tencent.mm");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未安装微信，或当前微信版本不支持直接调起分享", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "分享失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopProgressLoop();
        if (vvFullVideo != null) vvFullVideo.stopPlayback();
    }
}
