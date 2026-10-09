package com.yf.camera;

import android.os.Handler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeTicker {
    private final Handler handler;
    private final WatermarkData data;
    private final Runnable callback;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    public TimeTicker(Handler handler, WatermarkData data, Runnable callback) {
        this.handler = handler; this.data = data; this.callback = callback;
    }
    private final Runnable task = new Runnable() { @Override public void run() {
        if (!data.manualTimeEnabled) {
            data.timeText = sdf.format(new Date());
        }
        if (callback != null) callback.run();
        handler.postDelayed(this, 1000);
    }};
    public void start() { task.run(); }
    public void stop() { handler.removeCallbacks(task); }
}
