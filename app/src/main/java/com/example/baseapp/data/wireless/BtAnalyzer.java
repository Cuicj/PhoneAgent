package com.example.baseapp.data.wireless;

import java.util.List;
import java.util.Locale;

/**
 * 蓝牙可疑设备判定：追踪器广播特征、持续存在的无名设备、近距离强信号等。
 * 只做启发式判断，结果仅供排查参考。
 */
public final class BtAnalyzer {

    private BtAnalyzer() {
    }

    /** 根据设备特征与本次扫描出现次数，刷新风险与原因 */
    public static void evaluate(BtDevice device) {
        device.reasons.clear();
        device.risk = RiskLevel.NORMAL;

        String tracker = trackerName(device.uuids);
        if (tracker != null) {
            device.addReason("广播包含 " + tracker
                    + " 类追踪器服务特征，可能用于物品追踪（仅按服务 UUID 粗略判断，存在误判）",
                    RiskLevel.HIGH);
        }
        if (device.name.isEmpty() && device.sightings >= 3 && device.rssi >= -70) {
            device.addReason("无名设备在本次扫描中反复出现（" + device.sightings
                    + " 次），且信号不弱，可能在附近持续工作", RiskLevel.WATCH);
        }
        if (device.name.isEmpty() && device.rssi >= -50) {
            device.addReason("信号很强（" + device.rssi + " dBm）但未广播名称，可能就在附近",
                    RiskLevel.WATCH);
        }
        if (!device.name.isEmpty() && device.hasRandomAddress()
                && device.sightings >= 3 && device.rssi >= -60) {
            device.addReason("使用随机蓝牙地址、厂商未知且持续存在，建议留意（随机 MAC 无法反查真实设备）",
                    RiskLevel.WATCH);
        }
    }

    /** 识别常见追踪器服务 UUID，识别不出返回 null */
    private static String trackerName(List<String> uuids) {
        if (uuids == null) {
            return null;
        }
        for (String uuid : uuids) {
            if (uuid == null) {
                continue;
            }
            String value = uuid.toLowerCase(Locale.ROOT);
            if (value.startsWith("0000fd6f") || value.equals("fd6f")) {
                return "Apple Find My";
            }
            if (value.startsWith("0000fd5a") || value.equals("fd5a")) {
                return "Samsung SmartTag";
            }
            if (value.startsWith("0000feed") || value.equals("feed")) {
                return "Tile";
            }
        }
        return null;
    }
}
