package com.example.baseapp.data.device;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;

/**
 * 电池广播（ACTION_BATTERY_CHANGED）的一次快照，字段缺省时保留系统原始缺省值。
 */
public final class BatterySnapshot {

    /** 电量百分比，-1 表示系统未提供 */
    public final int percent;
    /** BatteryManager.BATTERY_STATUS_*，-1 表示系统未提供 */
    public final int status;
    /** BatteryManager.BATTERY_PLUGGED_*，-1 表示系统未提供 */
    public final int plugged;
    /** BatteryManager.BATTERY_HEALTH_*，-1 表示系统未提供 */
    public final int health;
    /** 温度（摄氏度），NaN 表示系统未提供 */
    public final float temperatureC;
    /** 电压（毫伏），-1 表示系统未提供 */
    public final int voltageMv;
    /** 电池技术类型（如 Li-ion），null 表示系统未提供 */
    public final String technology;
    /** 循环次数，-1 表示系统未提供（Android 14 起由电池广播携带） */
    public final int cycleCount;

    private BatterySnapshot(int percent, int status, int plugged, int health,
                            float temperatureC, int voltageMv, String technology,
                            int cycleCount) {
        this.percent = percent;
        this.status = status;
        this.plugged = plugged;
        this.health = health;
        this.temperatureC = temperatureC;
        this.voltageMv = voltageMv;
        this.technology = technology;
        this.cycleCount = cycleCount;
    }

    /** 从电池广播构造快照；intent 为空时返回全缺省快照 */
    public static BatterySnapshot from(Intent intent) {
        if (intent == null) {
            return new BatterySnapshot(-1, -1, -1, -1, Float.NaN, -1, null, -1);
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        // 部分机型只给 level 不给 scale，此时无法换算百分比
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                Integer.MIN_VALUE);
        // EXTRA_TEMPERATURE 单位为 0.1 摄氏度
        float temperatureC = temperature == Integer.MIN_VALUE
                ? Float.NaN : temperature / 10f;
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        // 循环次数为 Android 14（API 34）新增字段，由电池广播携带
        int cycleCount = -1;
        if (Build.VERSION.SDK_INT >= 34) {
            cycleCount = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
        }
        return new BatterySnapshot(percent, status, plugged, health,
                temperatureC, voltage, technology, cycleCount);
    }

    /** 系统是否提供了温度值 */
    public boolean hasTemperature() {
        return !Float.isNaN(temperatureC);
    }
}
