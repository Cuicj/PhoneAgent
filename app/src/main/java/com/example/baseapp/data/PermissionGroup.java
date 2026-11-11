package com.example.baseapp.data;

import java.util.List;

/**
 * 权限分组：一个分类下的一组权限条目。
 */
public class PermissionGroup {

    private final PermissionCategory category;
    private final List<PermissionItem> items;

    public PermissionGroup(PermissionCategory category, List<PermissionItem> items) {
        this.category = category;
        this.items = items;
    }

    public PermissionCategory getCategory() {
        return category;
    }

    public List<PermissionItem> getItems() {
        return items;
    }
}
