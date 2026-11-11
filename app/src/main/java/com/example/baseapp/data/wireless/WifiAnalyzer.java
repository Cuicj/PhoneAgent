package com.example.baseapp.data.wireless;

import android.net.wifi.ScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WiFi 可疑热点判定：开放网络、WEP、同名多 BSSID、品牌与 OUI 不符等。
 * 判定结果仅供排查参考，不能替代专业安全检测。
 */
public final class WifiAnalyzer {

    private WifiAnalyzer() {
    }

    /** 将系统扫描结果转换为带风险信息的列表，高风险排在前面 */
    public static List<WifiNetwork> analyze(List<ScanResult> results) {
        List<WifiNetwork> networks = new ArrayList<>();
        if (results == null) {
            return networks;
        }
        for (ScanResult result : results) {
            if (result == null || result.BSSID == null) {
                continue;
            }
            String security = securityOf(result.capabilities);
            WifiNetwork network = new WifiNetwork(
                    result.SSID == null ? "" : result.SSID,
                    result.BSSID,
                    result.level,
                    bandOf(result.frequency),
                    security,
                    OuiLookup.vendorOf(result.BSSID));
            judgeSingle(network);
            networks.add(network);
        }
        judgeDuplicateSsid(networks);
        networks.sort(Comparator
                .comparingInt((WifiNetwork item) -> item.risk.ordinal())
                .thenComparingInt(item -> -item.rssi));
        return networks;
    }

    /** 单热点规则：开放、WEP、仅 WPA、品牌与 OUI 不符 */
    private static void judgeSingle(WifiNetwork network) {
        String security = network.security;
        if ("开放".equals(security)) {
            network.addReason("开放网络：无需密码即可连接，可能被用于钓鱼或流量窃听", RiskLevel.HIGH);
        } else if ("WEP".equals(security)) {
            network.addReason("使用已淘汰的 WEP 加密，容易被破解，建议更换路由器加密方式", RiskLevel.HIGH);
        } else if ("WPA".equals(security)) {
            network.addReason("仅使用 WPA（TKIP）加密，安全性低于 WPA2/WPA3", RiskLevel.WATCH);
        }
        if (OuiLookup.isBrandMismatch(network.ssid, network.bssid)) {
            network.addReason("SSID 声称的品牌与 BSSID 厂商 OUI 不一致（本地码库有限，仅供参考）",
                    RiskLevel.WATCH);
        }
    }

    /** 同名 SSID 规则：同一名称出现多个 BSSID，可能是仿冒热点或 Mesh 组网 */
    private static void judgeDuplicateSsid(List<WifiNetwork> networks) {
        Map<String, List<WifiNetwork>> groups = new HashMap<>();
        for (WifiNetwork network : networks) {
            if (network.ssid.isEmpty()) {
                continue;
            }
            String key = network.ssid.toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(network);
        }
        for (List<WifiNetwork> group : groups.values()) {
            // 同一 BSSID 可能被系统重复返回，这里按 BSSID 去重后再判断
            Map<String, WifiNetwork> distinct = new HashMap<>();
            for (WifiNetwork network : group) {
                distinct.put(network.bssid, network);
            }
            if (distinct.size() <= 1) {
                continue;
            }
            String reason = "同名 SSID 出现 " + distinct.size()
                    + " 个不同 BSSID，可能是仿冒/钓鱼热点，也可能是 Mesh 组网，请核对路由器 MAC";
            for (WifiNetwork network : group) {
                network.addReason(reason, RiskLevel.HIGH);
            }
        }
    }

    /** 从 capabilities 解析加密方式 */
    private static String securityOf(String capabilities) {
        String caps = capabilities == null ? "" : capabilities.toUpperCase(Locale.ROOT);
        if (caps.contains("WPA3")) {
            return "WPA3";
        }
        if (caps.contains("WPA2")) {
            return "WPA2";
        }
        if (caps.contains("WPA")) {
            return "WPA";
        }
        if (caps.contains("WEP")) {
            return "WEP";
        }
        return "开放";
    }

    /** 根据频率划分频段 */
    private static String bandOf(int frequency) {
        if (frequency >= 5925) {
            return "6 GHz";
        }
        if (frequency >= 4900) {
            return "5 GHz";
        }
        return "2.4 GHz";
    }

    /** dBm 转信号格描述，便于列表阅读 */
    public static String signalLabel(int rssi) {
        if (rssi >= -50) {
            return "很强";
        }
        if (rssi >= -60) {
            return "较强";
        }
        if (rssi >= -70) {
            return "一般";
        }
        if (rssi >= -80) {
            return "较弱";
        }
        return "很弱";
    }
}
