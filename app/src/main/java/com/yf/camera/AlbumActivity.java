package com.yf.camera;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AlbumActivity extends AppCompatActivity {
    private AlbumAdapter adapter;
    private TextView tvAlbumTip;
    private Button btnDelete;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnShare = findViewById(R.id.btnShare);
        btnDelete = findViewById(R.id.btnMultiSelect);
        tvAlbumTip = findViewById(R.id.tvAlbumTip);
        RecyclerView rvAlbum = findViewById(R.id.rvAlbum);

        adapter = new AlbumAdapter(this, (position, item) -> {
            if (adapter.isMultiSelectMode()) {
                updateTipForMultiSelect();
            } else {
                openViewer(position);
            }
        });
        rvAlbum.setLayoutManager(new GridLayoutManager(this, 3));
        rvAlbum.setAdapter(adapter);

        btnBack.setOnClickListener(v -> {
            if (adapter != null && adapter.isMultiSelectMode()) {
                adapter.setMultiSelectMode(false);
                updateNormalTip();
            } else {
                finish();
            }
        });
        btnDelete.setText("删除");
        btnDelete.setOnClickListener(v -> confirmDeleteSelected());
        btnShare.setOnClickListener(v -> showShareOptions());
        loadMedia();
    }

    private void loadMedia() {
        List<MediaItem> items = MediaSaveHelper.queryRecentMedia(this, 200);
        adapter.setItems(items);
        updateNormalTip();
    }

    private void updateNormalTip() {
        if (adapter == null || tvAlbumTip == null) return;
        if (adapter.getItemCount() == 0) {
            tvAlbumTip.setText("DCIM/YFCamera 目录下还没有照片或视频");
        } else {
            tvAlbumTip.setText("点击缩略图进入查看；长按进入多选；选中后可批量分享或删除");
        }
    }

    private void updateTipForMultiSelect() {
        if (adapter == null || tvAlbumTip == null) return;
        tvAlbumTip.setText("已选择 " + adapter.getSelectedCount() + " 项；继续点击可勾选/取消，点分享可批量发送，点删除可批量删除");
    }

    private void openViewer(int position) {
        List<MediaItem> items = adapter.getItemsSnapshot();
        if (position < 0 || position >= items.size()) return;

        ArrayList<String> uris = new ArrayList<>();
        ArrayList<String> mimes = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (MediaItem item : items) {
            uris.add(item.uri == null ? "" : item.uri.toString());
            mimes.add(item.mimeType == null ? "" : item.mimeType);
            names.add(item.displayName == null ? "" : item.displayName);
        }

        Intent intent = new Intent(this, MediaViewerActivity.class);
        intent.putStringArrayListExtra(MediaViewerActivity.EXTRA_URIS, uris);
        intent.putStringArrayListExtra(MediaViewerActivity.EXTRA_MIMES, mimes);
        intent.putStringArrayListExtra(MediaViewerActivity.EXTRA_NAMES, names);
        intent.putExtra(MediaViewerActivity.EXTRA_INDEX, position);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private List<MediaItem> getCurrentShareSelection() {
        List<MediaItem> selected;
        if (adapter != null && adapter.isMultiSelectMode()) {
            selected = adapter.getSelectedItems();
        } else {
            selected = new ArrayList<>();
            MediaItem item = adapter == null ? null : adapter.getSelectedItem();
            if (item != null) selected.add(item);
        }
        return selected;
    }

    private void showShareOptions() {
        List<MediaItem> selected = getCurrentShareSelection();
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先长按照片/视频进入多选，或先点选一个文件", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = new String[] { "预留分享通道（未来开发）", "微信分享给朋友" };
        new AlertDialog.Builder(this)
                .setTitle(selected.size() > 1 ? "批量分享 " + selected.size() + " 项" : "选择分享方式")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Toast.makeText(this, "这个分享通道先留空，后续开发", Toast.LENGTH_SHORT).show();
                    } else {
                        shareToWechat(selected);
                    }
                })
                .show();
    }

    private void confirmDeleteSelected() {
        if (adapter == null || !adapter.isMultiSelectMode()) {
            Toast.makeText(this, "长按照片或视频进入多选后，再点删除", Toast.LENGTH_SHORT).show();
            return;
        }
        List<MediaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, "还没有选择要删除的照片或视频", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除 " + selected.size() + " 项？")
                .setMessage("删除后会从系统相册和 DCIM/YFCamera 中移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    int deleted = MediaSaveHelper.deleteMedia(this, selected);
                    Toast.makeText(this, "已删除 " + deleted + " 项", Toast.LENGTH_SHORT).show();
                    adapter.setMultiSelectMode(false);
                    loadMedia();
                })
                .show();
    }

    private void shareToWechat(List<MediaItem> items) {
        if (items == null || items.isEmpty()) return;
        Intent intent;
        if (items.size() == 1) {
            MediaItem item = items.get(0);
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType(item.mimeType != null && item.mimeType.length() > 0 ? item.mimeType : (item.isVideo() ? "video/*" : "image/*"));
            intent.putExtra(Intent.EXTRA_STREAM, item.uri);
            if (item.uri != null) intent.setClipData(ClipData.newUri(getContentResolver(), "YFCamera", item.uri));
        } else {
            ArrayList<Uri> uris = new ArrayList<>();
            boolean hasVideo = false;
            boolean hasImage = false;
            ClipData clipData = null;
            for (MediaItem item : items) {
                if (item == null || item.uri == null) continue;
                uris.add(item.uri);
                if (item.isVideo()) hasVideo = true;
                if (item.isImage()) hasImage = true;
                if (clipData == null) clipData = ClipData.newUri(getContentResolver(), "YFCamera", item.uri);
                else clipData.addItem(new ClipData.Item(item.uri));
            }
            if (uris.isEmpty()) {
                Toast.makeText(this, "没有可分享的文件", Toast.LENGTH_SHORT).show();
                return;
            }
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType(hasVideo && hasImage ? "*/*" : (hasVideo ? "video/*" : "image/*"));
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            if (clipData != null) intent.setClipData(clipData);
        }
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

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null) loadMedia();
    }
}
