package com.example.baseapp.net;

import com.example.baseapp.data.netcapture.CaptureRecord;
import com.example.baseapp.data.netcapture.CaptureStore;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP 中继：每个五元组流一个受保护的 DatagramSocket，覆盖 DNS、QUIC 等流量。
 *
 * <p>QUIC 内容加密，无法解密，只记录流量元数据。</p>
 */
class UdpRelay {

    /** 同时跟踪的 UDP 流上限 */
    private static final int MAX_FLOWS = 200;

    private final CaptureVpnService service;
    private final Map<String, UdpFlow> flows = new ConcurrentHashMap<>();

    UdpRelay(CaptureVpnService service) {
        this.service = service;
    }

    /** 处理一个来自 tun 的 IPv4 UDP 包 */
    void handle(byte[] packet, int length, int ipHeaderLength) {
        int totalLength = PacketUtils.readU16(packet, 2);
        if (totalLength <= 0 || totalLength > length) {
            totalLength = length;
        }
        int udpOffset = ipHeaderLength;
        if (udpOffset + 8 > totalLength) {
            return;
        }
        int srcPort = PacketUtils.readU16(packet, udpOffset);
        int dstPort = PacketUtils.readU16(packet, udpOffset + 2);
        int udpLength = PacketUtils.readU16(packet, udpOffset + 4);
        int payloadLength = udpLength - 8;
        int payloadOffset = udpOffset + 8;
        if (payloadLength <= 0 || payloadOffset + payloadLength > totalLength) {
            return;
        }
        String srcIp = PacketUtils.ipv4ToString(packet, 12);
        String dstIp = PacketUtils.ipv4ToString(packet, 16);
        String key = srcPort + "|" + dstIp + "|" + dstPort;

        UdpFlow flow = flows.get(key);
        if (flow == null) {
            if (flows.size() >= MAX_FLOWS) {
                closeOldest();
            }
            flow = new UdpFlow(srcIp, srcPort, dstIp, dstPort, key);
            flows.put(key, flow);
            flow.start();
        }
        flow.send(packet, payloadOffset, payloadLength);
    }

    void remove(String key, UdpFlow flow) {
        flows.remove(key, flow);
    }

    private void closeOldest() {
        for (UdpFlow flow : flows.values()) {
            flow.close();
            return;
        }
    }

    /** 服务停止时释放所有 UDP Socket */
    void closeAll() {
        List<UdpFlow> snapshot = new ArrayList<>(flows.values());
        for (UdpFlow flow : snapshot) {
            flow.close();
        }
        flows.clear();
    }

    /**
     * 单个 UDP 流：记录创建、请求发送、响应回写。
     */
    class UdpFlow {

        private final String clientIp;
        private final int clientPort;
        private final String remoteIp;
        private final int remotePort;
        private final String key;
        private final CaptureRecord record;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private DatagramSocket socket;
        private volatile long lastActiveAt = System.currentTimeMillis();

        UdpFlow(String clientIp, int clientPort, String remoteIp, int remotePort, String key) {
            this.clientIp = clientIp;
            this.clientPort = clientPort;
            this.remoteIp = remoteIp;
            this.remotePort = remotePort;
            this.key = key;
            this.record = CaptureStore.get().add(CaptureRecord.PROTOCOL_UDP, remoteIp, remotePort);
            if (remotePort == 53) {
                record.note = "DNS 查询（UDP 53），仅记录流量元数据与查询域名";
            } else if (remotePort == 443) {
                record.encrypted = true;
                record.note = "QUIC（UDP 443，内容加密），仅记录流量元数据";
            } else {
                record.note = "UDP 流，仅记录流量元数据";
            }
        }

        /** 同步创建并保护 Socket，保证首包不会被丢弃 */
        void start() {
            try {
                DatagramSocket s = new DatagramSocket();
                service.protect(s);
                s.connect(new InetSocketAddress(remoteIp, remotePort));
                s.setSoTimeout(30_000);
                socket = s;
            } catch (Exception e) {
                record.failed = true;
                record.note = "UDP Socket 创建失败：" + e.getMessage();
                close();
                return;
            }
            Thread thread = new Thread(this::readLoop, "capture-udp-" + clientPort);
            thread.setDaemon(true);
            thread.start();
        }

