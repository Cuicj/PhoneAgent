package com.example.baseapp.data;

/**
 * 文件列表展示用的聚合结果：主记录 + 版本数量 + 最新版本缩略图。
 * 非数据库实体，仅用于查询投影。
 */
public class FileSummary {

    private long id;
    private String name;
    private int latestVersionNo;
    private long createdAt;
    private long updatedAt;
    private int versionCount;
    private String latestThumbPath;
    private long latestSizeBytes;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
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

    public int getVersionCount() {
        return versionCount;
    }

    public void setVersionCount(int versionCount) {
        this.versionCount = versionCount;
    }

    public String getLatestThumbPath() {
        return latestThumbPath;
    }

    public void setLatestThumbPath(String latestThumbPath) {
        this.latestThumbPath = latestThumbPath;
    }

    public long getLatestSizeBytes() {
        return latestSizeBytes;
    }

    public void setLatestSizeBytes(long latestSizeBytes) {
        this.latestSizeBytes = latestSizeBytes;
    }
}
