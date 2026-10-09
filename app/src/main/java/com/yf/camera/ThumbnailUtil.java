package com.yf.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

import java.io.InputStream;

public class ThumbnailUtil {
    private ThumbnailUtil() {}

    public static Bitmap loadThumbnail(Context context, Uri uri, int sizePx) {
        if (context == null || uri == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return context.getContentResolver().loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }
        } catch (Throwable ignored) {}

        InputStream in = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = context.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) in.close();

            int sample = 1;
            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxSide / sample > sizePx * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            in = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }
}
