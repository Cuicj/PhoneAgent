package com.example.baseapp.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件访问与授权仓库。
 * 同步方法供 ContentProvider 在 Binder 线程直接校验与落库；
 * 异步方法供界面读取授权列表、添加/取消授权。
 */
public final class FileAccessRepository {

    private static volatile FileAccessRepository instance;

    private final AppDatabase database;
    private final VersionFileStore fileStore;
    private final PackageManager packageManager;
    private final String ownPackage;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FileAccessRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.database = AppDatabase.getInstance(appContext);
        this.fileStore = new VersionFileStore(appContext);
        this.packageManager = appContext.getPackageManager();
        this.ownPackage = appContext.getPackageName();
    }

    public static FileAccessRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (FileAccessRepository.class) {
                if (instance == null) {
                    instance = new FileAccessRepository(context);
                }
            }
        }
        return instance;
    }

    /** 可授权的应用：包名 + 展示名称 */
    public static final class GrantableApp {
        private final String packageName;
        private final String label;

        GrantableApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Provider 校验：调用方是否已被授权查看该文件 */
    public boolean isGrantedSync(long fileId, String packageName) {
        return database.fileAppGrantDao().count(fileId, packageName) > 0;
    }

    /** Provider 读取当前最新有效版本，供确定访问的版本号与图片来源 */
    public FileVersionEntry latestActiveSync(long fileId) {
        return database.fileVersionDao().getLatestActive(fileId);
    }

    /** 相对路径转绝对路径，供 Provider 打开图片文件 */
    public String resolvePath(String relativePath) {
        return fileStore.resolveAbsolutePath(relativePath);
    }

    /** Provider 写入访问事件；失败只影响审计记录，不阻断访问流程 */
    public void recordEventSync(FileAccessEvent event) {
        try {
            database.fileAccessEventDao().insert(event);
        } catch (RuntimeException ignored) {
            // 审计写入失败不应该让外部读取直接失败
        }
    }

    /** 解析应用名称，应用已卸载时回退为包名 */
    public String resolveAppLabel(String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /** 读取某个文件的已授权应用列表 */
    public void loadGrants(long fileId,
                           @NonNull FileVersionRepository.Callback<List<FileAppGrant>> callback) {
        ioExecutor.execute(() -> {
            try {
                List<FileAppGrant> grants = database.fileAppGrantDao().listByFile(fileId);
                mainHandler.post(() -> callback.onSuccess(grants));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** 查询可授权的外部应用：能响应图片查看、且尚未授权 */
    public void loadGrantableApps(long fileId,
                                  @NonNull FileVersionRepository.Callback<List<GrantableApp>> callback) {
        ioExecutor.execute(() -> {
            try {
                Set<String> granted = new HashSet<>();
                List<FileAppGrant> grants = database.fileAppGrantDao().listByFile(fileId);
                if (grants != null) {
                    for (FileAppGrant grant : grants) {
                        granted.add(grant.getPackageName());
                    }
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType("image/*");
                List<ResolveInfo> resolved = packageManager.queryIntentActivities(intent, 0);
                List<GrantableApp> apps = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                if (resolved != null) {
                    for (ResolveInfo info : resolved) {
                        if (info.activityInfo == null) {
                            continue;
                        }
                        String packageName = info.activityInfo.packageName;
                        if (ownPackage.equals(packageName)
                                || granted.contains(packageName)
                                || !seen.add(packageName)) {
                            continue;
                        }
                        CharSequence label = info.loadLabel(packageManager);
                        apps.add(new GrantableApp(packageName,
                                label == null ? packageName : label.toString()));
                    }
                }
                Collections.sort(apps,
                        (left, right) -> left.getLabel().compareToIgnoreCase(right.getLabel()));
                mainHandler.post(() -> callback.onSuccess(apps));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** 已授权且能处理图片查看的应用，用于“用其他应用打开”入口 */
    public void loadAuthorizedOpenTargets(long fileId,
                                          @NonNull FileVersionRepository.Callback<List<GrantableApp>> callback) {
        ioExecutor.execute(() -> {
            try {
                Set<String> granted = new HashSet<>();
                List<FileAppGrant> grants = database.fileAppGrantDao().listByFile(fileId);
                if (grants != null) {
                    for (FileAppGrant grant : grants) {
                        granted.add(grant.getPackageName());
                    }
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType("image/*");
                List<ResolveInfo> resolved = packageManager.queryIntentActivities(intent, 0);
                List<GrantableApp> apps = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                if (resolved != null) {
                    for (ResolveInfo info : resolved) {
                        if (info.activityInfo == null) {
                            continue;
                        }
                        String packageName = info.activityInfo.packageName;
                        if (!granted.contains(packageName)
                                || ownPackage.equals(packageName)
                                || !seen.add(packageName)) {
                            continue;
                        }
                        CharSequence label = info.loadLabel(packageManager);
                        apps.add(new GrantableApp(packageName,
                                label == null ? packageName : label.toString()));
                    }
                }
                Collections.sort(apps,
                        (left, right) -> left.getLabel().compareToIgnoreCase(right.getLabel()));
                mainHandler.post(() -> callback.onSuccess(apps));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** 授予某个应用查看该文件的权限 */
    public void grant(long fileId, @NonNull GrantableApp app,
                      @NonNull FileVersionRepository.Callback<Void> callback) {
        ioExecutor.execute(() -> {
            try {
                database.fileAppGrantDao().insert(new FileAppGrant(fileId, app.getPackageName(),
                        app.getLabel(), System.currentTimeMillis()));
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** 取消某个应用对该文件的查看权限 */
    public void revoke(long fileId, @NonNull String packageName,
                       @NonNull FileVersionRepository.Callback<Void> callback) {
        ioExecutor.execute(() -> {
            try {
                database.fileAppGrantDao().delete(fileId, packageName);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (RuntimeException e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }
}
