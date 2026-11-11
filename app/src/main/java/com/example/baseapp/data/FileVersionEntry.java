package com.example.baseapp.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 文件时间线上的一个条目，按 version_no 从小到大构成线性历史。
 * (file_id, version_no) 唯一，保证同一文件不会出现重复版本号。
 * 删除版本时保留该条目并标记 deleted，用于展示可追溯的删除操作。
 */
@Entity(
        tableName = "file_version",
        foreignKeys = @ForeignKey(
                entity = FileRecord.class,
                parentColumns = "id",
                childColumns = "file_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index(value = {"file_id", "version_no"}, unique = true))
public class FileVersionEntry {

    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 所属文件主键 */
    @ColumnInfo(name = "file_id")
    private long fileId;

    /** 版本号：1 为原始图片，之后每次操作递增 */
    @ColumnInfo(name = "version_no")
    private int versionNo;

    /** 图片相对私有版本根目录的路径，例如 "12/v001_1730000000000.jpg"；删除后为空串 */
    @NonNull
    @ColumnInfo(name = "image_path")
    private String imagePath = "";

    /** 缩略图相对路径，生成失败或已删除时为空 */
    @ColumnInfo(name = "thumb_path")
    private String thumbPath;

    /** MIME 类型 */
    @ColumnInfo(name = "mime_type")
    private String mimeType;

    /** 文件大小（字节） */
    @ColumnInfo(name = "size_bytes")
    private long sizeBytes;

    /** 图片宽度（像素） */
    @ColumnInfo(name = "width")
    private int width;

    /** 图片高度（像素） */
    @ColumnInfo(name = "height")
    private int height;

    /** 条目创建（或最近一次操作）时间（毫秒） */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** 操作类型，落库为枚举名称 */
    @NonNull
    @ColumnInfo(name = "operation_type")
    private VersionOperationType operationType = VersionOperationType.ADD_VERSION;

    /** 自动生成的提交说明，写入时固化，保证各入口展示一致 */
    @NonNull
    @ColumnInfo(name = "description")
    private String description = "";

    /** 图片来源 */
    @ColumnInfo(name = "source")
    private VersionSource source;

    /** 关联版本号：回退时为回退目标，删除时为被删除版本；0 表示无关联 */
    @ColumnInfo(name = "related_version_no")
    private int relatedVersionNo;

    /** 是否为已删除的墓碑条目 */
    @ColumnInfo(name = "deleted")
    private boolean deleted;

    public FileVersionEntry(long fileId, int versionNo, @NonNull String imagePath, String thumbPath,
                            String mimeType, long sizeBytes, int width, int height,
                            long createdAt) {
        this.fileId = fileId;
        this.versionNo = versionNo;
        this.imagePath = imagePath;
        this.thumbPath = thumbPath;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.createdAt = createdAt;
    }

    /** Room 反序列化使用 */
    public FileVersionEntry() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getFileId() {
        return fileId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    @NonNull
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(@NonNull String imagePath) {
        this.imagePath = imagePath;
    }

    public String getThumbPath() {
        return thumbPath;
    }

    public void setThumbPath(String thumbPath) {
        this.thumbPath = thumbPath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @NonNull
    public VersionOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(@NonNull VersionOperationType operationType) {
        this.operationType = operationType;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    public VersionSource getSource() {
        return source;
    }

    public void setSource(VersionSource source) {
        this.source = source;
    }

    public int getRelatedVersionNo() {
        return relatedVersionNo;
    }

    public void setRelatedVersionNo(int relatedVersionNo) {
        this.relatedVersionNo = relatedVersionNo;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
