package com.example.baseapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FileVersionDao {

    @Insert
    long insert(FileVersionEntry entry);

    /** 按版本号升序返回完整时间线，包含已删除的墓碑条目 */
    @Query("SELECT * FROM file_version WHERE file_id = :fileId ORDER BY version_no ASC")
    List<FileVersionEntry> listByFileAsc(long fileId);

    @Query("SELECT * FROM file_version WHERE id = :id")
    FileVersionEntry getById(long id);

    @Query("SELECT * FROM file_version WHERE file_id = :fileId AND version_no = :versionNo LIMIT 1")
    FileVersionEntry getByVersionNo(long fileId, int versionNo);

    /** 最新有效版本（忽略已删除条目） */
    @Query("SELECT * FROM file_version WHERE file_id = :fileId AND deleted = 0 "
            + "ORDER BY version_no DESC LIMIT 1")
    FileVersionEntry getLatestActive(long fileId);

    /** 最大版本号包含墓碑条目，避免新版本复用历史版本号 */
    @Query("SELECT IFNULL(MAX(version_no), 0) FROM file_version WHERE file_id = :fileId")
    int maxVersionNo(long fileId);

    @Query("DELETE FROM file_version WHERE id = :id")
    void deleteById(long id);

    /** 软删除：保留时间线条目，清空图片路径并记录删除操作 */
    @Query("UPDATE file_version SET deleted = 1, operation_type = :operationType, "
            + "description = :description, created_at = :createdAt, "
            + "image_path = '', thumb_path = NULL WHERE id = :id")
    void markDeleted(long id, String operationType, String description, long createdAt);

    @Query("SELECT image_path FROM file_version WHERE deleted = 0 AND image_path != ''")
    List<String> listImagePaths();

    @Query("SELECT thumb_path FROM file_version WHERE deleted = 0 AND thumb_path IS NOT NULL")
    List<String> listThumbPaths();

    /** 所有有效版本占用的本地空间（字节） */
    @Query("SELECT IFNULL(SUM(size_bytes), 0) FROM file_version WHERE deleted = 0")
    long totalSizeBytes();
}
