package com.yf.camera;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

import com.example.locationlib.LocationSDK;

import kotlin.Unit;

public class YFLocationManager {
    private final Activity activity;
    private final Handler handler;
    private final WatermarkData data;
    private final Runnable onChanged;
    private LocationSDK sdk;

    public YFLocationManager(Activity activity, Handler handler, WatermarkData data, Runnable onChanged) {
        this.activity = activity; this.handler = handler; this.data = data; this.onChanged = onChanged;
    }

    public void start() {
        sdk = new LocationSDK(activity.getApplicationContext());
        sdk.setOnLocationChangedListener((address, lat, lng) -> {
            if (address != null && address.trim().length() > 0) data.address = address.trim();
            if (lat != null) data.latitude = lat;
            if (lng != null) data.longitude = lng;
            if (onChanged != null) activity.runOnUiThread(onChanged);
            return Unit.INSTANCE;
        });
        try { sdk.start(); } catch (Throwable t) { Toast.makeText(activity, "定位启动失败: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
        handler.postDelayed(refreshTask, 60_000);
    }

    public void showLocationOptions() {
        try { if (sdk != null) sdk.showLocationOptions(activity, data.latitude, data.longitude); } catch (Throwable ignored) {}
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        try { if (sdk != null) sdk.onActivityResult(requestCode, resultCode, intent); } catch (Throwable ignored) {}
    }

    private final Runnable refreshTask = new Runnable() { @Override public void run() {
        try { if (sdk != null) sdk.refresh(); } catch (Throwable ignored) {}
        handler.postDelayed(this, 60_000);
    }};

    public void stop() {
        handler.removeCallbacks(refreshTask);
        try { if (sdk != null) sdk.stop(); } catch (Throwable ignored) {}
    }
    public void release() {
        stop();
        try { if (sdk != null) sdk.release(); } catch (Throwable ignored) {}
        sdk = null;
    }
}
