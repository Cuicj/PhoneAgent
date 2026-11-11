package com.example.baseapp.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 红外电源键预设：仅内置少量示例 NEC 编码，实际品牌/型号编码需自行核对。
 */
public final class IrPresets {

    /** 设备类型 */
    public enum DeviceType {
        TV, AC, FAN, PROJECTOR
    }

    /** 单条预设 */
    public static final class Preset {
        private final DeviceType type;
        private final String brand;
        private final int address;
        private final int command;

        Preset(DeviceType type, String brand, int address, int command) {
            this.type = type;
            this.brand = brand;
            this.address = address;
            this.command = command;
        }

        public DeviceType getType() {
            return type;
        }

        public String getBrand() {
            return brand;
        }

        public int getAddress() {
            return address;
        }

        public int getCommand() {
            return command;
        }
    }

    private static final List<Preset> PRESETS = new ArrayList<>();

    static {
        // 以下均为示例码，不保证对应品牌真实电源键
        PRESETS.add(new Preset(DeviceType.TV, "小米（示例）", 0x00FF, 0x00C7));
        PRESETS.add(new Preset(DeviceType.TV, "海信（示例）", 0x00FF, 0x000C));
        PRESETS.add(new Preset(DeviceType.AC, "格力（示例）", 0x00FF, 0x0009));
        PRESETS.add(new Preset(DeviceType.AC, "美的（示例）", 0x00FF, 0x000C));
        PRESETS.add(new Preset(DeviceType.FAN, "美的（示例）", 0x00FF, 0x0002));
        PRESETS.add(new Preset(DeviceType.PROJECTOR, "极米（示例）", 0x00FF, 0x0008));
    }

    private IrPresets() {
    }

    /** 按设备类型筛选预设，{@code type} 为 null 时返回全部 */
    public static List<Preset> filter(DeviceType type) {
        if (type == null) {
            return new ArrayList<>(PRESETS);
        }
        List<Preset> result = new ArrayList<>();
        for (Preset preset : PRESETS) {
            if (preset.getType() == type) {
                result.add(preset);
            }
        }
        return result;
    }
}
