package com.example.baseapp.data.device;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.example.baseapp.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 读取本机设备信息，全部取自系统公开接口或可读系统属性；
 * 取不到时返回明确的「本机不提供 / 受系统限制」文案，不构造占位数据。
 */
public final class DeviceInfoCollector {

    /** 用于识别模拟器的特征词（型号、指纹、硬件等任一命中即判定） */
    private static final String[] EMULATOR_TOKENS = {
            "generic", "sdk_gphone", "sdk_google", "emulator", "goldfish", "ranchu",
            "vbox", "qemu", "genymotion", "mumu", "nemu", "bluestacks", "nox",
            "droid4x", "andy"
    };

    /** 常见厂商 ROM 版本属性（属性名，展示用名称） */
    private static final String[][] ROM_PROPS = {
            {"ro.mi.os.version.name", "HyperOS"},
            {"ro.miui.ui.version.name", "MIUI"},
            {"ro.build.version.emui", "EMUI"},
            {"ro.build.version.opporom", "ColorOS"},
            {"ro.build.version.oplusrom", "ColorOS"},
            {"ro.vivo.rom.version", "OriginOS / Funtouch"},
            {"ro.build.version.realmeui", "realme UI"},
            {"hw_sc.build.platform.version", "HarmonyOS"}
    };

    private DeviceInfoCollector() {
    }

    /** 一次性采集全部分组 */
    public static List<InfoGroup> collectAll(Context context, BatterySnapshot battery) {
        List<InfoGroup> groups = new ArrayList<>();
        groups.add(brandGroup(context));
        groups.add(chipGroup(context));
        groups.add(batteryGroup(context, battery));
        groups.add(systemGroup(context));
        return groups;
    }

    /** 品牌与型号分组 */
    public static InfoGroup brandGroup(Context context) {
        List<InfoItem> items = new ArrayList<>();
        items.add(row(context, R.string.device_row_brand, valueOrUnknown(context, Build.BRAND)));
        items.add(row(context, R.string.device_row_manufacturer,
                valueOrUnknown(context, Build.MANUFACTURER)));
        items.add(row(context, R.string.device_row_model, valueOrUnknown(context, Build.MODEL)));
        items.add(row(context, R.string.device_row_device, valueOrUnknown(context, Build.DEVICE)));
        items.add(row(context, R.string.device_row_product, valueOrUnknown(context, Build.PRODUCT)));

        // 模拟器判定：按指纹、硬件、型号等特征词匹配
        List<String> matched = detectEmulatorTokens();
        String emulator = matched.isEmpty()
                ? context.getString(R.string.device_emulator_no)
                : context.getString(R.string.device_emulator_yes,
                TextUtils.join("、", matched));
        items.add(row(context, R.string.device_row_emulator, emulator));
        items.add(row(context, R.string.device_row_emulator_rule,
                context.getString(R.string.device_emulator_rule)));
        items.add(row(context, R.string.device_row_fingerprint,
                valueOrUnknown(context, Build.FINGERPRINT)));
        return new InfoGroup(context.getString(R.string.device_group_brand), items);
    }

    /** 芯片分组 */
    public static InfoGroup chipGroup(Context context) {
        List<InfoItem> items = new ArrayList<>();

        // 芯片厂商：API 31+ 优先读 Build.SOC_MANUFACTURER，低版本回退系统属性
        String socManufacturer = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socManufacturer = meaningful(Build.SOC_MANUFACTURER) ? Build.SOC_MANUFACTURER : "";
        }
        if (socManufacturer.isEmpty()) {
            String prop = getSystemProperty("ro.soc.manufacturer");
            if (meaningful(prop)) {
                socManufacturer = context.getString(R.string.device_value_source,
                        prop, "ro.soc.manufacturer");
            }
        }
        items.add(row(context, R.string.device_row_soc_manufacturer,
                socManufacturer.isEmpty()
                        ? context.getString(R.string.device_value_not_provided_system)
                        : socManufacturer));

