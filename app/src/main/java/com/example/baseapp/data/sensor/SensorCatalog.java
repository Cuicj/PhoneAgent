package com.example.baseapp.data.sensor;

import android.content.Context;
import android.hardware.Sensor;

import com.example.baseapp.R;

import java.util.Locale;

/**
 * 传感器类型名称、关键传感器判定与数值格式化。
 */
public final class SensorCatalog {

    /** 关键传感器：缺失时需要在自检页单独提示 */
    public static final int[] KEY_TYPES = {
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_PRESSURE
    };

    private SensorCatalog() {
    }

    /** 关键传感器的中文名称 */
    public static String keyName(Context context, int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                return context.getString(R.string.sensor_name_accelerometer);
            case Sensor.TYPE_GYROSCOPE:
                return context.getString(R.string.sensor_name_gyroscope);
            case Sensor.TYPE_MAGNETIC_FIELD:
                return context.getString(R.string.sensor_name_magnetometer);
            case Sensor.TYPE_PRESSURE:
                return context.getString(R.string.sensor_name_barometer);
            default:
                return String.valueOf(type);
        }
    }

    /** 常见类型显示中文名称，其余回退到系统类型字符串 */
    public static String typeName(Context context, Sensor sensor) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                return context.getString(R.string.sensor_name_accelerometer);
            case Sensor.TYPE_GYROSCOPE:
                return context.getString(R.string.sensor_name_gyroscope);
            case Sensor.TYPE_MAGNETIC_FIELD:
                return context.getString(R.string.sensor_name_magnetometer);
            case Sensor.TYPE_PRESSURE:
                return context.getString(R.string.sensor_name_barometer);
            case Sensor.TYPE_LIGHT:
                return context.getString(R.string.sensor_name_light);
            case Sensor.TYPE_PROXIMITY:
                return context.getString(R.string.sensor_name_proximity);
            case Sensor.TYPE_GRAVITY:
                return context.getString(R.string.sensor_name_gravity);
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return context.getString(R.string.sensor_name_linear_acceleration);
            case Sensor.TYPE_ROTATION_VECTOR:
                return context.getString(R.string.sensor_name_rotation_vector);
            case Sensor.TYPE_STEP_COUNTER:
                return context.getString(R.string.sensor_name_step_counter);
            case Sensor.TYPE_STEP_DETECTOR:
                return context.getString(R.string.sensor_name_step_detector);
            case Sensor.TYPE_HEART_RATE:
                return context.getString(R.string.sensor_name_heart_rate);
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return context.getString(R.string.sensor_name_game_rotation_vector);
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                return context.getString(R.string.sensor_name_significant_motion);
            case Sensor.TYPE_STATIONARY_DETECT:
                return context.getString(R.string.sensor_name_stationary_detect);
            case Sensor.TYPE_MOTION_DETECT:
                return context.getString(R.string.sensor_name_motion_detect);
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return context.getString(R.string.sensor_name_humidity);
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return context.getString(R.string.sensor_name_ambient_temperature);
            default:
                String systemType = sensor.getStringType();
                return systemType == null ? String.valueOf(sensor.getType()) : systemType;
        }
    }

    /** 返回缺失的关键传感器名称，用「、」连接；全部存在时返回空串 */
    public static String missingKeyNames(Context context) {
        android.hardware.SensorManager manager = (android.hardware.SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int type : KEY_TYPES) {
            Sensor sensor = manager.getDefaultSensor(type);
            if (sensor == null) {
                if (builder.length() > 0) {
                    builder.append('、');
                }
                builder.append(keyName(context, type));
            }
        }
        return builder.toString();
    }

    /** 依据 minDelay（微秒）估算最大采样率，未知时返回 0 */
    public static float maxSampleRate(Sensor sensor) {
        int minDelay = sensor.getMinDelay();
        return minDelay > 0 ? 1_000_000f / minDelay : 0f;
    }

    /** 按数值大小选择合适的小数位，避免清单里出现过长的小数 */
    public static String formatValue(float value) {
        if (value >= 100f) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (value >= 1f) {
            return String.format(Locale.US, "%.2f", value);
        }
        return String.format(Locale.US, "%.4f", value);
    }
}
