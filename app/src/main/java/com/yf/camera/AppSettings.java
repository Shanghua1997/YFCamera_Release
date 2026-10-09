package com.yf.camera;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private AppSettings() {}

    public static final String PREFS = "yf_camera_prefs";
    public static final String KEY_LOGO_URI = "logo_uri";
    public static final String LOGO_PRESET_YFCAMERA = "preset:yfcamera";
    public static final String KEY_MANUAL_TIME_ENABLED = "manual_time_enabled";
    public static final String KEY_MANUAL_TIME_TEXT = "manual_time_text";
    public static final String KEY_ORIGINAL_SIZE = "original_size";
    public static final String KEY_PROJECT_NAME = "project_name";
    public static final String KEY_PHOTOGRAPHER_NAME = "photographer_name";

    public static final String DEFAULT_PROJECT_NAME = "抢修现场记录";
    public static final String DEFAULT_PHOTOGRAPHER_NAME = "";

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOriginalSize(Context context) {
        return prefs(context).getBoolean(KEY_ORIGINAL_SIZE, false);
    }

    public static void setOriginalSize(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ORIGINAL_SIZE, enabled).apply();
    }

    public static String getProjectName(Context context) {
        return prefs(context).getString(KEY_PROJECT_NAME, DEFAULT_PROJECT_NAME);
    }

    public static void setProjectName(Context context, String value) {
        prefs(context).edit().putString(KEY_PROJECT_NAME, value == null ? "" : value.trim()).apply();
    }

    public static String getPhotographerName(Context context) {
        return prefs(context).getString(KEY_PHOTOGRAPHER_NAME, DEFAULT_PHOTOGRAPHER_NAME);
    }

    public static void setPhotographerName(Context context, String value) {
        prefs(context).edit().putString(KEY_PHOTOGRAPHER_NAME, value == null ? "" : value.trim()).apply();
    }
}