        // 芯片型号：API 31+ 用 Build.SOC_MODEL，低版本依次回退属性与 Build 字段
        String socModel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socModel = meaningful(Build.SOC_MODEL) ? Build.SOC_MODEL : "";
        }
        if (socModel.isEmpty()) {
            socModel = firstSystemProperty(context, "ro.soc.model");
        }
        if (socModel.isEmpty()) {
            socModel = firstSystemProperty(context, "ro.board.platform");
        }
        if (socModel.isEmpty()) {
            socModel = firstSystemProperty(context, "ro.hardware");
        }
        if (socModel.isEmpty() && meaningful(Build.HARDWARE)) {
            socModel = context.getString(R.string.device_value_source,
                    Build.HARDWARE, "Build.HARDWARE");
        }
        items.add(row(context, R.string.device_row_soc_model,
                socModel.isEmpty()
                        ? context.getString(R.string.device_value_not_provided_system)
                        : socModel));

        items.add(row(context, R.string.device_row_hardware,
                valueOrUnknown(context, Build.HARDWARE)));
        items.add(row(context, R.string.device_row_board,
                valueOrUnknown(context, Build.BOARD)));
        items.add(row(context, R.string.device_row_abi,
                valueOrUnknown(context, joinSupportedAbis())));

        int cores = Runtime.getRuntime().availableProcessors();
        items.add(row(context, R.string.device_row_cpu_cores,
                context.getString(R.string.device_cpu_cores, cores)));
        return new InfoGroup(context.getString(R.string.device_group_chip), items);
    }

    /** 电池分组（含循环次数与容量近似值） */
    public static InfoGroup batteryGroup(Context context, BatterySnapshot snapshot) {
        List<InfoItem> items = new ArrayList<>();

        items.add(row(context, R.string.device_row_battery_level,
                snapshot.percent >= 0
                        ? context.getString(R.string.device_battery_percent, snapshot.percent)
                        : context.getString(R.string.device_value_not_provided)));
        items.add(row(context, R.string.device_row_battery_status,
                batteryStatusText(context, snapshot.status)));
        items.add(row(context, R.string.device_row_battery_plug,
                batteryPlugText(context, snapshot.plugged)));
        items.add(row(context, R.string.device_row_battery_health,
                batteryHealthText(context, snapshot.health)));
        items.add(row(context, R.string.device_row_battery_temp,
                snapshot.hasTemperature()
                        ? String.format(Locale.getDefault(), "%.1f °C", snapshot.temperatureC)
                        : context.getString(R.string.device_value_not_provided)));

        // 电压低于 1 V 时只显示毫伏，避免出现「0.00 V」的误导性读数
        String voltageText;
        if (snapshot.voltageMv >= 1000) {
            voltageText = context.getString(R.string.device_battery_voltage,
                    snapshot.voltageMv / 1000f, snapshot.voltageMv);
        } else if (snapshot.voltageMv > 0) {
            voltageText = context.getString(R.string.device_battery_voltage_low,
                    snapshot.voltageMv);
        } else {
            voltageText = context.getString(R.string.device_value_not_provided);
        }
        items.add(row(context, R.string.device_row_battery_voltage, voltageText));
        items.add(row(context, R.string.device_row_battery_tech,
                meaningful(snapshot.technology)
                        ? snapshot.technology
                        : context.getString(R.string.device_value_not_provided)));

        // 设计容量：系统未开放公开接口，只能尝试读取电池节点
        long designRaw = readLongFile("/sys/class/power_supply/battery/charge_full_design");
        items.add(row(context, R.string.device_row_battery_design,
                capacityText(context, designRaw,
                        R.string.device_battery_design_uah,
                        R.string.device_battery_design_mah)));

        // 当前容量近似值：优先电量计读数（µAh），读不到再退回满电节点
        BatteryManager batteryManager = (BatteryManager) context
                .getSystemService(Context.BATTERY_SERVICE);
        int chargeCounter = batteryManager != null
                ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                : Integer.MIN_VALUE;
        if (chargeCounter >= 1000) {
            items.add(row(context, R.string.device_row_battery_current,
                    context.getString(R.string.device_battery_current,
                            Math.round(chargeCounter / 1000f))));
        } else if (chargeCounter > 0) {
            // 不足 1 mAh 时按微安时展示，避免取整后显示 0 mAh
            items.add(row(context, R.string.device_row_battery_current,
                    context.getString(R.string.device_battery_current_uah, chargeCounter)));
        } else {
            long chargeFull = readLongFile("/sys/class/power_supply/battery/charge_full");
            items.add(row(context, R.string.device_row_battery_current,
                    capacityText(context, chargeFull,
                            R.string.device_battery_current_full_uah,
                            R.string.device_battery_current_full_mah)));
        }

        // 循环次数：Android 14（API 34）起由电池广播携带，依赖电池驱动上报
        String cycleText;
        if (Build.VERSION.SDK_INT >= 34) {
            cycleText = snapshot.cycleCount > 0
                    ? context.getString(R.string.device_battery_cycle_count,
                    snapshot.cycleCount)
                    : context.getString(R.string.device_battery_cycle_unsupported);
        } else {
            cycleText = context.getString(R.string.device_battery_cycle_no_api);
        }
        items.add(row(context, R.string.device_row_battery_cycle, cycleText));
        return new InfoGroup(context.getString(R.string.device_group_battery), items);
    }

    /** 系统版本与设备标识分组 */
    public static InfoGroup systemGroup(Context context) {
        List<InfoItem> items = new ArrayList<>();
        items.add(row(context, R.string.device_row_android,
                valueOrUnknown(context, Build.VERSION.RELEASE)));
        items.add(row(context, R.string.device_row_api,
                String.valueOf(Build.VERSION.SDK_INT)));

        String securityPatch = Build.VERSION.SECURITY_PATCH;
        items.add(row(context, R.string.device_row_security,
                meaningful(securityPatch)
                        ? securityPatch
                        : context.getString(R.string.device_value_not_provided)));

        items.add(row(context, R.string.device_row_kernel,
                valueOrUnknown(context, System.getProperty("os.version"))));

        String display = valueOrUnknown(context, Build.DISPLAY);
        String buildId = meaningful(Build.ID) ? Build.ID : "";
        items.add(row(context, R.string.device_row_build,
                buildId.isEmpty() ? display : display + "（" + buildId + "）"));

        items.add(row(context, R.string.device_row_rom, romVersionText(context)));
        items.add(row(context, R.string.device_row_serial, serialText(context)));
        items.add(row(context, R.string.device_row_build_serial, buildSerialText(context)));
        items.add(row(context, R.string.device_row_android_id, androidIdText(context)));
        items.add(row(context, R.string.device_row_serial_note,
                context.getString(R.string.device_serial_note)));
        return new InfoGroup(context.getString(R.string.device_group_system), items);
    }

    /** 生成供复制/发送的纯文本，分组以【】标注 */
    public static String toPlainText(Context context, List<InfoGroup> groups) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.device_copy_header, nowText())).append('\n');
        for (InfoGroup group : groups) {
            builder.append('\n').append('【').append(group.title).append('】').append('\n');
            for (InfoItem item : group.items) {
                builder.append(item.label).append('：').append(item.value).append('\n');
            }
        }
        return builder.toString();
    }

    /** 当前时间，用于说明采集/刷新时间 */
    public static String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    /** 是否已获得读取电话状态权限（序列号读取依赖该权限） */
    public static boolean hasPhoneStatePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static InfoItem row(Context context, int labelRes, String value) {
        return new InfoItem(context.getString(labelRes), value);
    }

    /** 空值或 unknown 统一显示为本机不提供 */
    private static String valueOrUnknown(Context context, String value) {
        return meaningful(value) ? value : context.getString(R.string.device_value_not_provided);
    }

    private static boolean meaningful(String value) {
        return !TextUtils.isEmpty(value)
                && !"unknown".equalsIgnoreCase(value.trim());
    }

    /** 拼接本机支持的 ABI 列表（minSdk 24，不能使用 String.join） */
    private static String joinSupportedAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) {
                builder.append(" / ");
            }
            builder.append(abis[i]);
        }
        return builder.toString();
    }

    /** 匹配模拟器特征词，返回命中的特征列表 */
    private static List<String> detectEmulatorTokens() {
        String fingerprint = lower(Build.FINGERPRINT);
        String hardware = lower(Build.HARDWARE);
        String model = lower(Build.MODEL);
        String product = lower(Build.PRODUCT);
        String board = lower(Build.BOARD);
        // 部分模拟器会伪装 Build 字段，补充虚拟化属性作为辅助判定
        String virtualProps = lower(getSystemProperty("ro.kernel.qemu") + " "
                + getSystemProperty("ro.boot.qemu") + " "
                + getSystemProperty("ro.build.hv.platform"));
        List<String> matched = new ArrayList<>();
        for (String token : EMULATOR_TOKENS) {
            if (fingerprint.contains(token) || hardware.contains(token)
                    || model.contains(token) || product.contains(token)
                    || board.contains(token) || virtualProps.contains(token)) {
                matched.add(token);
            }
        }
        return matched;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    /** 读取系统属性，失败（含隐藏 API 限制）时返回空串 */
    private static String getSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Object value = clazz.getMethod("get", String.class).invoke(null, key);
            return value instanceof String ? ((String) value).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 读取属性并附带来源说明，属性为空时返回空串 */
    private static String firstSystemProperty(Context context, String key) {
        String value = getSystemProperty(key);
        return meaningful(value)
                ? context.getString(R.string.device_value_source, value, key)
                : "";
    }

    /** 读取只读系统节点，失败返回 -1 */
    private static long readLongFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            return line == null ? -1 : Long.parseLong(line.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 容量节点转文案：大于 100000 视为 µAh 自动换算为 mAh，否则按 mAh 原样展示。
     */
    private static String capacityText(Context context, long raw,
                                       int uahRes, int mahRes) {
        if (raw <= 0) {
            return context.getString(R.string.device_value_not_provided_system);
        }
        if (raw >= 100000) {
            return context.getString(uahRes, Math.round(raw / 1000f));
        }
        return context.getString(mahRes, raw);
    }

    private static String batteryStatusText(Context context, int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return context.getString(R.string.device_battery_status_charging);
            case BatteryManager.BATTERY_STATUS_FULL:
                return context.getString(R.string.device_battery_status_full);
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return context.getString(R.string.device_battery_status_discharging);
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return context.getString(R.string.device_battery_status_not_charging);
            default:
                return context.getString(R.string.device_value_unknown);
        }
    }

    private static String batteryPlugText(Context context, int plugged) {
        switch (plugged) {
            case 0:
                return context.getString(R.string.device_battery_plug_none);
            case BatteryManager.BATTERY_PLUGGED_AC:
                return context.getString(R.string.device_battery_plug_ac);
            case BatteryManager.BATTERY_PLUGGED_USB:
                return context.getString(R.string.device_battery_plug_usb);
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return context.getString(R.string.device_battery_plug_wireless);
            case BatteryManager.BATTERY_PLUGGED_DOCK:
                return context.getString(R.string.device_battery_plug_dock);
            default:
                return context.getString(R.string.device_value_unknown);
        }
    }

    private static String batteryHealthText(Context context, int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return context.getString(R.string.device_battery_health_good);
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return context.getString(R.string.device_battery_health_overheat);
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return context.getString(R.string.device_battery_health_dead);
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return context.getString(R.string.device_battery_health_over_voltage);
            case BatteryManager.BATTERY_HEALTH_COLD:
                return context.getString(R.string.device_battery_health_cold);
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return context.getString(R.string.device_battery_health_failure);
            default:
                return context.getString(R.string.device_value_unknown);
        }
    }

    /** 厂商 ROM 版本：逐个探测常见属性 */
    private static String romVersionText(Context context) {
        for (String[] entry : ROM_PROPS) {
            String value = getSystemProperty(entry[0]);
            if (meaningful(value)) {
                return context.getString(R.string.device_rom_value,
                        entry[1], value, entry[0]);
            }
        }
        return context.getString(R.string.device_rom_none);
    }

    /** 序列号：需要 READ_PHONE_STATE，Android 10+ 多数返回 unknown */
    private static String serialText(Context context) {
        if (!hasPhoneStatePermission(context)) {
            return context.getString(R.string.device_serial_no_permission);
        }
        try {
            String serial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? Build.getSerial() : Build.SERIAL;
            if (!meaningful(serial)) {
                return context.getString(R.string.device_serial_unknown);
            }
            return serial;
        } catch (SecurityException e) {
            return context.getString(R.string.device_serial_restricted);
        }
    }

    /** Build.SERIAL 自 Android 8 起对普通应用固定返回 unknown */
    private static String buildSerialText(Context context) {
        String serial = Build.SERIAL;
        if (!meaningful(serial)) {
            return context.getString(R.string.device_build_serial_unknown);
        }
        return serial;
    }

    /** Android ID：可作为重装应用后仍相对稳定的参考标识 */
    private static String androidIdText(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return meaningful(androidId)
                ? androidId
                : context.getString(R.string.device_value_not_provided);
    }
}
