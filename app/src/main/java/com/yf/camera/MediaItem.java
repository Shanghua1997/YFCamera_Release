package com.yf.camera;

import android.net.Uri;

public class MediaItem {
    public final Uri uri;
    public final String mimeType;
    public final String displayName;
    public final long dateAdded;
    public final int mediaType;

    public MediaItem(Uri uri, String mimeType, String displayName, long dateAdded, int mediaType) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.displayName = displayName;
        this.dateAdded = dateAdded;
        this.mediaType = mediaType;
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video");
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image");
    }
}
