package com.yf.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.OutputStream;

public class PhotoWatermarkUtil {
    // 非原图模式下仍保留足够像素，避免照片在手机相册里一放大就糊。
    // 之前用 inSampleSize 纯 2 倍降采样，4000px 照片可能直接变成约 1000px，水印看起来像截图放大。
    private static final int DEFAULT_MAX_SIDE = 2560;

    public static void addWatermarkAndSave(Context context, File srcFile, Uri dstUri, String text, boolean originalSize) throws Exception {
        addWatermarkAndSave(context, srcFile, dstUri, text, originalSize, null);
    }

    public static void addWatermarkAndSave(Context context, File srcFile, Uri dstUri, String text, boolean originalSize, Bitmap logoBitmap) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalStateException("照片尺寸读取失败");

        int sample = originalSize ? 1 : calculateSafeSampleSize(bounds.outWidth, bounds.outHeight, DEFAULT_MAX_SIDE);

        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(srcFile.getAbsolutePath(), opt);
        if (decoded == null) throw new IllegalStateException("照片解码失败");

        Bitmap rotated = rotateIfNeeded(srcFile, decoded);
        Bitmap prepared = originalSize ? rotated : scaleDownIfNeeded(rotated, DEFAULT_MAX_SIDE);
        if (prepared != rotated) rotated.recycle();

        Bitmap mutable = prepared.copy(Bitmap.Config.ARGB_8888, true);
        if (mutable != prepared) prepared.recycle();

        WatermarkPainter painter = new WatermarkPainter();
        painter.setLogoBitmap(logoBitmap);
        // 水印在最终输出尺寸上重新绘制，而不是先画到低分辨率图上再放大。
        painter.draw(new Canvas(mutable), text);

        OutputStream os = context.getContentResolver().openOutputStream(dstUri);
        if (os == null) throw new IllegalStateException("无法打开保存路径");
        try {
            mutable.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.flush();
        } finally {
            os.close();
            mutable.recycle();
        }
        MediaSaveHelper.finishImage(context, dstUri);
    }

    /**
     * 只用 inSampleSize 做粗降采样，保证解码后的长边仍然大于目标长边，后面再精确缩放。
     * 这样不会出现 4000px -> 1000px 这种过度降采样导致的模糊。
     */
    private static int calculateSafeSampleSize(int width, int height, int targetMaxSide) {
        int maxSide = Math.max(width, height);
        int sample = 1;
        while (sample * 2 <= 8 && maxSide / (sample * 2) >= targetMaxSide) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static Bitmap scaleDownIfNeeded(Bitmap bitmap, int targetMaxSide) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide <= targetMaxSide) return bitmap;

        float scale = targetMaxSide / (float) maxSide;
        int newW = Math.max(1, Math.round(w * scale));
        int newH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true);
    }

    private static Bitmap rotateIfNeeded(File file, Bitmap bitmap) throws Exception {
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int degrees = 0;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
        if (degrees == 0) return bitmap;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        bitmap.recycle();
        return rotated;
    }
}
