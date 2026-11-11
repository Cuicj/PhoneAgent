package com.example.baseapp.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 界面展示用的格式化工具。
 */
public final class Formats {

    private static final SimpleDateFormat DATE_TIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private Formats() {
    }

    /** 毫秒时间戳格式化为 yyyy-MM-dd HH:mm */
    public static synchronized String formatTime(long timeMillis) {
        return DATE_TIME.format(new Date(timeMillis));
    }

    /** 字节数转换为带单位的可读文本 */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.CHINA, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.CHINA, "%.1f MB", mb);
        }
        return String.format(Locale.CHINA, "%.2f GB", mb / 1024.0);
    }

    /** 提取异常的可读信息，避免界面出现空提示 */
    public static String errorMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    /** 将 image/jpeg 之类的 MIME 转成便于阅读的格式名 */
    public static String formatMime(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return "未知格式";
        }
        int slash = mimeType.indexOf('/');
        String subtype = slash >= 0 ? mimeType.substring(slash + 1) : mimeType;
        return subtype.isEmpty() ? "未知格式" : subtype.toUpperCase(Locale.US);
    }
}
