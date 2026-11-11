package com.example.baseapp.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 被记录文件的主记录。
 * 只保存索引与元信息，图片本体存放在应用私有目录中，避免大图写入数据库。
 */
@Entity(tableName = "file_record")
public class FileRecord {

    /** 主键，同时作为私有版本目录名 */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 文件展示名称 */
    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    /** 当前最新版本号，1 表示只有原始版本 */
    @ColumnInfo(name = "latest_version_no")
    private int latestVersionNo;

    /** 首次记录时间（毫秒） */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** 最近一次追加版本的时间（毫秒） */
    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public FileRecord(@NonNull String name, int latestVersionNo, long createdAt, long updatedAt) {
        this.name = name;
        this.latestVersionNo = latestVersionNo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Room 反序列化使用 */
    public FileRecord() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public int getLatestVersionNo() {
        return latestVersionNo;
    }

    public void setLatestVersionNo(int latestVersionNo) {
        this.latestVersionNo = latestVersionNo;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
