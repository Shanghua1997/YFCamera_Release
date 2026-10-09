package com.yf.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

public class WatermarkPainter {
    private static final float TARGET_WATERMARK_HEIGHT_RATIO = 0.27f;
    private static final float MIN_WATERMARK_HEIGHT_RATIO_FOR_UPSCALE = 0.25f;
    private static final float MAX_AUTO_SCALE = 3.20f;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private volatile Bitmap logoBitmap;

    public WatermarkPainter() {
        // 水印本身不绘制白底卡片，只保留 PNG 与文字，避免预览和成片出现“白块”。
        bgPaint.setColor(Color.TRANSPARENT);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(0f);
        strokePaint.setColor(Color.TRANSPARENT);
        textPaint.setColor(Color.WHITE);
        textPaint.setSubpixelText(true);
        textPaint.setDither(true);
        textPaint.clearShadowLayer();
        bitmapPaint.setDither(true);
        bitmapPaint.setFilterBitmap(true);
    }

    public void setLogoBitmap(Bitmap logoBitmap) {
        this.logoBitmap = logoBitmap;
    }

    public void draw(Canvas canvas, String text) {
        if (canvas == null || text == null) return;
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // CameraX 的录像 Overlay 在部分机型上拿到的是横向缓冲区；App 固定竖屏，水印锁定为竖向坐标系。
        if (w > h) {
            canvas.save();
            canvas.translate(0, h);
            canvas.rotate(-90f);
            drawInPortraitSpace(canvas, h, w, text);
            canvas.restore();
            return;
        }
        drawInPortraitSpace(canvas, w, h, text);
    }

    private void drawInPortraitSpace(Canvas canvas, int w, int h, String text) {
        float densityBase = Math.max(1f, Math.min(w, h) / 720f);

        WatermarkLayout layout = buildLayout(w, h, text, densityBase, 1f);

        // 预览页水印是原生 TextView/Logo，成片水印是直接画到照片/视频像素上。
        // 上一版虽然做了自动放大，但在部分机型的成片里仍明显小于预览。
        // 这里改为更硬性的视觉比例：最终照片/视频水印组高度控制在接近竖向画幅 1/4，文字比上一版更小，
        // 实际在相册/播放器中看起来会更接近预览页约 1/4 的占比。
        float targetH = h * TARGET_WATERMARK_HEIGHT_RATIO;
        if (layout.totalH > 0 && layout.totalH < targetH) {
            float scale = Math.min(MAX_AUTO_SCALE, targetH / layout.totalH);
            layout = buildLayout(w, h, text, densityBase, Math.max(1f, scale));

            // 放大后因文字重新换行，真实高度可能仍不够，再做两次迭代校准。
            for (int i = 0; i < 2 && layout.totalH > 0 && layout.totalH < h * MIN_WATERMARK_HEIGHT_RATIO_FOR_UPSCALE; i++) {
                scale = Math.min(MAX_AUTO_SCALE, scale * (targetH / layout.totalH));
                layout = buildLayout(w, h, text, densityBase, Math.max(1f, scale));
            }
        }

        drawLayout(canvas, w, h, layout);
    }

