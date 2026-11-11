package com.example.baseapp.data.wireless;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝牙设备信息与判定结果，经典蓝牙与 BLE 共用。
 */
public class BtDevice {

    public final String address;
    public String name;
    public int rssi;
    public String typeLabel;
    public String connectableLabel;
    public final List<String> uuids = new ArrayList<>();

    public final String vendor;
    public final List<String> reasons = new ArrayList<>();
    public RiskLevel risk = RiskLevel.NORMAL;
    public int sightings;

    public BtDevice(String address, String name, int rssi, String typeLabel,
                    String connectableLabel, String vendor) {
        this.address = address;
        this.name = name == null ? "" : name;
        this.rssi = rssi;
        this.typeLabel = typeLabel;
        this.connectableLabel = connectableLabel;
        this.vendor = vendor;
    }

    /** 追加一条判定原因，并同步抬高风险等级 */
    public void addReason(String reason, RiskLevel level) {
        reasons.add(reason);
        risk = RiskLevel.max(risk, level);
    }

    /** 无名称设备统一显示为「未知设备」，用于和「可疑」区分 */
    public String displayName() {
        return name.isEmpty() ? "未知设备" : name;
    }

    /** 是否为随机/本地管理地址，用于提示无法反查真实身份 */
    public boolean hasRandomAddress() {
        return OuiLookup.isLocallyAdministered(address);
    }
}
