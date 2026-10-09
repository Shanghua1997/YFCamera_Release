package com.yf.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WatermarkData {
    public volatile String timeText = "";
    public volatile boolean manualTimeEnabled = false;
    public volatile String address = "正在获取周边地点...";
    public volatile double latitude = 0;
    public volatile double longitude = 0;
    public volatile String projectName = AppSettings.DEFAULT_PROJECT_NAME;
    public volatile String photographerName = AppSettings.DEFAULT_PHOTOGRAPHER_NAME;

    public String buildText() {
        List<String> lines = new ArrayList<>();
        addIfNotEmpty(lines, getTimeLine());
        addIfNotEmpty(lines, getLocationLine());
        addIfNotEmpty(lines, getCoordLine());
        addIfNotEmpty(lines, getProjectLine());
        addIfNotEmpty(lines, getPhotographerLine());
        return joinLines(lines);
    }

    public String getTimeLine() {
        return timeText == null ? "" : timeText;
    }

    public String getLocationLine() {
        return "位置: " + normalizeAddressForDisplay(address);
    }

    public String getCoordLine() {
        if (Math.abs(latitude) > 0.000001 || Math.abs(longitude) > 0.000001) {
            return "经度: " + String.format(Locale.CHINA, "%.6f", longitude) +
                    "  纬度: " + String.format(Locale.CHINA, "%.6f", latitude);
        }
        return "经纬度获取中";
    }

    public String getProjectLine() {
        String v = projectName == null ? "" : projectName.trim();
        if (v.length() == 0) return "";
        return "项目: " + v;
    }

    public String getPhotographerLine() {
        String v = photographerName == null ? "" : photographerName.trim();
        if (v.length() == 0) return "";
        return "拍摄人: " + v;
    }

    public String getProjectInfoPreview() {
        List<String> lines = new ArrayList<>();
        addIfNotEmpty(lines, getProjectLine());
        addIfNotEmpty(lines, getPhotographerLine());
        return joinLines(lines);
    }

    private void addIfNotEmpty(List<String> lines, String value) {
        if (value != null && value.trim().length() > 0) lines.add(value.trim());
    }

    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private String normalizeAddressForDisplay(String raw) {
        if (raw == null) return "正在获取周边地点...";
        String v = raw.trim();
        if (v.length() == 0) return "正在获取周边地点...";
        if (looksLikeCoordinate(v)) return "已获取坐标，正在解析周边地点...";
        return omitProvincePrefix(v);
    }

    /**
     * 水印里省级名称占空间太大，且用户主要需要市、区、街道、小区/商户等细节。
     * 这里只去掉“辽宁省、河北省、山东省”等普通省份和自治区前缀；
     * 北京/上海/天津/重庆这类直辖市不去掉。
     */
    private String omitProvincePrefix(String value) {
        String v = value.trim().replaceAll("\\s+", " ");
        v = v.replaceFirst("^[\\u4e00-\\u9fa5]{2,7}省\\s*", "");
        v = v.replaceFirst("^(内蒙古|广西壮族|西藏|宁夏回族|新疆维吾尔)自治区\\s*", "");
        v = v.replaceFirst("^[\\u4e00-\\u9fa5]{2,7}自治区\\s*", "");
        return v.trim();
    }

    private boolean looksLikeCoordinate(String value) {
        String v = value.replace(" ", "").replace("：", ":");
        if (v.contains("经度") || v.contains("纬度") || v.contains("longitude") || v.contains("latitude")) return true;
        return v.matches(".*[-+]?\\d{2,3}\\.\\d{4,}.*[-+]?\\d{1,2}\\.\\d{4,}.*");
    }
}
