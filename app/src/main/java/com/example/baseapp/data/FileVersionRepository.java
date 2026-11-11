package com.example.baseapp.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件版本仓库：统一负责版本时间线写入、描述生成与本地文件清理。
 * 所有写操作串行执行，保证 version_no 连续且唯一。
 */
public final class FileVersionRepository {

    /** 单个文件最多保留的有效版本数，超出后优先裁剪较旧的非原始版本 */
    private static final int MAX_VERSIONS_PER_FILE = 20;

    private static volatile FileVersionRepository instance;

    private final AppDatabase database;
    private final VersionFileStore fileStore;
    private final ContentResolver contentResolver;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FileVersionRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.database = AppDatabase.getInstance(appContext);
        this.fileStore = new VersionFileStore(appContext);
        this.contentResolver = appContext.getContentResolver();
    }

    public static FileVersionRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (FileVersionRepository.class) {
                if (instance == null) {
                    instance = new FileVersionRepository(context);
                }
            }
        }
        return instance;
    }

    /** 统一回调：结果切回主线程 */
    public interface Callback<T> {
        void onSuccess(T value);

        default void onError(@NonNull Exception error) {
        }
    }

    /** 文件列表快照：文件汇总 + 本地总占用 */
    public static final class LibrarySnapshot {
        private final List<FileSummary> files;
        private final long totalBytes;

        LibrarySnapshot(List<FileSummary> files, long totalBytes) {
            this.files = files;
            this.totalBytes = totalBytes;
        }

        public List<FileSummary> getFiles() {
            return files;
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }

    /** 相对路径转绝对路径，供 UI 加载图片 */
    public String resolvePath(String relativePath) {
        return fileStore.resolveAbsolutePath(relativePath);
    }

    /** 读取文件列表 */
    public void loadLibrary(@NonNull Callback<LibrarySnapshot> callback) {
        ioExecutor.execute(() -> {
            try {
                List<FileSummary> files = database.fileRecordDao().listSummaries();
                long totalBytes = database.fileVersionDao().totalSizeBytes();
                mainHandler.post(() -> callback.onSuccess(new LibrarySnapshot(files, totalBytes)));
            } catch (RuntimeException e) {
                postError(callback, e);
            }
        });
    }

    /** 读取某个文件的完整时间线：版本记录与外部访问事件按时间合并 */
    public void loadTimeline(long fileId, @NonNull Callback<List<TimelineItem>> callback) {
        ioExecutor.execute(() -> {
            try {
                List<FileVersionEntry> versions = database.fileVersionDao().listByFileAsc(fileId);
                List<FileAccessEvent> events = database.fileAccessEventDao().listByFileAsc(fileId);
                List<TimelineItem> items = TimelineItem.merge(versions, events);
                mainHandler.post(() -> callback.onSuccess(items));
            } catch (RuntimeException e) {
                postError(callback, e);
            }
        });
    }

    /**
     * 导入一张图片，生成文件记录与第 1 版（原始导入）。
     */
    public void importImage(@NonNull Uri uri, @NonNull Callback<Long> callback) {
        ioExecutor.execute(() -> {
            long fileId = -1;
            try {
                String displayName = queryDisplayName(uri);
                String mimeType = resolveMimeType(uri);
                VersionSource source = detectSource(uri);
                long now = System.currentTimeMillis();

                fileId = database.fileRecordDao().insert(
                        new FileRecord(displayName, 1, now, now));

                VersionFileStore.StoredImage stored;
                try {
                    stored = fileStore.saveVersion(fileId, 1, uri, contentResolver, displayName);
                } catch (Exception e) {
                    // 复制失败时回滚主记录，避免留下空文件
                    database.fileRecordDao().deleteById(fileId);
                    throw e;
                }

                FileVersionEntry entry = new FileVersionEntry(fileId, 1, stored.imagePath,
                        stored.thumbPath, mimeType, stored.sizeBytes, stored.width, stored.height, now);
                entry.setOperationType(VersionOperationType.ORIGINAL_IMPORT);
                entry.setDescription(VersionDescriptionBuilder.forOriginal(
                        stored.width, stored.height, stored.sizeBytes, mimeType, source));
                entry.setSource(source);

                try {
                    database.runInTransaction(() -> database.fileVersionDao().insert(entry));
                } catch (RuntimeException e) {
                    fileStore.deleteStoredImage(stored);
                    database.fileRecordDao().deleteById(fileId);
                    throw e;
                }

                long resultId = fileId;
                mainHandler.post(() -> callback.onSuccess(resultId));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    /**
     * 为已有文件追加一个新版本，自动描述与上一有效版本的差异。
     */
    public void addVersion(long fileId, @NonNull Uri uri, @NonNull Callback<Integer> callback) {
        ioExecutor.execute(() -> {
            try {
                FileRecord record = database.fileRecordDao().getById(fileId);
                if (record == null) {
                    throw new IllegalStateException("文件记录不存在");
                }
                FileVersionEntry previous = database.fileVersionDao().getLatestActive(fileId);
                int nextVersionNo = database.fileVersionDao().maxVersionNo(fileId) + 1;
                VersionFileStore.StoredImage stored = fileStore.saveVersion(
                        fileId, nextVersionNo, uri, contentResolver, record.getName());
                String mimeType = resolveMimeType(uri);
                VersionSource source = detectSource(uri);
                long now = System.currentTimeMillis();

                FileVersionEntry entry = new FileVersionEntry(fileId, nextVersionNo,
                        stored.imagePath, stored.thumbPath, mimeType, stored.sizeBytes,
                        stored.width, stored.height, now);
                entry.setOperationType(VersionOperationType.ADD_VERSION);
                entry.setDescription(VersionDescriptionBuilder.forAdded(previous,
                        stored.width, stored.height, stored.sizeBytes, mimeType, source));
                entry.setSource(source);

                try {
                    database.runInTransaction(() -> {
                        database.fileVersionDao().insert(entry);
                        database.fileRecordDao().updateLatest(fileId, nextVersionNo, now);
                    });
                } catch (RuntimeException e) {
                    fileStore.deleteStoredImage(stored);
                    throw e;
                }

                pruneOldVersions(fileId);
                mainHandler.post(() -> callback.onSuccess(nextVersionNo));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    /**
     * 回退到某个历史版本：复制目标版本图片生成新的最新版本。
     */
    public void revertToVersion(long fileId, int targetVersionNo,
                                @NonNull Callback<Integer> callback) {
        ioExecutor.execute(() -> {
            try {
                FileRecord record = database.fileRecordDao().getById(fileId);
                if (record == null) {
                    throw new IllegalStateException("文件记录不存在");
                }
                FileVersionEntry target = database.fileVersionDao()
                        .getByVersionNo(fileId, targetVersionNo);
                if (target == null || target.isDeleted()) {
                    throw new IllegalStateException("目标版本不存在或已删除");
                }
                FileVersionEntry previous = database.fileVersionDao().getLatestActive(fileId);
                if (previous != null && previous.getVersionNo() == targetVersionNo) {
                    throw new IllegalStateException("该版本已经是最新版本");
                }

                int nextVersionNo = database.fileVersionDao().maxVersionNo(fileId) + 1;
                VersionFileStore.StoredImage stored = fileStore.copyVersion(fileId,
                        nextVersionNo, target);
                long now = System.currentTimeMillis();

                FileVersionEntry entry = new FileVersionEntry(fileId, nextVersionNo,
                        stored.imagePath, stored.thumbPath, target.getMimeType(),
                        stored.sizeBytes, stored.width, stored.height, now);
                entry.setOperationType(VersionOperationType.REVERT);
                entry.setDescription(VersionDescriptionBuilder.forRevert(previous, target,
                        VersionSource.APP_EDIT));
                entry.setSource(VersionSource.APP_EDIT);
                entry.setRelatedVersionNo(targetVersionNo);

                try {
                    database.runInTransaction(() -> {
                        database.fileVersionDao().insert(entry);
                        database.fileRecordDao().updateLatest(fileId, nextVersionNo, now);
                    });
                } catch (RuntimeException e) {
                    fileStore.deleteStoredImage(stored);
                    throw e;
                }

                pruneOldVersions(fileId);
                mainHandler.post(() -> callback.onSuccess(nextVersionNo));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    /**
     * 删除某个版本：保留时间线条目并记录删除操作，仅移除图片副本。
     * 原始版本不可删除。
     *
     * @return 删除后剩余的最新有效版本号
     */
    public void deleteVersion(long fileId, int versionNo,
                              @NonNull Callback<Integer> callback) {
        ioExecutor.execute(() -> {
            try {
                FileVersionEntry target = database.fileVersionDao()
                        .getByVersionNo(fileId, versionNo);
                if (target == null || target.isDeleted()) {
                    throw new IllegalStateException("版本不存在或已删除");
                }
                if (target.getVersionNo() <= 1) {
                    throw new IllegalStateException("原始版本不能删除");
                }

                List<FileVersionEntry> all = database.fileVersionDao().listByFileAsc(fileId);
                int newLatestVersionNo = 0;
                for (FileVersionEntry item : all) {
                    if (!item.isDeleted() && item.getId() != target.getId()) {
                        // 列表按版本号升序，最后一次赋值即剩余的最新版本
                        newLatestVersionNo = item.getVersionNo();
                    }
                }

                long now = System.currentTimeMillis();
                // lambda 只能捕获 final 变量，这里固定删除后的最新版本号
                final int remainingLatestVersionNo = newLatestVersionNo;
                String description = VersionDescriptionBuilder.forDeleted(
                        target, remainingLatestVersionNo);
                database.runInTransaction(() -> {
                    database.fileVersionDao().markDeleted(target.getId(),
                            VersionOperationType.DELETE_VERSION.name(), description, now);
                    database.fileRecordDao().updateLatest(fileId, remainingLatestVersionNo, now);
                });
                fileStore.deleteVersionFiles(target);

                mainHandler.post(() -> callback.onSuccess(remainingLatestVersionNo));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    /** 删除文件记录及其全部版本图片 */
    public void deleteFile(long fileId, @NonNull Callback<Void> callback) {
        ioExecutor.execute(() -> {
            try {
                // 外键级联删除版本行，随后清理磁盘文件
                database.fileRecordDao().deleteById(fileId);
                fileStore.deleteFileDir(fileId);
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (RuntimeException e) {
                postError(callback, e);
            }
        });
    }

    /**
     * 启动时的存储自检：删除无版本的残留记录、清理数据库未引用的图片文件。
     */
    public void cleanupOrphans() {
        ioExecutor.execute(() -> {
            try {
                List<Long> emptyIds = database.fileRecordDao().listEmptyIds();
                for (Long emptyId : emptyIds) {
                    database.fileRecordDao().deleteById(emptyId);
                }

                Set<String> referencedPaths = new HashSet<>();
                List<String> imagePaths = database.fileVersionDao().listImagePaths();
                if (imagePaths != null) {
                    referencedPaths.addAll(imagePaths);
                }
                List<String> thumbPaths = database.fileVersionDao().listThumbPaths();
                if (thumbPaths != null) {
                    referencedPaths.addAll(thumbPaths);
                }
                Set<Long> knownFileIds = new HashSet<>(database.fileRecordDao().listIds());
                fileStore.cleanupOrphans(referencedPaths, knownFileIds);
            } catch (RuntimeException ignored) {
                // 清理属于后台自检，失败不阻塞应用启动
            }
        });
    }

    /**
     * 有效版本超出上限时从最旧的非原始版本开始裁剪，原始版本永久保留。
     */
    private void pruneOldVersions(long fileId) {
        List<FileVersionEntry> versions = database.fileVersionDao().listByFileAsc(fileId);
        if (versions == null) {
            return;
        }
        int activeCount = 0;
        for (FileVersionEntry version : versions) {
            if (!version.isDeleted()) {
                activeCount++;
            }
        }
        if (activeCount <= MAX_VERSIONS_PER_FILE) {
            return;
        }
        int excess = activeCount - MAX_VERSIONS_PER_FILE;
        for (FileVersionEntry version : new ArrayList<>(versions)) {
            if (excess <= 0) {
                break;
            }
            if (version.isDeleted() || version.getVersionNo() <= 1) {
                continue;
            }
            database.fileVersionDao().deleteById(version.getId());
            fileStore.deleteVersionFiles(version);
            excess--;
        }
    }

    /** 读取所选文件的展示名称，取不到时按时间生成 */
    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = contentResolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (RuntimeException ignored) {
            // 部分文档提供方不支持查询名称，回退到默认命名
        }
        return "图片_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                .format(new Date());
    }

    private String resolveMimeType(Uri uri) {
        String mimeType = contentResolver.getType(uri);
        return mimeType == null ? "image/jpeg" : mimeType;
    }

    /**
     * 尽力识别来源：SAF 不暴露调用方信息，按文档提供方 authority 粗略判断。
     */
    private VersionSource detectSource(Uri uri) {
        String authority = uri.getAuthority();
        if (authority == null) {
            return VersionSource.UNKNOWN;
        }
        String normalized = authority.toLowerCase(Locale.US);
        if (normalized.contains("camera")) {
            return VersionSource.CAMERA;
        }
        if (normalized.contains("media")) {
            return VersionSource.GALLERY;
        }
        return VersionSource.UNKNOWN;
    }

    private <T> void postError(Callback<T> callback, Exception error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
