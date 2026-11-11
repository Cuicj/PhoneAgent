package com.example.baseapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间线条目：把版本记录与外部访问事件放在同一时间轴上，按时间先后排列。
 * 同一时间戳时版本记录优先，保证版本节点在前、访问节点在后。
 */
public final class TimelineItem {

    /** 版本记录 */
    public static final int TYPE_VERSION = 0;

    /** 外部访问事件 */
    public static final int TYPE_ACCESS = 1;

    private final int type;
    private final FileVersionEntry version;
    private final FileAccessEvent access;
    private final long timestamp;

    private TimelineItem(int type, FileVersionEntry version, FileAccessEvent access,
                         long timestamp) {
        this.type = type;
        this.version = version;
        this.access = access;
        this.timestamp = timestamp;
    }

    public static TimelineItem ofVersion(FileVersionEntry version) {
        return new TimelineItem(TYPE_VERSION, version, null, version.getCreatedAt());
    }

    public static TimelineItem ofAccess(FileAccessEvent access) {
        return new TimelineItem(TYPE_ACCESS, null, access, access.getOccurredAt());
    }

    /** 合并并按时间升序排序，两个列表允许为空 */
    public static List<TimelineItem> merge(List<FileVersionEntry> versions,
                                           List<FileAccessEvent> events) {
        List<TimelineItem> items = new ArrayList<>();
        if (versions != null) {
            for (FileVersionEntry version : versions) {
                items.add(ofVersion(version));
            }
        }
        if (events != null) {
            for (FileAccessEvent event : events) {
                items.add(ofAccess(event));
            }
        }
        Collections.sort(items, (left, right) -> {
            int compare = Long.compare(left.timestamp, right.timestamp);
            return compare != 0 ? compare : Integer.compare(left.type, right.type);
        });
        return items;
    }

    public int getType() {
        return type;
    }

    public FileVersionEntry getVersion() {
        return version;
    }

    public FileAccessEvent getAccess() {
        return access;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
