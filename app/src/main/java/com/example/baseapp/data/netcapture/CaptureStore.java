package com.example.baseapp.data.netcapture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 抓取记录内存仓库：服务线程写入，界面线程读取快照。
 *
 * <p>只保留最近 {@link #MAX_RECORDS} 条，超过后丢弃最旧记录，避免长时间抓取耗尽内存。
 * 所有方法加锁，保证转发线程与界面线程并发安全。</p>
 */
public final class CaptureStore {

    /** 最多保留的记录数 */
    public static final int MAX_RECORDS = 300;

    private static final CaptureStore INSTANCE = new CaptureStore();

    private final List<CaptureRecord> records = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    private CaptureStore() {
    }

    public static CaptureStore get() {
        return INSTANCE;
    }

    /** 新建记录并插入到列表最前（时间倒序） */
    public synchronized CaptureRecord add(int protocol, String remoteIp, int remotePort) {
        CaptureRecord record = new CaptureRecord(nextId.getAndIncrement(), protocol,
                System.currentTimeMillis(), remoteIp, remotePort);
        records.add(0, record);
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
        return record;
    }

    /** 返回当前记录的副本列表（时间倒序） */
    public synchronized List<CaptureRecord> snapshot() {
        return new ArrayList<>(records);
    }

    /** 按 id 查找记录，已被上限淘汰时返回 null */
    public synchronized CaptureRecord find(long id) {
        for (CaptureRecord record : records) {
            if (record.id == id) {
                return record;
            }
        }
        return null;
    }

    public synchronized int count() {
        return records.size();
    }

    /** 清空所有记录；不影响 VPN 开关状态 */
    public synchronized void clear() {
        records.clear();
    }
}
