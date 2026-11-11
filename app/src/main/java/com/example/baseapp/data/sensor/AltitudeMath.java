package com.example.baseapp.data.sensor;

/**
 * 气压高度换算：国际标准大气公式。
 */
public final class AltitudeMath {

    /** 海平面标准气压（hPa） */
    public static final double SEA_LEVEL_HPA = 1013.25;

    private AltitudeMath() {
    }

    /**
     * 气压转海拔高度。
     * h = 44330 × (1 − (p / p0)^(1 / 5.255))
     */
    public static double altitudeMeters(double pressureHpa) {
        if (pressureHpa <= 0d) {
            return Double.NaN;
        }
        return 44330.0 * (1.0 - Math.pow(pressureHpa / SEA_LEVEL_HPA, 1.0 / 5.255));
    }
}
