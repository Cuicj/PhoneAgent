package com.example.baseapp.data;

import com.example.baseapp.util.Formats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 版本描述构建器：统一生成类似 Git 提交说明的自然文案。
 * 所有描述在写入时生成并落库，保证任何入口打开时文案一致。
 */
public final class VersionDescriptionBuilder {

    private VersionDescriptionBuilder() {
    }

    /** 原始导入描述 */
    public static String forOriginal(int width, int height, long sizeBytes,
                                     String mimeType, VersionSource source) {
        return "导入原始图片：" + describeContent(width, height, sizeBytes, mimeType)
                + "，来源：" + sourceLabel(source) + "。";
    }

    /** 追加新版本描述，尽量给出与上一版的差异 */
    public static String forAdded(FileVersionEntry previous, int width, int height,
                                  long sizeBytes, String mimeType, VersionSource source) {
        if (previous == null) {
            return "追加新版本：" + describeContent(width, height, sizeBytes, mimeType)
                    + "，来源：" + sourceLabel(source) + "。";
        }
        List<String> changes = collectChanges(previous.getWidth(), previous.getHeight(),
                previous.getSizeBytes(), previous.getMimeType(),
                width, height, sizeBytes, mimeType);
        if (changes.isEmpty()) {
            return "追加新版本：图片元信息与上一版一致（"
                    + describeContent(width, height, sizeBytes, mimeType)
                    + "），来源：" + sourceLabel(source) + "。";
        }
        return "追加新版本：" + join(changes) + "；来源：" + sourceLabel(source) + "。";
    }

    /** 回退描述：说明回退目标版本以及相对当前版本的差异 */
    public static String forRevert(FileVersionEntry previous, FileVersionEntry target,
                                   VersionSource source) {
        StringBuilder builder = new StringBuilder("回退到第 ")
                .append(target.getVersionNo()).append(" 版（")
                .append(describeContent(target.getWidth(), target.getHeight(),
                        target.getSizeBytes(), target.getMimeType()))
                .append("）");
        if (previous != null) {
            List<String> changes = collectChanges(previous.getWidth(), previous.getHeight(),
                    previous.getSizeBytes(), previous.getMimeType(),
                    target.getWidth(), target.getHeight(), target.getSizeBytes(),
                    target.getMimeType());
            if (!changes.isEmpty()) {
                builder.append("：").append(join(changes));
            }
        }
        return builder.append("；来源：").append(sourceLabel(source)).append("。").toString();
    }

    /** 删除版本描述 */
    public static String forDeleted(FileVersionEntry target, int newLatestVersionNo) {
        StringBuilder builder = new StringBuilder("删除第 ")
                .append(target.getVersionNo()).append(" 版（")
                .append(describeContent(target.getWidth(), target.getHeight(),
                        target.getSizeBytes(), target.getMimeType()))
                .append("），图片副本已从本机移除");
        if (newLatestVersionNo > 0) {
            builder.append("，第 ").append(newLatestVersionNo).append(" 版成为最新版本");
        }
        return builder.append("。").toString();
    }

    /** 收集尺寸、体积、格式三项差异，没有差异时返回空列表 */
    private static List<String> collectChanges(int prevWidth, int prevHeight, long prevSize,
                                               String prevMime, int width, int height,
                                               long size, String mime) {
        List<String> changes = new ArrayList<>();
        String dimension = describeDimensionChange(prevWidth, prevHeight, width, height);
        if (dimension != null) {
            changes.add(dimension);
        }
        String sizeChange = describeSizeChange(prevSize, size);
        if (sizeChange != null) {
            changes.add(sizeChange);
        }
        String format = describeFormatChange(prevMime, mime);
        if (format != null) {
            changes.add(format);
        }
        return changes;
    }

    private static String describeDimensionChange(int prevWidth, int prevHeight,
                                                  int width, int height) {
        if (prevWidth <= 0 || prevHeight <= 0 || width <= 0 || height <= 0) {
            return null;
        }
        if (prevWidth == width && prevHeight == height) {
            return "尺寸保持 " + width + "×" + height;
        }
        return "尺寸 " + prevWidth + "×" + prevHeight + " → " + width + "×" + height;
    }

    private static String describeSizeChange(long prevSize, long size) {
        if (prevSize <= 0) {
            return null;
        }
        if (prevSize == size) {
            return "体积保持 " + Formats.formatBytes(size);
        }
        long delta = size - prevSize;
        int percent = (int) Math.round(Math.abs(delta) * 100.0 / prevSize);
        String direction = delta > 0 ? "增加" : "减少";
        return "体积 " + Formats.formatBytes(prevSize) + " → " + Formats.formatBytes(size)
                + "（" + direction + " " + percent + "%）";
    }

    private static String describeFormatChange(String prevMime, String mime) {
        String previous = formatLabel(prevMime);
        String current = formatLabel(mime);
        if (previous == null || current == null || previous.equals(current)) {
            return null;
        }
        return "格式 " + previous + " → " + current;
    }

    /** 当前版本的尺寸、体积与格式 */
    private static String describeContent(int width, int height, long sizeBytes, String mimeType) {
        StringBuilder builder = new StringBuilder();
        if (width > 0 && height > 0) {
            builder.append(width).append("×").append(height).append("，");
        }
        builder.append(Formats.formatBytes(sizeBytes));
        String label = formatLabel(mimeType);
        if (label != null) {
            builder.append("，").append(label);
        }
        return builder.toString();
    }

    private static String formatLabel(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        int slash = mimeType.indexOf('/');
        String subtype = slash >= 0 ? mimeType.substring(slash + 1) : mimeType;
        return subtype.isEmpty() ? null : subtype.toUpperCase(Locale.US);
    }

    private static String sourceLabel(VersionSource source) {
        return (source == null ? VersionSource.UNKNOWN : source).getLabel();
    }

    private static String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }
}
