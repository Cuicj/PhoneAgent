package com.example.baseapp.data;

/**
 * 版本图片来源，用于生成自然的提交说明。
 */
public enum VersionSource {

    /** 相册或系统文件选择器 */
    GALLERY("相册"),

    /** 相机拍摄 */
    CAMERA("相机"),

    /** 应用内编辑（含回退复制） */
    APP_EDIT("应用内编辑"),

    /** 无法识别的来源 */
    UNKNOWN("未知来源");

    private final String label;

    VersionSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
