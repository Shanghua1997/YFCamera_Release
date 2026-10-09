package com.yf.camera;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQ_PICK_LOGO = 72001;

    private Switch switchOriginal;
    private TextView tvProjectValue;
    private TextView tvPhotographerValue;
    private TextView tvLogoValue;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView btnBack = findViewById(R.id.btnBack);
        switchOriginal = findViewById(R.id.switchOriginal);
        tvProjectValue = findViewById(R.id.tvProjectValue);
        tvPhotographerValue = findViewById(R.id.tvPhotographerValue);
        tvLogoValue = findViewById(R.id.tvLogoValue);
        LinearLayout rowOriginal = findViewById(R.id.rowOriginal);
        LinearLayout rowWatermarkLogo = findViewById(R.id.rowWatermarkLogo);
        LinearLayout rowProject = findViewById(R.id.rowProject);
        LinearLayout rowPhotographer = findViewById(R.id.rowPhotographer);

        btnBack.setOnClickListener(v -> finish());
        switchOriginal.setChecked(AppSettings.isOriginalSize(this));
        switchOriginal.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            AppSettings.setOriginalSize(this, isChecked);
            Toast.makeText(this, isChecked ? "已开启原图" : "已关闭原图", Toast.LENGTH_SHORT).show();
        });
        rowOriginal.setOnClickListener(v -> switchOriginal.setChecked(!switchOriginal.isChecked()));

        rowWatermarkLogo.setOnClickListener(v -> showLogoChoiceDialog());
        rowWatermarkLogo.setOnLongClickListener(v -> {
            AppSettings.prefs(this).edit().remove(AppSettings.KEY_LOGO_URI).apply();
            refreshValues();
            Toast.makeText(this, "已恢复默认 CBN 水印", Toast.LENGTH_SHORT).show();
            return true;
        });

        rowProject.setOnClickListener(v -> showTextEditDialog(
                "项目",
                AppSettings.getProjectName(this),
                value -> {
                    AppSettings.setProjectName(this, value);
                    refreshValues();
                }
        ));

        rowPhotographer.setOnClickListener(v -> showTextEditDialog(
                "拍摄人",
                AppSettings.getPhotographerName(this),
                value -> {
                    AppSettings.setPhotographerName(this, value);
                    refreshValues();
                }
        ));

        refreshValues();
    }

    private void refreshValues() {
        tvProjectValue.setText(displayValue(AppSettings.getProjectName(this)));
        tvPhotographerValue.setText(displayValue(AppSettings.getPhotographerName(this)));
        String savedLogo = AppSettings.prefs(this).getString(AppSettings.KEY_LOGO_URI, null);
        if (savedLogo == null || savedLogo.length() == 0) {
            tvLogoValue.setText("当前：默认 CBN 水印，点击可切换");
        } else if (AppSettings.LOGO_PRESET_YFCAMERA.equals(savedLogo)) {
            tvLogoValue.setText("当前：YFCamera 预设图标，点击可切换");
        } else {
            tvLogoValue.setText("当前：自定义图片，点击可切换，长按恢复 CBN");
        }
    }

    private String displayValue(String value) {
        if (value == null || value.trim().length() == 0) return "未填写，水印不显示";
        return value.trim();
    }

    private void showLogoChoiceDialog() {
        String[] items = new String[]{"使用默认 CBN 水印", "使用 YFCamera 预设图标", "从本地选择 PNG"};
        new AlertDialog.Builder(this)
                .setTitle("选择水印图片")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        AppSettings.prefs(this).edit().remove(AppSettings.KEY_LOGO_URI).apply();
                        refreshValues();
                        Toast.makeText(this, "已切换为默认 CBN 水印", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        AppSettings.prefs(this).edit()
                                .putString(AppSettings.KEY_LOGO_URI, AppSettings.LOGO_PRESET_YFCAMERA)
                                .apply();
                        refreshValues();
                        Toast.makeText(this, "已切换为 YFCamera 预设图标", Toast.LENGTH_SHORT).show();
                    } else {
                        openLogoPicker();
                    }
                })
                .show();
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_LOGO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Throwable ignored) {}
            AppSettings.prefs(this).edit().putString(AppSettings.KEY_LOGO_URI, uri.toString()).apply();
            refreshValues();
            Toast.makeText(this, "水印图片已更换", Toast.LENGTH_SHORT).show();
        }
    }

    private interface TextSaveCallback {
        void onSave(String value);
    }

    private void showTextEditDialog(String title, String current, TextSaveCallback callback) {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(current == null ? "" : current);
        input.setSelectAllOnFocus(true);
        input.setHint("为空则不显示“" + title + "”字段");
        input.setPadding(36, 18, 36, 18);

        new AlertDialog.Builder(this)
                .setTitle("设置" + title)
                .setMessage("填写后会显示在预览、照片和视频水印中；清空则不显示该字段。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    callback.onSave(value);
                    Toast.makeText(this, "已保存" + title, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", (dialog, which) -> {
                    callback.onSave("");
                    Toast.makeText(this, "已清空" + title, Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
