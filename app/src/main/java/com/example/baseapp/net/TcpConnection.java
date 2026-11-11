package com.example.baseapp.net;

import com.example.baseapp.data.netcapture.CaptureRecord;
import com.example.baseapp.data.netcapture.CaptureStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单条 TCP 连接的用户态转发与记录。
 *
 * <p>在 tun 侧实现最小 TCP 状态机：SYN-ACK、ACK、PSH、FIN、RST；
 * 把客户端数据写入受保护的 Socket，把 Socket 响应重新封装成 IP/TCP 包写回 tun。
 * 本实现不处理拥塞控制、窗口缩放与重排序，仅保证本机代理场景下的可用性。</p>
 */
class TcpConnection {

    private static final long MASK = 0xFFFFFFFFL;

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_SYN = 0x02;
    private static final int FLAG_RST = 0x04;
    private static final int FLAG_PSH = 0x08;
    private static final int FLAG_ACK = 0x10;

    /** 上行队列上限，防止远端写入阻塞时无限堆积 */
    private static final int MAX_QUEUE = 256;
    /** 空闲多久后主动关闭连接 */
    private static final long IDLE_TIMEOUT_MS = 120_000L;

    private final CaptureVpnService service;
    private final TcpRelay relay;
    private final String key;
    private final String clientIp;
    private final int clientPort;
    private final String remoteIp;
    private final int remotePort;
    private final CaptureRecord record;
    private final HttpStreamParser requestParser;
    private final HttpStreamParser responseParser;
    private final TlsInspector tlsInspector;

    private final LinkedBlockingQueue<byte[]> uploadQueue = new LinkedBlockingQueue<>(MAX_QUEUE);
    private final AtomicBoolean cleaned = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile OutputStream outputStream;
    private volatile InputStream inputStream;
    private volatile long clientNextSeq;
    private volatile long sendSeq;
    private volatile long lastActiveAt = System.currentTimeMillis();
    private volatile boolean clientClosed;
    private volatile boolean closed;

    TcpConnection(CaptureVpnService service, TcpRelay relay, String key,
                  String clientIp, int clientPort, String remoteIp, int remotePort, long synSeq) {
        this.service = service;
        this.relay = relay;
        this.key = key;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;

        int protocol;
        if (remotePort == 80) {
            protocol = CaptureRecord.PROTOCOL_HTTP;
        } else if (remotePort == 443) {
            protocol = CaptureRecord.PROTOCOL_HTTPS;
        } else {
            protocol = CaptureRecord.PROTOCOL_TCP;
        }
        record = CaptureStore.get().add(protocol, remoteIp, remotePort);
        record.encrypted = protocol == CaptureRecord.PROTOCOL_HTTPS;
        if (protocol == CaptureRecord.PROTOCOL_HTTPS) {
            record.tlsState = "握手中";
        } else if (protocol == CaptureRecord.PROTOCOL_TCP) {
            record.note = "非 80/443 端口，仅记录连接元数据";
        }
        requestParser = protocol == CaptureRecord.PROTOCOL_HTTP ? new HttpStreamParser(true) : null;
        responseParser = protocol == CaptureRecord.PROTOCOL_HTTP ? new HttpStreamParser(false) : null;
        tlsInspector = protocol == CaptureRecord.PROTOCOL_HTTPS ? new TlsInspector() : null;

        // 客户端 SYN 占用一个序号，后续数据从 +1 开始
        clientNextSeq = (synSeq + 1) & MASK;
        sendSeq = ((long) (Math.random() * Integer.MAX_VALUE)) & MASK;
    }

    /** 回 SYN-ACK 并异步建立到远端的 Socket */
    void start() {
        sendTcp(FLAG_SYN | FLAG_ACK, sendSeq, clientNextSeq, null, 0);
        sendSeq = (sendSeq + 1) & MASK;
        Thread connector = new Thread(this::connectAndRead, "capture-tcp-" + clientPort);
        connector.setDaemon(true);
        connector.start();
    }

    /** 建立 Socket 并持续读取远端响应 */
    private void connectAndRead() {
        try {
            Socket s = new Socket();
            // 代理 Socket 必须绕过 VPN，否则会再次被 tun 捕获形成环路
            service.protect(s);
            s.setTcpNoDelay(true);
            s.setSoTimeout(60_000);
            s.connect(new InetSocketAddress(remoteIp, remotePort), 8000);
            socket = s;
            outputStream = s.getOutputStream();
            inputStream = s.getInputStream();
            startUploader();

            byte[] buffer = new byte[1400];
            while (!closed) {
                int read;
                try {
                    read = inputStream.read(buffer);
                } catch (SocketTimeoutException timeout) {
                    if (System.currentTimeMillis() - lastActiveAt > IDLE_TIMEOUT_MS) {
                        break;
                    }
                    continue;
                }
                if (read < 0) {
                    break;
                }
                lastActiveAt = System.currentTimeMillis();
                record.downBytes += read;
                if (responseParser != null) {
                    responseParser.onData(buffer, 0, read, record);
                }
                if (tlsInspector != null) {
                    tlsInspector.onServerData(buffer, 0, read, record);
                }
                sendTcp(FLAG_PSH | FLAG_ACK, sendSeq, clientNextSeq, buffer, read);
                sendSeq = (sendSeq + read) & MASK;
            }
            if (!closed) {
                // 服务端关闭：回 FIN 给客户端
                sendTcp(FLAG_FIN | FLAG_ACK, sendSeq, clientNextSeq, null, 0);
                sendSeq = (sendSeq + 1) & MASK;
                finish();
            }
        } catch (IOException e) {
            if (!closed) {
                record.failed = true;
                record.note = "连接失败：" + describe(e);
                sendTcp(FLAG_RST | FLAG_ACK, sendSeq, clientNextSeq, null, 0);
                finish();
            }
        } finally {
            cleanup();
        }
    }

