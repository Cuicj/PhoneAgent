package com.example.baseapp.data;

import java.util.Collections;
import java.util.Set;

/**
 * 应用列表项：只保留列表展示与筛选需要的轻量字段，图标由界面异步加载。
 */
public class InstalledApp {

    private final String packageName;
    private final String label;
    private final boolean system;
    private final int declaredCount;
    private final int dangerousCount;
    private final Set<PermissionCategory> categories;

    public InstalledApp(String packageName, String label, boolean system,
                        int declaredCount, int dangerousCount,
                        Set<PermissionCategory> categories) {
        this.packageName = packageName;
        this.label = label;
        this.system = system;
        this.declaredCount = declaredCount;
        this.dangerousCount = dangerousCount;
        this.categories = categories == null ? Collections.emptySet() : categories;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSystem() {
        return system;
    }

    public int getDeclaredCount() {
        return declaredCount;
    }

    public int getDangerousCount() {
        return dangerousCount;
    }

    /** 该应用是否声明了指定类别的权限，用于列表筛选 */
    public boolean hasCategory(PermissionCategory category) {
        return categories.contains(category);
    }
}
