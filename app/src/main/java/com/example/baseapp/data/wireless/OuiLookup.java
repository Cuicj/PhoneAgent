package com.example.baseapp.data.wireless;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地 OUI 码库：只覆盖少量常见厂商，用于「SSID 声称品牌与 BSSID 厂商是否一致」的粗略参考。
 * 码库不完整，未收录不等于异常。
 */
public final class OuiLookup {

    public static final String UNKNOWN_VENDOR = "未知厂商";

    private static final Map<String, String> OUI = new HashMap<>();

    static {
        // 小米 / 红米
        put("00:9A:CD", "小米");
        put("50:8F:4C", "小米");
        put("64:09:80", "小米");
        put("78:11:DC", "小米");
        put("8C:53:C3", "小米");
        put("F0:B4:29", "小米");
        // 华为
        put("00:E0:FC", "华为");
        put("28:6E:D4", "华为");
        put("48:46:FB", "华为");
        put("70:72:3C", "华为");
        put("AC:E2:15", "华为");
        // TP-Link / 普联
        put("14:CC:20", "普联");
        put("50:C7:BF", "普联");
        put("A4:2B:B0", "普联");
        put("C0:25:E9", "普联");
        put("C4:E9:84", "普联");
        // 腾达
        put("C8:3A:35", "腾达");
        put("00:B0:0C", "腾达");
        // 华三
        put("00:0F:E2", "华三");
        put("00:1D:0F", "华三");
        // 网件
        put("A0:40:A0", "网件");
        put("E8:FC:AF", "网件");
        // 华硕
        put("04:D4:C4", "华硕");
        put("2C:FD:A1", "华硕");
        // 友讯
        put("00:1B:11", "友讯");
        put("1C:7E:E5", "友讯");
    }

    private OuiLookup() {
    }

    private static void put(String prefix, String vendor) {
        OUI.put(prefix, vendor);
    }

    /** 取 MAC 前 3 字节的厂商名，未收录返回「未知厂商」 */
    public static String vendorOf(String mac) {
        String prefix = prefixOf(mac);
        if (prefix == null) {
            return UNKNOWN_VENDOR;
        }
        String vendor = OUI.get(prefix);
        return vendor == null ? UNKNOWN_VENDOR : vendor;
    }

    /** 判断某个 SSID 的品牌关键词与 BSSID 厂商是否明显不一致 */
    public static boolean isBrandMismatch(String ssid, String mac) {
        if (ssid == null || ssid.isEmpty()) {
            return false;
        }
        String vendor = vendorOf(mac);
        if (UNKNOWN_VENDOR.equals(vendor)) {
            // 厂商未知时不做判断，避免误报
            return false;
        }
        String lower = ssid.toLowerCase(Locale.ROOT);
        String expected = brandOfSsid(lower);
        return expected != null && !expected.equals(vendor);
    }

    /** SSID 中出现品牌关键词时，返回本地码库里的对应厂商名 */
    private static String brandOfSsid(String lowerSsid) {
        if (lowerSsid.contains("xiaomi") || lowerSsid.contains("xiaomi_")
                || lowerSsid.contains("小米") || lowerSsid.contains("redmi")
                || lowerSsid.contains("红米")) {
            return "小米";
        }
        if (lowerSsid.contains("huawei") || lowerSsid.contains("华为")) {
            return "华为";
        }
        if (lowerSsid.contains("tplink") || lowerSsid.contains("tp-link")
                || lowerSsid.contains("普联")) {
            return "普联";
        }
        if (lowerSsid.contains("tenda") || lowerSsid.contains("腾达")) {
            return "腾达";
        }
        if (lowerSsid.contains("h3c") || lowerSsid.contains("华三")) {
            return "华三";
        }
        if (lowerSsid.contains("netgear") || lowerSsid.contains("网件")) {
            return "网件";
        }
        if (lowerSsid.contains("asus") || lowerSsid.contains("华硕")) {
            return "华硕";
        }
        if (lowerSsid.contains("dlink") || lowerSsid.contains("d-link")
                || lowerSsid.contains("友讯")) {
            return "友讯";
        }
        return null;
    }

    /** 本地管理的随机 MAC 常见于蓝牙设备，这里只做展示提示 */
    public static boolean isLocallyAdministered(String mac) {
        String prefix = prefixOf(mac);
        if (prefix == null) {
            return false;
        }
        try {
            int firstByte = Integer.parseInt(prefix.substring(0, 2), 16);
            return (firstByte & 0x02) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String prefixOf(String mac) {
        if (mac == null || mac.length() < 8) {
            return null;
        }
        return mac.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** 常见品牌关键词，用于蓝牙「异常厂商」的粗略提示 */
    public static Set<String> knownVendors() {
        return new HashSet<>(OUI.values());
    }
}