    /** 启动上行写入线程，避免 tun 读取线程被远端写阻塞拖慢 */
    private void startUploader() {
        Thread uploader = new Thread(() -> {
            try {
                while (!closed) {
                    byte[] chunk = uploadQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (chunk == null) {
                        if (clientClosed && uploadQueue.isEmpty()) {
                            break;
                        }
                        continue;
                    }
                    outputStream.write(chunk);
                    outputStream.flush();
                }
                if (clientClosed && !closed && socket != null) {
                    // 客户端已 FIN，通知远端本方向结束
                    try {
                        socket.shutdownOutput();
                    } catch (IOException ignored) {
                    }
                }
            } catch (Exception e) {
                if (!closed) {
                    record.failed = true;
                    record.note = "上行写入失败：" + describe(e);
                }
            }
        }, "capture-tcp-upload-" + clientPort);
        uploader.setDaemon(true);
        uploader.start();
    }

    /** 处理客户端发来的 TCP 包 */
    void onPacket(long seq, long ack, int flags, byte[] packet, int payloadOffset, int payloadLength) {
        if (closed) {
            return;
        }
        lastActiveAt = System.currentTimeMillis();
        if ((flags & FLAG_RST) != 0) {
            cleanup();
            return;
        }

        boolean fin = (flags & FLAG_FIN) != 0;
        if (payloadLength > 0) {
            if (seq == clientNextSeq) {
                byte[] chunk = new byte[payloadLength];
                System.arraycopy(packet, payloadOffset, chunk, 0, payloadLength);
                record.upBytes += payloadLength;
                if (requestParser != null) {
                    requestParser.onData(chunk, 0, chunk.length, record);
                }
                if (tlsInspector != null) {
                    tlsInspector.onClientData(chunk, 0, chunk.length, record);
                }
                enqueue(chunk);
                clientNextSeq = (clientNextSeq + payloadLength) & MASK;
                sendTcp(FLAG_ACK, sendSeq, clientNextSeq, null, 0);
            } else {
                // 重复包或乱序包：重新告知期望序号，交给客户端重传
                sendTcp(FLAG_ACK, sendSeq, clientNextSeq, null, 0);
            }
        }

        if (fin) {
            clientNextSeq = (clientNextSeq + 1) & MASK;
            sendTcp(FLAG_ACK, sendSeq, clientNextSeq, null, 0);
            clientClosed = true;
        }
    }

    /** 入队上行数据；队列满说明远端长时间不消费，主动重置连接 */
    private void enqueue(byte[] chunk) {
        if (!uploadQueue.offer(chunk)) {
            record.failed = true;
            record.note = "上行队列已满，连接被重置";
            sendTcp(FLAG_RST | FLAG_ACK, sendSeq, clientNextSeq, null, 0);
            cleanup();
        }
    }

    /** 由中继主动关闭（超出连接上限或被新握手替换） */
    void closeByRelay() {
        cleanup();
    }

    private void finish() {
        if (tlsInspector != null) {
            tlsInspector.finish(record);
        }
        record.finished = true;
        record.endTime = System.currentTimeMillis();
    }

    private void cleanup() {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        closed = true;
        if (!record.finished) {
            finish();
        }
        Socket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        relay.remove(key, this);
    }

    /**
     * 构造并写入一个 TCP 包（源为远端，目的为客户端）。
     */
    private void sendTcp(int flags, long seq, long ack, byte[] payload, int payloadLength) {
        if (service == null) {
            return;
        }
        int totalLength = PacketUtils.IP_HEADER_LEN + PacketUtils.TCP_HEADER_LEN + payloadLength;
        byte[] packet = new byte[totalLength];
        // IPv4 头
        packet[0] = 0x45;
        packet[1] = 0;
        PacketUtils.writeU16(packet, 2, totalLength);
        PacketUtils.writeU16(packet, 4, PacketUtils.nextIpId());
        packet[6] = 0x40;
        packet[7] = 0;
        packet[8] = 64;
        packet[9] = 6;
        System.arraycopy(PacketUtils.ipv4ToBytes(remoteIp), 0, packet, 12, 4);
        System.arraycopy(PacketUtils.ipv4ToBytes(clientIp), 0, packet, 16, 4);
        PacketUtils.writeU16(packet, 10, PacketUtils.checksum(packet, 0, PacketUtils.IP_HEADER_LEN));

        // TCP 头
        int tcp = PacketUtils.IP_HEADER_LEN;
        PacketUtils.writeU16(packet, tcp, remotePort);
        PacketUtils.writeU16(packet, tcp + 2, clientPort);
        PacketUtils.writeU32(packet, tcp + 4, seq);
        PacketUtils.writeU32(packet, tcp + 8, ack);
        packet[tcp + 12] = 0x50;
        packet[tcp + 13] = (byte) flags;
        PacketUtils.writeU16(packet, tcp + 14, 65535);
        PacketUtils.writeU16(packet, tcp + 16, 0);
        PacketUtils.writeU16(packet, tcp + 18, 0);
        if (payloadLength > 0 && payload != null) {
            System.arraycopy(payload, 0, packet, tcp + PacketUtils.TCP_HEADER_LEN, payloadLength);
        }
        PacketUtils.writeU16(packet, tcp + 16,
                PacketUtils.tcpChecksum(packet, 0, tcp, PacketUtils.TCP_HEADER_LEN + payloadLength));
        service.writeToTun(packet, totalLength);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
