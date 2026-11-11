package com.example.baseapp.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 文件访问事件：记录经过本应用入口的读取与拦截，用于版本时间线中的“被某应用读取”节点。
 * 只覆盖经过本应用 ContentProvider 的访问，直接由其他应用打开文件的行为无法捕获。
 */
@Entity(
        tableName = "file_access_event",
        foreignKeys = @ForeignKey(
                entity = FileRecord.class,
                parentColumns = "id",
                childColumns = "file_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index(value = {"file_id", "occurred_at"}))
public class FileAccessEvent {

    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 所属文件主键 */
    @ColumnInfo(name = "file_id")
    private long fileId;

    /** 访问发生时的最新有效版本号，0 表示当时没有可用版本 */
    @ColumnInfo(name = "version_no")
    private int versionNo;

    /** 访问类型，落库为枚举名称 */
    @NonNull
    @ColumnInfo(name = "access_type")
    private FileAccessType accessType = FileAccessType.READ;

    /** 调用方包名，无法解析时记录 uid 文本 */
    @NonNull
    @ColumnInfo(name = "caller_package")
    private String callerPackage = "";

    /** 调用方应用名称快照，应用卸载后仍可读 */
    @ColumnInfo(name = "caller_label")
    private String callerLabel;

    /** 调用方 UID */
    @ColumnInfo(name = "caller_uid")
    private int callerUid;

    /** 事件时间（毫秒） */
    @ColumnInfo(name = "occurred_at")
    private long occurredAt;

    /** 补充说明，例如拦截原因 */
    @ColumnInfo(name = "detail")
    private String detail;

    public FileAccessEvent(long fileId, int versionNo, @NonNull FileAccessType accessType,
                           @NonNull String callerPackage, String callerLabel, int callerUid,
                           long occurredAt, String detail) {
        this.fileId = fileId;
        this.versionNo = versionNo;
        this.accessType = accessType;
        this.callerPackage = callerPackage;
        this.callerLabel = callerLabel;
        this.callerUid = callerUid;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }

    /** Room 反序列化使用 */
    public FileAccessEvent() {
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
    public FileAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(@NonNull FileAccessType accessType) {
        this.accessType = accessType;
    }

    @NonNull
    public String getCallerPackage() {
        return callerPackage;
    }

    public void setCallerPackage(@NonNull String callerPackage) {
        this.callerPackage = callerPackage;
    }

    public String getCallerLabel() {
        return callerLabel;
    }

    public void setCallerLabel(String callerLabel) {
        this.callerLabel = callerLabel;
    }

    public int getCallerUid() {
        return callerUid;
    }

    public void setCallerUid(int callerUid) {
        this.callerUid = callerUid;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(long occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
