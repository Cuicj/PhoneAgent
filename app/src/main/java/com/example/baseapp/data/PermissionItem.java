package com.example.baseapp.data;

/**
 * 单个权限条目：区分危险/普通，以及安装时是否即授予。
 */
public class PermissionItem {

    private final String name;
    private final String label;
    private final String description;
    private final boolean dangerous;
    private final boolean installGranted;

    public PermissionItem(String name, String label, String description,
                          boolean dangerous, boolean installGranted) {
        this.name = name;
        this.label = label;
        this.description = description;
        this.dangerous = dangerous;
        this.installGranted = installGranted;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDangerous() {
        return dangerous;
    }

    public boolean isInstallGranted() {
        return installGranted;
    }
}
