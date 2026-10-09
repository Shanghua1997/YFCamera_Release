package com.yf.camera;

import android.Manifest;
import android.os.Build;
import android.widget.Toast;
import androidx.fragment.app.FragmentActivity;
import com.example.permissionlib.PermissionRequester;
import com.example.permissionlib.PermissionRequesterActivity;
import kotlin.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PermissionHelper {
    public static void requestCameraPermissions(FragmentActivity activity, Runnable grantedAction) {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.RECORD_AUDIO);
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT <= 28) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        String[] permissions = list.toArray(new String[0]);
        PermissionRequester.Config config = new PermissionRequester.Config(
                permissions,
                () -> { if (grantedAction != null) grantedAction.run(); return Unit.INSTANCE; },
                (Map<String, Boolean> denied) -> { Toast.makeText(activity, "权限被拒绝，无法拍照录像", Toast.LENGTH_LONG).show(); return Unit.INSTANCE; },
                (List<String> denied) -> { Toast.makeText(activity, "权限被永久拒绝，请到系统设置开启", Toast.LENGTH_LONG).show(); return Unit.INSTANCE; },
                (String p) -> { Toast.makeText(activity, "需要权限: " + p, Toast.LENGTH_SHORT).show(); return Unit.INSTANCE; }
        );
        new PermissionRequesterActivity(activity, config).request();
    }
}
