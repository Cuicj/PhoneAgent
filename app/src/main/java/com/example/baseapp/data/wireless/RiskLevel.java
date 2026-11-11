package com.example.baseapp.data.wireless;

/**
 * 风险等级：高风险 / 留意 / 正常。
 */
public enum RiskLevel {
    HIGH,
    WATCH,
    NORMAL;

    /** 取两个等级中更高的一个，用于多条规则叠加 */
    public static RiskLevel max(RiskLevel first, RiskLevel second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }
}
