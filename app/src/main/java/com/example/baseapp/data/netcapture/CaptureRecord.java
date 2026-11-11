package com.example.baseapp.data.netcapture;

/**
 * 一条网络抓取记录。
 *
 * <p>HTTP 记录包含请求与响应头部/正文；HTTPS 无法解密，仅保存连接层面的元数据，
 * 正文与头部保持为空并由 {@link #encrypted} 标记，避免界面伪装成能看到内容。</p>
 */
public class CaptureRecord {

    /** 明文 HTTP（尝试解析请求与响应） */
    public static final int PROTOCOL_HTTP = 1;
    /** HTTPS/TLS：仅记录连接元数据 */
    public static final int PROTOCOL_HTTPS = 2;
    /** 其他 TCP 端口：仅记录连接元数据 */
    public static final int PROTOCOL_TCP = 3;
    /** UDP 流（DNS、QUIC 等）：仅记录流量元数据 */
    public static final int PROTOCOL_UDP = 4;

    /** 记录唯一标识，详情页按此查找 */
    public final long id;
    /** 协议类型，取值见上方常量 */
    public final int protocol;
    /** 记录创建时间（毫秒时间戳） */
    public final long startTime;
    /** 对端 IP */
    public final String remoteIp;
    /** 对端端口 */
    public final int remotePort;

    /** 域名：HTTP 取 Host 头，HTTPS 取 SNI，DNS 取查询域名；取不到时退回 IP */
    public volatile String host;
    /** HTTP 请求方法；HTTPS/UDP 为 null */
    public volatile String method;
    /** HTTP 请求路径（含查询串） */
    public volatile String path;
    /** HTTP 响应状态码；未知为 -1 */
    public volatile int statusCode = -1;
    /** 结束时间（毫秒时间戳），0 表示仍在进行中 */
    public volatile long endTime;
    /** 上行字节数（客户端发往远端） */
    public volatile long upBytes;
    /** 下行字节数（远端返回客户端） */
    public volatile long downBytes;
    /** 是否失败：连接失败、RST、TLS 告警、HTTP 4xx/5xx */
    public volatile boolean failed;
    /** 内容是否加密（HTTPS/QUIC） */
    public volatile boolean encrypted;
    /** TLS 握手观察结果：成功 / 失败 / 未知 */
    public volatile String tlsState;
    /** 请求头原文（HTTP） */
    public volatile String requestHeaders;
    /** 请求正文（仅可读文本） */
    public volatile String requestBody;
    /** 响应头原文（HTTP） */
    public volatile String responseHeaders;
    /** 响应正文（仅可读文本） */
    public volatile String responseBody;
    /** 请求正文是否可读；false 时界面只展示大小 */
    public volatile boolean requestBodyReadable = true;
    /** 响应正文是否可读；false 时界面只展示大小 */
    public volatile boolean responseBodyReadable = true;
    /** 补充说明：不可读原因、覆盖范围等 */
    public volatile String note;
    /** 记录是否已结束 */
    public volatile boolean finished;

    public CaptureRecord(long id, int protocol, long startTime, String remoteIp, int remotePort) {
        this.id = id;
        this.protocol = protocol;
        this.startTime = startTime;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.host = remoteIp;
    }

    /** 已耗时；未结束时按当前时间计算 */
    public long durationMs() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return Math.max(0, end - startTime);
    }

    /** 是否 HTTPS 类加密连接 */
    public boolean isEncrypted() {
        return encrypted || protocol == PROTOCOL_HTTPS;
    }

    /** 展示用主机名：域名优先，缺失时用 IP */
    public String displayHost() {
        return host == null || host.isEmpty() ? remoteIp : host;
    }

    /** 展示用地址：主机 + 路径（HTTP） */
    public String displayTarget() {
        String base = displayHost();
        if (protocol == PROTOCOL_HTTP && path != null && !path.isEmpty()) {
            return base + path;
        }
        return base;
    }
}
