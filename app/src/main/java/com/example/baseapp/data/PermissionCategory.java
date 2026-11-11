package com.example.baseapp.data;

import androidx.annotation.StringRes;

import com.example.baseapp.R;

import java.util.Locale;

/**
 * 权限分组：按常见敏感能力归类，无法识别的权限落到「其他」。
 */
public enum PermissionCategory {

    /** 存储 / 媒体 */
    STORAGE(R.string.perm_category_storage),
    /** 相机 */
    CAMERA(R.string.perm_category_camera),
    /** 麦克风 */
    MICROPHONE(R.string.perm_category_microphone),
    /** 位置 */
    LOCATION(R.string.perm_category_location),
    /** 通讯录 */
    CONTACTS(R.string.perm_category_contacts),
    /** 电话 / 短信 */
    PHONE_SMS(R.string.perm_category_phone_sms),
    /** 日历 */
    CALENDAR(R.string.perm_category_calendar),
    /** 传感器 */
    SENSORS(R.string.perm_category_sensors),
    /** 通知 */
    NOTIFICATION(R.string.perm_category_notification),
    /** 其他（网络、前台服务、唤醒锁等普通权限） */
    OTHER(R.string.perm_category_other);

    @StringRes
    private final int titleRes;

    PermissionCategory(@StringRes int titleRes) {
        this.titleRes = titleRes;
    }

    @StringRes
    public int getTitleRes() {
        return titleRes;
    }

    /**
     * 根据权限名与系统分组名推断分类。
     * 优先使用更直观的权限名关键字，未命中时回退到系统分组名。
     */
    public static PermissionCategory from(String permissionName, String groupName) {
        String name = permissionName == null ? "" : permissionName.toLowerCase(Locale.US);
        if (name.contains("camera")) {
            return CAMERA;
        }
        if (name.contains("record_audio") || name.contains("microphone")) {
            return MICROPHONE;
        }
        if (name.contains("location")) {
            return LOCATION;
        }
        if (name.contains("contact") || name.contains("get_accounts")) {
            return CONTACTS;
        }
        if (name.contains("calendar")) {
            return CALENDAR;
        }
        if (name.contains("sensor") || name.contains("activity_recognition")) {
            return SENSORS;
        }
        if (name.contains("notification")) {
            return NOTIFICATION;
        }
        if (name.contains("sms") || name.contains("mms") || name.contains("call")
                || name.contains("phone") || name.contains("telecom")) {
            return PHONE_SMS;
        }
        if (name.contains("storage") || name.contains("media") || name.contains("external")) {
            return STORAGE;
        }
        return fromGroup(groupName);
    }

    /** 系统分组名回退映射，覆盖权限名无法判断的场景 */
    private static PermissionCategory fromGroup(String groupName) {
        String group = groupName == null ? "" : groupName.toLowerCase(Locale.US);
        if (group.contains("storage") || group.contains("media")) {
            return STORAGE;
        }
        if (group.contains("camera")) {
            return CAMERA;
        }
        if (group.contains("microphone")) {
            return MICROPHONE;
        }
        if (group.contains("location")) {
            return LOCATION;
        }
        if (group.contains("contact")) {
            return CONTACTS;
        }
        if (group.contains("phone") || group.contains("sms") || group.contains("call")) {
            return PHONE_SMS;
        }
        if (group.contains("calendar")) {
            return CALENDAR;
        }
        if (group.contains("sensor") || group.contains("activity_recognition")) {
            return SENSORS;
        }
        if (group.contains("notification")) {
            return NOTIFICATION;
        }
        return OTHER;
    }
}
