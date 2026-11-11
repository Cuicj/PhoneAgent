package com.example.baseapp.data.sensor;

import android.hardware.SensorManager;

/**
 * 指南针计算：加速度计 + 磁力计融合得到方位角，并处理屏幕旋转。
 */
public final class CompassMath {

    private CompassMath() {
    }

    /**
     * 计算方位角（0–360，正北为 0，顺时针增加）。
     *
     * @param axisX 屏幕旋转后映射到的 X 轴
     * @param axisY 屏幕旋转后映射到的 Y 轴
     * @return 计算失败时返回 -1
     */
    public static float azimuthDegrees(float[] accel, float[] mag, float[] rotation,
                                       float[] remapped, float[] orientation,
                                       int axisX, int axisY) {
        if (!SensorManager.getRotationMatrix(rotation, null, accel, mag)) {
            return -1f;
        }
        if (!SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)) {
            return -1f;
        }
        SensorManager.getOrientation(remapped, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        if (degrees < 0f) {
            degrees += 360f;
        }
        return degrees;
    }

    /** 圆周平滑，避免 0°/360° 附近跳变 */
    public static float smooth(float previous, float current, float factor) {
        if (previous < 0f) {
            return current;
        }
        float diff = current - previous;
        if (diff > 180f) {
            diff -= 360f;
        } else if (diff < -180f) {
            diff += 360f;
        }
        float result = previous + diff * factor;
        if (result < 0f) {
            result += 360f;
        } else if (result >= 360f) {
            result -= 360f;
        }
        return result;
    }

    /** 方位角对应的八方向索引：0 北、1 东北、2 东…… */
    public static int directionIndex(float azimuth) {
        return (int) (((azimuth + 22.5f) % 360f) / 45f);
    }
}
