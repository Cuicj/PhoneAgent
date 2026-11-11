package com.example.baseapp.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * 对外文件入口：外部应用通过 content://{@link #AUTHORITY}/file/{fileId} 读取图片。
 * 每次读取都会用 Binder.getCallingUid() 解析调用方，按“可查看的应用”授权表放行或拦截，
 * 并把事件写入本地时间线。共享入口只读，写入请求一律拦截。
 */
public class FileAccessProvider extends ContentProvider {

    /** Provider 授权标识 */
    public static final String AUTHORITY = "com.example.baseapp.files";

    /** 路径第一段固定为 file */
    private static final String PATH_FILE = "file";

    private FileAccessRepository repository;

    /** 构造对外文件 URI：content://authority/file/{fileId} */
    public static Uri buildUri(long fileId) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(PATH_FILE)
                .appendPath(String.valueOf(fileId))
                .build();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        repository = FileAccessRepository.getInstance(context);
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        long fileId = parseFileId(uri);
        int callingUid = Binder.getCallingUid();
        boolean ownCaller = callingUid == Process.myUid();
        String packageName = resolveCallerPackage(callingUid);
        String appLabel = repository.resolveAppLabel(packageName);
        FileVersionEntry latest = repository.latestActiveSync(fileId);
        int versionNo = latest == null ? 0 : latest.getVersionNo();

        // 共享入口只读：写入请求直接拒绝并记录，避免破坏历史版本图片
        if (mode != null && mode.contains("w")) {
            if (!ownCaller) {
                repository.recordEventSync(new FileAccessEvent(fileId, versionNo,
                        FileAccessType.BLOCKED, packageName, appLabel, callingUid,
                        System.currentTimeMillis(), "请求写入，共享入口只读"));
            }
            throw new SecurityException("该入口仅支持读取");
        }

        // 未授权的调用方拒绝访问，并留下拦截事件
        if (!ownCaller && !repository.isGrantedSync(fileId, packageName)) {
            repository.recordEventSync(new FileAccessEvent(fileId, versionNo,
                    FileAccessType.BLOCKED, packageName, appLabel, callingUid,
                    System.currentTimeMillis(), "未获得查看授权"));
            throw new SecurityException("未授权的访问");
        }

        if (latest == null) {
            throw new FileNotFoundException("没有可读取的版本");
        }
        String absolutePath = repository.resolvePath(latest.getImagePath());
        if (absolutePath == null || absolutePath.isEmpty()) {
            throw new FileNotFoundException("版本图片不存在");
        }
        File file = new File(absolutePath);
        if (!file.isFile()) {
            throw new FileNotFoundException("版本图片不存在");
        }

        // 本应用自身读取不记录，避免时间线被内部访问刷屏
        if (!ownCaller) {
            repository.recordEventSync(new FileAccessEvent(fileId, versionNo,
                    FileAccessType.READ, packageName, appLabel, callingUid,
                    System.currentTimeMillis(), null));
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        try {
            long fileId = parseFileId(uri);
            FileVersionEntry latest = repository.latestActiveSync(fileId);
            if (latest != null && latest.getMimeType() != null) {
                return latest.getMimeType();
            }
        } catch (FileNotFoundException ignored) {
            // 路径非法时按通用图片类型返回
        }
        return "image/*";
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("文件入口不支持写入");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("文件入口不支持删除");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("文件入口不支持修改");
    }

    /** 解析 content://authority/file/{fileId} 中的文件主键 */
    private long parseFileId(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2 || !PATH_FILE.equals(segments.get(0))) {
            throw new FileNotFoundException("无法识别的文件路径");
        }
        try {
            return Long.parseLong(segments.get(1));
        } catch (NumberFormatException e) {
            throw new FileNotFoundException("无法识别的文件主键");
        }
    }

    /** UID 转包名；共享 UID 时取首个包，解析失败记录 uid 文本 */
    private String resolveCallerPackage(int uid) {
        PackageManager packageManager = getContext() == null
                ? null : getContext().getPackageManager();
        if (packageManager != null) {
            String[] packages = packageManager.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0];
            }
        }
        return "uid:" + uid;
    }
}