    private WatermarkLayout buildLayout(int w, int h, String text, float densityBase, float autoScale) {
        WatermarkLayout layout = new WatermarkLayout();
        layout.padding = 6f * densityBase * autoScale;
        layout.textSize = 22.5f * densityBase * autoScale;
        layout.lineGap = 5f * densityBase * autoScale;
        layout.radius = 12f * densityBase * autoScale;
        layout.gap = 8f * densityBase * autoScale;

        textPaint.setTextSize(layout.textSize);
        strokePaint.setStrokeWidth(Math.max(1f, 1.2f * densityBase * autoScale));
        layout.fm = textPaint.getFontMetrics();
        layout.lineHeight = layout.fm.descent - layout.fm.ascent + layout.lineGap;

        // 成片分辨率通常远高于预览，如果文本宽度随像素无限变宽，地址会变成一长行，
        // 整组水印高度自然偏小。限制文本最大宽度，让成片换行方式更接近手机预览。
        float maxContentWidth = Math.min(w - layout.padding * 4, w * 0.82f);
        layout.lines = wrapText(text, maxContentWidth);
        float maxLineWidth = 0;
        for (String line : layout.lines) maxLineWidth = Math.max(maxLineWidth, textPaint.measureText(line));
        layout.textBoxW = Math.min(w - layout.padding * 2, maxLineWidth + layout.padding * 2);
        layout.textBoxH = layout.lines.length * layout.lineHeight + layout.padding * 2;

        Bitmap logo = logoBitmap;
        if (logo != null && !logo.isRecycled()) {
            // 对任何自定义 PNG 都进行边界约束：保持长宽比，避免过高/过宽抢占画面。
            // maxLogoH 跟随文字自动放大，让照片/视频中的 Logo 与文字比例接近预览页。
            float maxLogoH = Math.min(4.2f * layout.lineHeight + layout.padding * 2, h * 0.22f);
            float maxLogoW = w * 0.58f;
            float naturalW = maxLogoH * logo.getWidth() / Math.max(1f, logo.getHeight()) + layout.padding * 2;
            layout.logoBoxW = Math.min(maxLogoW, naturalW);
            layout.logoBoxH = Math.min(maxLogoH, (layout.logoBoxW - layout.padding * 2) * logo.getHeight() / Math.max(1f, logo.getWidth()) + layout.padding * 2);
            layout.logoBoxW = Math.max(layout.logoBoxW, 1f);
            layout.logoBoxH = Math.max(layout.logoBoxH, 1f);
        }

        layout.totalH = layout.textBoxH + (layout.logoBoxH > 0 ? layout.logoBoxH + layout.gap : 0);
        return layout;
    }

    private void drawLayout(Canvas canvas, int w, int h, WatermarkLayout layout) {
        Bitmap logo = logoBitmap;
        float left = layout.padding;
        // 成片水印不要贴到视频缓冲区最底部，避免部分机型/播放器边缘裁切导致定位信息被截掉。
        // 保持较大的安全距离，同时让整体视觉上继续位于画面下部。
        float bottomSafeInset = Math.max(layout.padding * 4.2f, h * 0.045f);
        float top = h - layout.totalH - bottomSafeInset;
        if (top < layout.padding) top = layout.padding;

        if (layout.logoBoxH > 0 && logo != null && !logo.isRecycled()) {
            RectF logoBg = new RectF(left, top, left + layout.logoBoxW, top + layout.logoBoxH);
            drawCard(canvas, logoBg, layout.radius);
            float innerLeft = logoBg.left + layout.padding;
            float innerTop = logoBg.top + layout.padding;
            float innerW = logoBg.width() - layout.padding * 2;
            float innerH = logoBg.height() - layout.padding * 2;
            float scale = Math.min(innerW / logo.getWidth(), innerH / logo.getHeight());
            float drawW = logo.getWidth() * scale;
            float drawH = logo.getHeight() * scale;
            RectF dst = new RectF(
                    innerLeft + (innerW - drawW) / 2f,
                    innerTop + (innerH - drawH) / 2f,
                    innerLeft + (innerW + drawW) / 2f,
                    innerTop + (innerH + drawH) / 2f
            );
            canvas.drawBitmap(logo, null, dst, bitmapPaint);
            top += layout.logoBoxH + layout.gap;
        }

        textPaint.setTextSize(layout.textSize);
        RectF textBg = new RectF(left, top, left + layout.textBoxW, top + layout.textBoxH);
        drawCard(canvas, textBg, layout.radius);
        float y = top + layout.padding - layout.fm.ascent;
        for (String line : layout.lines) {
            canvas.drawText(line, left + layout.padding, y, textPaint);
            y += layout.lineHeight;
        }
    }

    private void drawCard(Canvas canvas, RectF rect, float radius) {
        // 透明水印：保留布局计算，但不绘制背景卡片和边框。
    }

    private String[] wrapText(String text, float maxWidth) {
        List<String> result = new ArrayList<>();
        String[] rawLines = text.split("\\n");
        for (String raw : rawLines) {
            if (raw == null || raw.length() == 0) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                String candidate = line.toString() + c;
                if (line.length() > 0 && textPaint.measureText(candidate) > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
            if (line.length() > 0) result.add(line.toString());
        }
        return result.toArray(new String[0]);
    }

    private static class WatermarkLayout {
        float padding;
        float textSize;
        float lineGap;
        float radius;
        float gap;
        Paint.FontMetrics fm;
        float lineHeight;
        String[] lines;
        float textBoxW;
        float textBoxH;
        float logoBoxW;
        float logoBoxH;
        float totalH;
    }
}
