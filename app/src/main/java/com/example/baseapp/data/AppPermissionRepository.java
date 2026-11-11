package com.example.baseapp.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用权限仓库：后台线程读取已安装应用与权限声明，主线程回调给界面。
 * 只读取清单中声明的权限与安装时授予标记，不尝试读取运行时开关。
 */
public final class AppPermissionRepository {

    /** 异步结果回调，均在主线程触发 */
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(@NonNull Exception error);
    }

    private static volatile AppPermissionRepository instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 应用列表内存缓存，避免从详情返回时重复扫描 */
    private volatile List<InstalledApp> cachedApps;

    private AppPermissionRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static AppPermissionRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AppPermissionRepository.class) {
                if (instance == null) {
                    instance = new AppPermissionRepository(context);
                }
            }
        }
        return instance;
    }

    /**
     * 读取本机应用列表。已有缓存时直接回调，{@code forceReload} 为 true 时重新扫描。
     */
    public void loadApps(boolean forceReload, @NonNull Callback<List<InstalledApp>> callback) {
        List<InstalledApp> cached = cachedApps;
        if (!forceReload && cached != null) {
            callback.onSuccess(cached);
            return;
        }
        executor.execute(() -> {
            try {
                List<InstalledApp> apps = scanApps();
                cachedApps = apps;
                mainHandler.post(() -> callback.onSuccess(apps));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /** 读取指定应用的权限详情，按类别分组并区分危险权限 */
    public void loadDetail(String packageName, @NonNull Callback<AppPermissionDetail> callback) {
        executor.execute(() -> {
            try {
                AppPermissionDetail detail = readDetail(packageName);
                mainHandler.post(() -> callback.onSuccess(detail));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private List<InstalledApp> scanApps() {
        PackageManager pm = appContext.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        List<InstalledApp> apps = new ArrayList<>(packages.size());
        for (PackageInfo info : packages) {
            ApplicationInfo applicationInfo = info.applicationInfo;
            if (applicationInfo == null) {
                continue;
            }
            String[] permissions = info.requestedPermissions;
            int declaredCount = permissions == null ? 0 : permissions.length;
            int dangerousCount = 0;
            Set<PermissionCategory> categories = EnumSet.noneOf(PermissionCategory.class);
            if (permissions != null) {
                for (String permission : permissions) {
                    PermissionInfo permissionInfo = resolvePermission(pm, permission);
                    if (permissionInfo == null) {
                        continue;
                    }
                    // 危险权限按系统 protection level 判定；appop 特殊权限（如所有文件访问）同属高敏感
                    if (isSensitive(permissionInfo)) {
                        dangerousCount++;
                    }
                    categories.add(PermissionCategory.from(permission, permissionInfo.group));
                }
            }
            boolean system = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String label = pm.getApplicationLabel(applicationInfo).toString();
            apps.add(new InstalledApp(info.packageName, label, system,
                    declaredCount, dangerousCount, categories));
        }
        // 用户应用优先，其次按名称排序，保证列表稳定易读
        Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(apps, (left, right) -> {
            if (left.isSystem() != right.isSystem()) {
                return left.isSystem() ? 1 : -1;
            }
            return collator.compare(left.getLabel(), right.getLabel());
        });
        return apps;
    }

    private AppPermissionDetail readDetail(String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = appContext.getPackageManager();
        PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        ApplicationInfo applicationInfo = info.applicationInfo;
        String label = applicationInfo == null
                ? packageName : pm.getApplicationLabel(applicationInfo).toString();
        boolean system = applicationInfo != null
                && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

        String[] permissions = info.requestedPermissions;
        int[] flags = info.requestedPermissionsFlags;
        int declaredCount = permissions == null ? 0 : permissions.length;
        int dangerousCount = 0;
        int installGrantedCount = 0;
        Map<PermissionCategory, List<PermissionItem>> grouped =
                new EnumMap<>(PermissionCategory.class);
        if (permissions != null) {
            for (int i = 0; i < permissions.length; i++) {
                String name = permissions[i];
                PermissionInfo permissionInfo = resolvePermission(pm, name);
                boolean dangerous = permissionInfo != null && isSensitive(permissionInfo);
                // 安装时授予标记：只能反映安装时的状态，不代表当前运行时开关
                boolean granted = flags != null && i < flags.length
                        && (flags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                if (dangerous) {
                    dangerousCount++;
                }
                if (granted) {
                    installGrantedCount++;
                }
                String permissionLabel = permissionInfo == null
                        ? name : permissionInfo.loadLabel(pm).toString();
                CharSequence description = permissionInfo == null
                        ? null : permissionInfo.loadDescription(pm);
                PermissionCategory category = PermissionCategory.from(name,
                        permissionInfo == null ? null : permissionInfo.group);
                List<PermissionItem> items = grouped.get(category);
                if (items == null) {
                    items = new ArrayList<>();
                    grouped.put(category, items);
                }
                items.add(new PermissionItem(name, permissionLabel,
                        description == null ? null : description.toString().trim(),
                        dangerous, granted));
            }
        }

        // 分组顺序按枚举定义，组内危险权限优先
        List<PermissionGroup> groups = new ArrayList<>(grouped.size());
        for (PermissionCategory category : PermissionCategory.values()) {
            List<PermissionItem> items = grouped.get(category);
            if (items == null || items.isEmpty()) {
                continue;
            }
            Collections.sort(items, (left, right) -> {
                if (left.isDangerous() != right.isDangerous()) {
                    return left.isDangerous() ? -1 : 1;
                }
                return left.getLabel().compareToIgnoreCase(right.getLabel());
            });
            groups.add(new PermissionGroup(category, items));
        }

        return new AppPermissionDetail(packageName, label, info.versionName, system,
                declaredCount, dangerousCount, installGrantedCount, groups);
    }

    /** 系统权限可能不存在（自定义权限被卸载等），失败时按未知权限处理 */
    private PermissionInfo resolvePermission(PackageManager pm, String permission) {
        try {
            return pm.getPermissionInfo(permission, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * 判定敏感权限：dangerous 级，或带 appop 标记的特殊权限
     * （如 MANAGE_EXTERNAL_STORAGE、SYSTEM_ALERT_WINDOW），界面统一按危险展示。
     */
    private static boolean isSensitive(PermissionInfo info) {
        return info.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                || (info.protectionLevel & PermissionInfo.PROTECTION_FLAG_APPOP) != 0;
    }
}
