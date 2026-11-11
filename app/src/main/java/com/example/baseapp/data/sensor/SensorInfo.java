package com.example.baseapp.data.sensor;

/**
 * 传感器自检清单中的一行数据。
 */
public final class SensorInfo {

    /** 传感器名称 */
    public final String name;
    /** 类型名称（中文或系统类型字符串） */
    public final String typeName;
    /** 厂商 */
    public final String vendor;
    /** 最大量程（原始单位） */
    public final String maxRange;
    /** 分辨率（原始单位） */
    public final String resolution;

    public SensorInfo(String name, String typeName, String vendor,
                      String maxRange, String resolution) {
        this.name = name;
        this.typeName = typeName;
        this.vendor = vendor;
        this.maxRange = maxRange;
        this.resolution = resolution;
    }
}
