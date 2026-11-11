package com.example.baseapp.data.sensor;

/**
 * 分贝估算：对 16 位 PCM 采样计算 RMS，再换算为相对声压级。
 * 数值未经校准，只适合相对比较，不能用于噪声合规判定。
 */
public final class DecibelMath {

    /** 相对声压级的粗略偏移量，不同设备差异很大 */
    private static final double REFERENCE_OFFSET_DB = 90.0;
    /** 避免 log10(0) */
    private static final double MIN_RMS = 1e-7;

    private DecibelMath() {
    }

    /** 计算一段 16 位 PCM 数据的均方根值 */
    public static double rms(short[] data, int length) {
        if (data == null || length <= 0) {
            return 0d;
        }
        double sum = 0d;
        int count = Math.min(length, data.length);
        for (int i = 0; i < count; i++) {
            double sample = data[i];
            sum += sample * sample;
        }
        return Math.sqrt(sum / count);
    }

    /** RMS 转相对声压级（dB），结果限制在 0–130 */
    public static double rmsToDb(double rms) {
        double normalized = Math.max(rms / 32768.0, MIN_RMS);
        double dbfs = 20.0 * Math.log10(normalized);
        return clamp(dbfs + REFERENCE_OFFSET_DB, 0d, 130d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
