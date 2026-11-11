package com.example.baseapp.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 版本图片的本地文件仓库。
 * 目录结构：filesDir/file_versions/&lt;fileId&gt;/v001_时间戳.jpg。
 * 数据库只保存相对路径，便于应用升级或迁移目录后仍能定位文件。
 */
public final class VersionFileStore {

    /** 版本图片根目录名 */
    private static final String ROOT_DIR_NAME = "file_versions";

    /** 缩略图最长边像素 */
    private static final int THUMB_MAX_SIZE = 512;

    /** 缩略图压缩质量 */
    private static final int THUMB_QUALITY = 80;

    /** 拷贝缓冲区大小 */
    private static final int COPY_BUFFER_SIZE = 32 * 1024;

    private final File rootDir;

    public VersionFileStore(Context context) {
        this.rootDir = new File(context.getFilesDir(), ROOT_DIR_NAME);
    }

    /** 一次保存操作的产物，均为相对根目录的路径 */
    public static final class StoredImage {
        public final String imagePath;
        public final String thumbPath;
        public final long sizeBytes;
        public final int width;
        public final int height;

        StoredImage(String imagePath, String thumbPath, long sizeBytes, int width, int height) {
            this.imagePath = imagePath;
            this.thumbPath = thumbPath;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 将选中的图片复制为指定文件的指定版本，并生成缩略图。
     */
    public StoredImage saveVersion(long fileId, int versionNo, Uri uri, ContentResolver resolver,
                                   String displayName) throws IOException {
        File dir = prepareDirectory(fileId);
        String extension = resolveExtension(resolver.getType(uri), displayName);
        String baseName = String.format(Locale.US, "v%03d_%d", versionNo, System.currentTimeMillis());
        File imageFile = new File(dir, baseName + "." + extension);

        long sizeBytes;
        try {
            sizeBytes = copyToFile(uri, resolver, imageFile);
        } catch (IOException | SecurityException e) {
            imageFile.delete();
            throw e instanceof IOException ? (IOException) e : new IOException("无法读取所选图片", e);
        }
        if (sizeBytes <= 0) {
            imageFile.delete();
            throw new IOException("所选图片内容为空");
        }
        return buildStoredImage(dir, imageFile, baseName, sizeBytes);
    }

    /**
     * 将某个已有版本复制为新版本，用于“回退到某版”操作。
     */
    public StoredImage copyVersion(long fileId, int versionNo, FileVersionEntry source)
            throws IOException {
        File sourceFile = resolveFile(source.getImagePath());
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("源版本图片已不存在");
        }
        File dir = prepareDirectory(fileId);
        String extension = resolveExtension(null, sourceFile.getName());
        String baseName = String.format(Locale.US, "v%03d_%d", versionNo, System.currentTimeMillis());
        File imageFile = new File(dir, baseName + "." + extension);

        long sizeBytes;
        try {
            sizeBytes = copyFile(sourceFile, imageFile);
        } catch (IOException e) {
            imageFile.delete();
            throw e;
        }
        if (sizeBytes <= 0) {
            imageFile.delete();
            throw new IOException("源版本图片内容为空");
        }
        return buildStoredImage(dir, imageFile, baseName, sizeBytes);
    }

    /** 确保文件版本目录存在 */
    private File prepareDirectory(long fileId) throws IOException {
        File dir = new File(rootDir, String.valueOf(fileId));
        if (dir.exists() && !dir.isDirectory()) {
            // 目录被同名普通文件占用时先移除，避免后续写入失败
            if (!dir.delete()) {
                throw new IOException("版本目录不可用");
            }
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建版本目录");
        }
        return dir;
    }

    /** 读取尺寸并生成缩略图，产出可入库的相对路径与元信息 */
    private StoredImage buildStoredImage(File dir, File imageFile, String baseName,
                                         long sizeBytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);
        int width = Math.max(bounds.outWidth, 0);
        int height = Math.max(bounds.outHeight, 0);

        String thumbRelative = null;
        File thumbFile = new File(dir, baseName + "_thumb.jpg");
        if (createThumbnail(imageFile, thumbFile)) {
            thumbRelative = toRelativePath(dir, thumbFile);
        }
        return new StoredImage(toRelativePath(dir, imageFile), thumbRelative, sizeBytes, width, height);
    }

