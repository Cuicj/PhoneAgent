package com.example.baseapp.data.device;

/**
 * 设备信息中的一行：左侧字段名，右侧取值。
 */
public final class InfoItem {

    /** 字段名 */
    public final String label;
    /** 取值（取不到时已是「本机不提供」等说明文案） */
    public final String value;

    public InfoItem(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
