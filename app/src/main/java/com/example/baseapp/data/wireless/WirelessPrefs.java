package com.example.baseapp.data.wireless;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 记录最近一次扫描时间，供总览页展示。
 */
public final class WirelessPrefs {

    private static final String FILE = "wireless_prefs";
    /** WiFi 最近扫描时间 */
    public static final String KEY_WIFI = "wifi_last_scan";
    /** 蓝牙最近扫描时间 */
    public static final String KEY_BT = "bt_last_scan";

    private WirelessPrefs() {
    }

    public static void saveScanTime(Context context, String key) {
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        prefs(context).edit().putString(key, time).apply();
    }

    /** 返回格式化时间，未扫描过返回 null */
    public static String lastScanTime(Context context, String key) {
        return prefs(context).getString(key, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