    /** 相对路径转绝对路径；入参为空时返回 null */
    public String resolveAbsolutePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        return new File(rootDir, relativePath).getAbsolutePath();
    }

    /** 删除单个版本的图片与缩略图，并在目录为空时移除目录 */
    public void deleteVersionFiles(FileVersionEntry entry) {
        deleteQuietly(resolveFile(entry.getImagePath()));
        deleteQuietly(resolveFile(entry.getThumbPath()));
        removeDirIfEmpty(entry.getFileId());
    }

    /** 删除一次保存产物（写库失败时回滚使用） */
    public void deleteStoredImage(StoredImage stored) {
        if (stored == null) {
            return;
        }
        deleteQuietly(resolveFile(stored.imagePath));
        deleteQuietly(resolveFile(stored.thumbPath));
    }

    /** 删除某文件的全部版本文件 */
    public void deleteFileDir(long fileId) {
        deleteRecursively(new File(rootDir, String.valueOf(fileId)));
    }

    /**
     * 清理孤立文件：数据库无记录的文件目录整体删除，目录内无引用的文件单独删除。
     */
    public void cleanupOrphans(Set<String> referencedPaths, Set<Long> knownFileIds) {
        File[] children = rootDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory()) {
                // 根目录下不应出现散落文件，直接清理
                deleteQuietly(child);
                continue;
            }
            long fileId;
            try {
                fileId = Long.parseLong(child.getName());
            } catch (NumberFormatException e) {
                deleteRecursively(child);
                continue;
            }
            if (!knownFileIds.contains(fileId)) {
                deleteRecursively(child);
                continue;
            }
            File[] files = child.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String relative = child.getName() + "/" + file.getName();
                if (!referencedPaths.contains(relative)) {
                    deleteQuietly(file);
                }
            }
            removeDirIfEmpty(fileId);
        }
    }

    private File resolveFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        return new File(rootDir, relativePath);
    }

    private long copyToFile(Uri uri, ContentResolver resolver, File target) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法打开所选图片");
            }
            try (OutputStream output = new FileOutputStream(target)) {
                return copyStream(input, output);
            }
        }
    }

    private long copyFile(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            return copyStream(input, output);
        }
    }

    private long copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            total += read;
        }
        output.flush();
        return total;
    }

    /**
     * 生成最长边不超过 {@link #THUMB_MAX_SIZE} 的 JPEG 缩略图，原图不加载进内存。
     */
    private boolean createThumbnail(File source, File target) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMB_MAX_SIZE);
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) {
            return false;
        }

        Bitmap thumb = scaleDown(decoded, THUMB_MAX_SIZE);
        boolean success;
        try (FileOutputStream output = new FileOutputStream(target)) {
            success = thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, output);
        } catch (IOException e) {
            success = false;
        } finally {
            if (thumb != decoded) {
                thumb.recycle();
            }
            decoded.recycle();
        }
        if (!success) {
            deleteQuietly(target);
        }
        return success;
    }

    private Bitmap scaleDown(Bitmap source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxSize) {
            return source;
        }
        float ratio = (float) maxSize / longest;
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private static int calculateInSampleSize(int width, int height, int maxSize) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxSize) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * 解析图片扩展名：优先 MIME，其次原文件名后缀，最后回退 jpg。
     */
    private static String resolveExtension(String mimeType, String displayName) {
        if (mimeType != null) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (isValidExtension(extension)) {
                return extension.toLowerCase(Locale.US);
            }
        }
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String extension = displayName.substring(dot + 1);
                if (isValidExtension(extension)) {
                    return extension.toLowerCase(Locale.US);
                }
            }
        }
        return "jpg";
    }

    private static boolean isValidExtension(String extension) {
        if (extension == null || extension.isEmpty() || extension.length() > 5) {
            return false;
        }
        for (int i = 0; i < extension.length(); i++) {
            if (!Character.isLetterOrDigit(extension.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String toRelativePath(File dir, File file) {
        return dir.getName() + "/" + file.getName();
    }

    private void removeDirIfEmpty(long fileId) {
        File dir = new File(rootDir, String.valueOf(fileId));
        File[] files = dir.listFiles();
        if (files != null && files.length == 0) {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            // 本地缓存文件删除失败不影响主流程，下次启动的孤立文件清理会兜底
            file.delete();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        deleteQuietly(file);
    }
}
