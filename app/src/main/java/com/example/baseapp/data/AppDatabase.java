package com.example.baseapp.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 本地版本库：仅存放文件与版本索引，图片本体在应用私有目录。
 * v2 增加操作类型、自动描述、来源、关联版本与软删除字段。
 * v3 增加外部访问事件与“可查看的应用”授权表。
 */
@Database(entities = {FileRecord.class, FileVersionEntry.class, FileAccessEvent.class,
        FileAppGrant.class}, version = 3, exportSchema = false)
@TypeConverters(AppDatabase.Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "file_version.db";

    private static volatile AppDatabase instance;

    public abstract FileRecordDao fileRecordDao();

    public abstract FileVersionDao fileVersionDao();

    public abstract FileAccessEventDao fileAccessEventDao();

    public abstract FileAppGrantDao fileAppGrantDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * v1 -> v2：重建版本表，去掉旧的 note 字段，补充时间线所需字段。
     * 旧数据按版本号推断操作类型，描述生成一份基础说明。
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `file_version_new` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`file_id` INTEGER NOT NULL, "
                    + "`version_no` INTEGER NOT NULL, "
                    + "`image_path` TEXT NOT NULL, "
                    + "`thumb_path` TEXT, "
                    + "`mime_type` TEXT, "
                    + "`size_bytes` INTEGER NOT NULL, "
                    + "`width` INTEGER NOT NULL, "
                    + "`height` INTEGER NOT NULL, "
                    + "`created_at` INTEGER NOT NULL, "
                    + "`operation_type` TEXT NOT NULL, "
                    + "`description` TEXT NOT NULL, "
                    + "`source` TEXT, "
                    + "`related_version_no` INTEGER NOT NULL, "
                    + "`deleted` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`file_id`) REFERENCES `file_record`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("INSERT INTO `file_version_new` "
                    + "(`id`, `file_id`, `version_no`, `image_path`, `thumb_path`, `mime_type`, "
                    + "`size_bytes`, `width`, `height`, `created_at`, `operation_type`, "
                    + "`description`, `source`, `related_version_no`, `deleted`) "
                    + "SELECT `id`, `file_id`, `version_no`, `image_path`, `thumb_path`, `mime_type`, "
                    + "`size_bytes`, `width`, `height`, `created_at`, "
                    + "CASE WHEN `version_no` = 1 THEN 'ORIGINAL_IMPORT' ELSE 'ADD_VERSION' END, "
                    + "CASE WHEN `version_no` = 1 "
                    + "THEN '导入原始图片（' || `width` || '×' || `height` || '，' "
                    + "|| `size_bytes` || ' 字节）。' "
                    + "ELSE '追加新版本（' || `width` || '×' || `height` || '，' "
                    + "|| `size_bytes` || ' 字节）。' END, "
                    + "NULL, 0, 0 FROM `file_version`");
            db.execSQL("DROP TABLE `file_version`");
            db.execSQL("ALTER TABLE `file_version_new` RENAME TO `file_version`");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_file_version_file_id_version_no` "
                    + "ON `file_version` (`file_id`, `version_no`)");
        }
    };

    /**
     * v2 -> v3：新增外部访问事件表与“可查看的应用”授权表。
     * 两张表都随文件记录级联删除，保证删除文件时审计数据一并清理。
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `file_access_event` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`file_id` INTEGER NOT NULL, "
                    + "`version_no` INTEGER NOT NULL, "
                    + "`access_type` TEXT NOT NULL, "
                    + "`caller_package` TEXT NOT NULL, "
                    + "`caller_label` TEXT, "
                    + "`caller_uid` INTEGER NOT NULL, "
                    + "`occurred_at` INTEGER NOT NULL, "
                    + "`detail` TEXT, "
                    + "FOREIGN KEY(`file_id`) REFERENCES `file_record`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_file_access_event_file_id_occurred_at` "
                    + "ON `file_access_event` (`file_id`, `occurred_at`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `file_app_grant` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`file_id` INTEGER NOT NULL, "
                    + "`package_name` TEXT NOT NULL, "
                    + "`app_label` TEXT, "
                    + "`granted_at` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`file_id`) REFERENCES `file_record`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_file_app_grant_file_id_package_name` "
                    + "ON `file_app_grant` (`file_id`, `package_name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_file_app_grant_package_name` "
                    + "ON `file_app_grant` (`package_name`)");
        }
    };

    /** 枚举与数据库文本的转换 */
    public static class Converters {

        @TypeConverter
        public static String fromOperationType(VersionOperationType type) {
            return type == null ? null : type.name();
        }

        @TypeConverter
        @NonNull
        public static VersionOperationType toOperationType(String name) {
            VersionOperationType type = parse(VersionOperationType.class, name);
            // 旧数据或异常值回退为追加版本，保证非空列始终可读
            return type == null ? VersionOperationType.ADD_VERSION : type;
        }

        @TypeConverter
        public static String fromSource(VersionSource source) {
            return source == null ? null : source.name();
        }

        @TypeConverter
        public static VersionSource toSource(String name) {
            return parse(VersionSource.class, name);
        }

        @TypeConverter
        public static String fromAccessType(FileAccessType type) {
            return type == null ? null : type.name();
        }

        @TypeConverter
        @NonNull
        public static FileAccessType toAccessType(String name) {
            FileAccessType type = parse(FileAccessType.class, name);
            // 非法值回退为读取，保证非空列始终可读
            return type == null ? FileAccessType.READ : type;
        }

        private static <E extends Enum<E>> E parse(Class<E> clazz, String name) {
            if (name == null) {
                return null;
            }
            try {
                return Enum.valueOf(clazz, name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