        /** 发送客户端 UDP 载荷到远端 */
        void send(byte[] packet, int offset, int length) {
            lastActiveAt = System.currentTimeMillis();
            record.upBytes += length;
            if (remotePort == 53 && remoteIp.equals(record.host)) {
                String domain = parseDnsName(packet, offset, length);
                if (domain != null) {
                    record.host = domain;
                }
            }
            DatagramSocket s = socket;
            if (s == null) {
                return;
            }
            try {
                s.send(new DatagramPacket(packet, offset, length,
                        new InetSocketAddress(remoteIp, remotePort)));
            } catch (IOException e) {
                record.failed = true;
                record.note = "UDP 发送失败：" + e.getMessage();
            }
        }

        private void readLoop() {
            DatagramSocket s = socket;
            if (s == null) {
                close();
                return;
            }
            try {
                byte[] buffer = new byte[65535];
                while (!closed.get()) {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    try {
                        s.receive(response);
                    } catch (SocketTimeoutException timeout) {
                        if (System.currentTimeMillis() - lastActiveAt > 60_000) {
                            break;
                        }
                        continue;
                    }
                    lastActiveAt = System.currentTimeMillis();
                    int size = response.getLength();
                    record.downBytes += size;
                    sendToClient(buffer, size);
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    record.failed = true;
                    record.note = "UDP 接收失败：" + e.getMessage();
                }
            } finally {
                close();
            }
        }

        /** 把远端 UDP 响应封装成 IPv4/UDP 包写回 tun */
        private void sendToClient(byte[] payload, int payloadLength) {
            int totalLength = PacketUtils.IP_HEADER_LEN + 8 + payloadLength;
            byte[] packet = new byte[totalLength];
            packet[0] = 0x45;
            packet[1] = 0;
            PacketUtils.writeU16(packet, 2, totalLength);
            PacketUtils.writeU16(packet, 4, PacketUtils.nextIpId());
            packet[6] = 0x40;
            packet[8] = 64;
            packet[9] = 17;
            System.arraycopy(PacketUtils.ipv4ToBytes(remoteIp), 0, packet, 12, 4);
            System.arraycopy(PacketUtils.ipv4ToBytes(clientIp), 0, packet, 16, 4);
            PacketUtils.writeU16(packet, 10, PacketUtils.checksum(packet, 0, PacketUtils.IP_HEADER_LEN));

            int udp = PacketUtils.IP_HEADER_LEN;
            PacketUtils.writeU16(packet, udp, remotePort);
            PacketUtils.writeU16(packet, udp + 2, clientPort);
            PacketUtils.writeU16(packet, udp + 4, 8 + payloadLength);
            // IPv4 允许 UDP 校验和为 0（表示未计算）
            PacketUtils.writeU16(packet, udp + 6, 0);
            System.arraycopy(payload, 0, packet, udp + 8, payloadLength);
            service.writeToTun(packet, totalLength);
        }

        /** 关闭 Socket 并把记录标记为结束 */
        void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            DatagramSocket s = socket;
            socket = null;
            if (s != null) {
                s.close();
            }
            record.finished = true;
            record.endTime = System.currentTimeMillis();
            remove(key, this);
        }
    }

    /**
     * 从 DNS 查询报文中提取第一个查询域名，解析失败返回 null。
     */
    static String parseDnsName(byte[] data, int offset, int length) {
        if (length < 12) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int pos = offset + 12;
        int end = offset + length;
        int guard = 0;
        while (pos < end && guard++ < 64) {
            int labelLength = data[pos] & 0xFF;
            if (labelLength == 0) {
                break;
            }
            // 查询名一般是完整标签，压缩指针只出现在响应中
            if ((labelLength & 0xC0) != 0) {
                break;
            }
            pos++;
            if (pos + labelLength > end) {
                break;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(new String(data, pos, labelLength, StandardCharsets.US_ASCII));
            pos += labelLength;
        }
        return builder.length() == 0 ? null : builder.toString();
    }
}
