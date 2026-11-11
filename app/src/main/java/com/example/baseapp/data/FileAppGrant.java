package com.example.baseapp.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 文件的可查看应用授权：只有出现在这里的外部应用，才能通过本应用 Provider 读取该文件。
 * (file_id, package_name) 唯一，避免重复授权。
 */
@Entity(
        tableName = "file_app_grant",
        foreignKeys = @ForeignKey(
                entity = FileRecord.class,
                parentColumns = "id",
                childColumns = "file_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"file_id", "package_name"}, unique = true),
                @Index(value = {"package_name"})
        })
public class FileAppGrant {

    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 所属文件主键 */
    @ColumnInfo(name = "file_id")
    private long fileId;

    /** 被授权的应用包名 */
    @NonNull
    @ColumnInfo(name = "package_name")
    private String packageName = "";

    /** 授权时解析到的应用名称快照 */
    @ColumnInfo(name = "app_label")
    private String appLabel;

    /** 授权时间（毫秒） */
    @ColumnInfo(name = "granted_at")
    private long grantedAt;

    public FileAppGrant(long fileId, @NonNull String packageName, String appLabel,
                        long grantedAt) {
        this.fileId = fileId;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.grantedAt = grantedAt;
    }

    /** Room 反序列化使用 */
    public FileAppGrant() {
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

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(@NonNull String packageName) {
        this.packageName = packageName;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public void setAppLabel(String appLabel) {
        this.appLabel = appLabel;
    }

    public long getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(long grantedAt) {
        this.grantedAt = grantedAt;
    }
}
