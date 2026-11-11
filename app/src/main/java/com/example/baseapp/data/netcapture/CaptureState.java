package com.example.baseapp.data.netcapture;

/**
 * 抓取服务运行状态：服务与界面处于同一进程，直接用静态字段传递。
 */
public final class CaptureState {

    /** 转发服务是否正在运行 */
    public static volatile boolean running;
    /** 最近一次启动失败原因；成功启动后清空 */
    public static volatile String lastError;
    /** 服务启动时间 */
    public static volatile long startedAt;

    private CaptureState() {
    }
}
