package com.example.baseapp.data;

import java.util.List;

/**
 * 应用权限详情：应用基本信息、统计与按类别分组的权限清单。
 */
public class AppPermissionDetail {

    private final String packageName;
    private final String label;
    private final String versionName;
    private final boolean system;
    private final int declaredCount;
    private final int dangerousCount;
    private final int installGrantedCount;
    private final List<PermissionGroup> groups;

    public AppPermissionDetail(String packageName, String label, String versionName,
                               boolean system, int declaredCount, int dangerousCount,
                               int installGrantedCount, List<PermissionGroup> groups) {
        this.packageName = packageName;
        this.label = label;
        this.versionName = versionName;
        this.system = system;
        this.declaredCount = declaredCount;
        this.dangerousCount = dangerousCount;
        this.installGrantedCount = installGrantedCount;
        this.groups = groups;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label;
    }

    public String getVersionName() {
        return versionName;
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

    public int getInstallGrantedCount() {
        return installGrantedCount;
    }

    public List<PermissionGroup> getGroups() {
        return groups;
    }
}
