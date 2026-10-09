package com.yf.camera;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaSaveHelper {
    public static final String DIR_NAME = "YFCamera";
    public static final String RELATIVE_DIR = Environment.DIRECTORY_DCIM + "/" + DIR_NAME;

    public static Uri createImageUri(Context context) {
        String name = "IMG_" + nowName() + ".jpg";
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME);
            if (!dir.exists()) dir.mkdirs();
            values.put(MediaStore.Images.Media.DATA, new File(dir, name).getAbsolutePath());
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
    }

    public static ContentValues createVideoContentValues() {
        String name = "VID_" + nowName() + ".mp4";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_DIR);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME);
            if (!dir.exists()) dir.mkdirs();
            values.put(MediaStore.Video.Media.DATA, new File(dir, name).getAbsolutePath());
        }
        return values;
    }

    public static void finishImage(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        }
    }

    public static void finishVideo(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        }
    }

    public static MediaItem queryLatestMedia(Context context) {
        List<MediaItem> list = queryRecentMedia(context, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<MediaItem> queryRecentMedia(Context context, int limit) {
        List<MediaItem> result = new ArrayList<>();
        if (context == null) return result;
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = new String[] {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED
        };

        String selection;
        String[] args;
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
                    MediaStore.Files.FileColumns.MEDIA_TYPE + "=?) AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            args = new String[] {
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO),
                    RELATIVE_DIR + "%"
            };
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), DIR_NAME);
            selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
                    MediaStore.Files.FileColumns.MEDIA_TYPE + "=?) AND " +
                    MediaStore.MediaColumns.DATA + " LIKE ?";
            args = new String[] {
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                    String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO),
                    dir.getAbsolutePath() + "%"
            };
        }

        String sort = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(collection, projection, selection, args, sort);
            if (cursor == null) return result;
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int typeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);
            int mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int count = 0;
            while (cursor.moveToNext() && (limit <= 0 || count < limit)) {
                long id = cursor.getLong(idIndex);
                int mediaType = cursor.getInt(typeIndex);
                String mime = cursor.getString(mimeIndex);
                String name = cursor.getString(nameIndex);
                long date = cursor.getLong(dateIndex);
                Uri uri;
                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    if (mime == null) mime = "video/mp4";
                } else {
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    if (mime == null) mime = "image/jpeg";
                }
                result.add(new MediaItem(uri, mime, name, date, mediaType));
                count++;
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    public static int deleteMedia(Context context, List<MediaItem> items) {
        if (context == null || items == null || items.isEmpty()) return 0;
        int deleted = 0;
        ContentResolver resolver = context.getContentResolver();
        for (MediaItem item : items) {
            if (item == null || item.uri == null) continue;
            try {
                deleted += resolver.delete(item.uri, null, null);
            } catch (Throwable ignored) {
                // 如果未来删除非本 App 创建的媒体，Android 10+ 可能需要系统授权；
                // 当前 YFCamera 目录下由本 App 创建的文件一般可以直接删除。
            }
        }
        return deleted;
    }

    private static String nowName() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
    }
}
