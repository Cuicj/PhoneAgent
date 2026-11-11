package com.example.baseapp.data;

/**
 * 版本时间线上的操作类型，落库时保存枚举名称，便于后续扩展新操作。
 */
public enum VersionOperationType {

    /** 原始导入：文件第一次进入版本库 */
    ORIGINAL_IMPORT,

    /** 追加新版本：用户选择了修改后的图片 */
    ADD_VERSION,

    /** 回退到某版：复制历史版本生成新的最新版本 */
    REVERT,

    /** 删除版本：保留时间线记录，移除图片副本 */
    DELETE_VERSION
}
