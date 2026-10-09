package com.yf.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.permissionlib.PermissionRequester;
import com.example.permissionlib.PermissionRequesterActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kotlin.Unit;

public class PermissionHelper {
    public static void requestCameraPermissions(FragmentActivity activity, Runnable grantedAction) {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.RECORD_AUDIO);
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        addMediaReadPermissions(list);
        if (Build.VERSION.SDK_INT <= 28) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        request(activity, list, grantedAction, "权限被拒绝，无法完整使用拍照录像和相册", "权限被永久拒绝，请到系统设置开启");
    }

    public static void requestAlbumPermissions(FragmentActivity activity, Runnable grantedAction) {
        List<String> list = new ArrayList<>();
        addMediaReadPermissions(list);
        request(activity, list, grantedAction, "照片/视频读取权限被拒绝，无法显示历史素材", "照片/视频读取权限被永久拒绝，请到系统设置开启");
    }

    public static boolean hasAlbumPermission(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            return hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    && hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO);
        }
        return hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private static void addMediaReadPermissions(List<String> list) {
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.READ_MEDIA_IMAGES);
            list.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static void request(FragmentActivity activity, List<String> list, Runnable grantedAction, String deniedMsg, String permanentlyDeniedMsg) {
        String[] permissions = list.toArray(new String[0]);
        PermissionRequester.Config config = new PermissionRequester.Config(
                permissions,
                () -> { if (grantedAction != null) grantedAction.run(); return Unit.INSTANCE; },
                (Map<String, Boolean> denied) -> { Toast.makeText(activity, deniedMsg, Toast.LENGTH_LONG).show(); return Unit.INSTANCE; },
                (List<String> denied) -> { Toast.makeText(activity, permanentlyDeniedMsg, Toast.LENGTH_LONG).show(); return Unit.INSTANCE; },
                (String p) -> { Toast.makeText(activity, "需要权限: " + p, Toast.LENGTH_SHORT).show(); return Unit.INSTANCE; }
        );
        new PermissionRequesterActivity(activity, config).request();
    }
}
