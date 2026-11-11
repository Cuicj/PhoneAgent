package com.example.baseapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FileAccessEventDao {

    @Insert
    long insert(FileAccessEvent event);

    /** 按时间升序返回某个文件的全部访问事件 */
    @Query("SELECT * FROM file_access_event WHERE file_id = :fileId ORDER BY occurred_at ASC")
    List<FileAccessEvent> listByFileAsc(long fileId);
}
