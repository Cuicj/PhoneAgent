package com.example.baseapp.data;

/**
 * 外部应用访问文件的类型，落库时保存枚举名称，便于后续扩展。
 */
public enum FileAccessType {

    /** 读取/查看：经过本应用入口成功读取了图片 */
    READ("读取"),

    /** 写入：保留给后续支持写入的入口，当前共享入口只读 */
    WRITE("写入"),

    /** 拦截：调用方未获得授权或请求了不支持的操作 */
    BLOCKED("已拦截");

    private final String label;

    FileAccessType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
