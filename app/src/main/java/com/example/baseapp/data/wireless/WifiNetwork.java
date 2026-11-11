package com.example.baseapp.data.wireless;

import java.util.ArrayList;
import java.util.List;

/**
 * WiFi 热点信息与判定结果。
 */
public class WifiNetwork {

    public final String ssid;
    public final String bssid;
    public final String security;
    public final String band;
    public final String vendor;
    public final int rssi;

    public final List<String> reasons = new ArrayList<>();
    public RiskLevel risk = RiskLevel.NORMAL;

    public WifiNetwork(String ssid, String bssid, int rssi, String band,
                       String security, String vendor) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.band = band;
        this.security = security;
        this.vendor = vendor;
    }

    /** 追加一条判定原因，并同步抬高风险等级 */
    public void addReason(String reason, RiskLevel level) {
        reasons.add(reason);
        risk = RiskLevel.max(risk, level);
    }

    /** 隐藏热点没有 SSID，统一展示为占位文案 */
    public String displaySsid() {
        return ssid == null || ssid.isEmpty() ? "(隐藏网络)" : ssid;
    }
}
