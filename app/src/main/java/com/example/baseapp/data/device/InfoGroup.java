package com.example.baseapp.data.device;

import java.util.List;

/**
 * 设备信息的一个分组卡片（品牌型号 / 芯片 / 电池 / 系统）。
 */
public final class InfoGroup {

    /** 分组标题 */
    public final String title;
    /** 分组内的字段行 */
    public final List<InfoItem> items;

    public InfoGroup(String title, List<InfoItem> items) {
        this.title = title;
        this.items = items;
    }
}
