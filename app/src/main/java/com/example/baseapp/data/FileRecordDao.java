package com.example.baseapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FileRecordDao {

    @Insert
    long insert(FileRecord record);

    @Query("SELECT * FROM file_record WHERE id = :id")
    FileRecord getById(long id);

    /** 更新最新版本号与最近记录时间 */
    @Query("UPDATE file_record SET latest_version_no = :latestVersionNo, updated_at = :updatedAt WHERE id = :id")
    void updateLatest(long id, int latestVersionNo, long updatedAt);

    @Query("DELETE FROM file_record WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT id FROM file_record")
    List<Long> listIds();

    /** 没有任何版本记录的空文件记录，用于启动时清理 */
    @Query("SELECT fr.id FROM file_record fr WHERE NOT EXISTS "
            + "(SELECT 1 FROM file_version fv WHERE fv.file_id = fr.id)")
    List<Long> listEmptyIds();

    /**
     * 文件列表：按最近记录时间倒序，并带出有效版本数量与最新有效版本缩略图。
     */
    @Query("SELECT fr.id AS id, fr.name AS name, fr.latest_version_no AS latestVersionNo, "
            + "fr.created_at AS createdAt, fr.updated_at AS updatedAt, "
            + "(SELECT COUNT(*) FROM file_version fv WHERE fv.file_id = fr.id AND fv.deleted = 0) "
            + "AS versionCount, "
            + "(SELECT fv.thumb_path FROM file_version fv WHERE fv.file_id = fr.id AND fv.deleted = 0 "
            + "ORDER BY fv.version_no DESC LIMIT 1) AS latestThumbPath, "
            + "(SELECT fv.size_bytes FROM file_version fv WHERE fv.file_id = fr.id AND fv.deleted = 0 "
            + "ORDER BY fv.version_no DESC LIMIT 1) AS latestSizeBytes "
            + "FROM file_record fr ORDER BY fr.updated_at DESC")
    List<FileSummary> listSummaries();
}
