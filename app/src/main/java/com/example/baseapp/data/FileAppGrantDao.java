package com.example.baseapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FileAppGrantDao {

    /** 重复授权时覆盖原有记录与授权时间 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FileAppGrant grant);

    @Query("DELETE FROM file_app_grant WHERE file_id = :fileId AND package_name = :packageName")
    void delete(long fileId, String packageName);

    @Query("SELECT COUNT(*) FROM file_app_grant WHERE file_id = :fileId "
            + "AND package_name = :packageName")
    int count(long fileId, String packageName);

    @Query("SELECT * FROM file_app_grant WHERE file_id = :fileId ORDER BY granted_at ASC")
    List<FileAppGrant> listByFile(long fileId);
}
