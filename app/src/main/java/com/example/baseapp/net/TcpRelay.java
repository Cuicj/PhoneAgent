package com.example.baseapp.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP 连接中继：按 (客户端端口, 远端 IP, 远端端口) 维护连接，
 * 每个连接独立线程读取远端响应。
 */
class TcpRelay {

    /** 同时跟踪的连接上限，超出后关闭最早建立的连接 */
    private static final int MAX_CONNECTIONS = 200;

    private final CaptureVpnService service;
    private final Map<String, TcpConnection> connections = new ConcurrentHashMap<>();

    TcpRelay(CaptureVpnService service) {
        this.service = service;
    }

    /** 处理一个来自 tun 的 IPv4 TCP 包 */
    void handle(byte[] packet, int length, int ipHeaderLength) {
        int totalLength = PacketUtils.readU16(packet, 2);
        if (totalLength <= 0 || totalLength > length) {
            totalLength = length;
        }
        int tcpOffset = ipHeaderLength;
        if (tcpOffset + PacketUtils.TCP_HEADER_LEN > totalLength) {
            return;
        }
        int srcPort = PacketUtils.readU16(packet, tcpOffset);
        int dstPort = PacketUtils.readU16(packet, tcpOffset + 2);
        long seq = PacketUtils.readU32(packet, tcpOffset + 4);
        int flags = packet[tcpOffset + 13] & 0xFF;
        if ((flags & 0x04) != 0) {
            // RST 不转发
            return;
        }
        int dataOffset = ((packet[tcpOffset + 12] & 0xF0) >> 4) * 4;
        int payloadLength = totalLength - tcpOffset - dataOffset;
        if (payloadLength < 0) {
            payloadLength = 0;
        }
        int payloadOffset = tcpOffset + dataOffset;

        String srcIp = PacketUtils.ipv4ToString(packet, 12);
        String dstIp = PacketUtils.ipv4ToString(packet, 16);
        String key = srcPort + "|" + dstIp + "|" + dstPort;

        boolean syn = (flags & 0x02) != 0;
        boolean ack = (flags & 0x10) != 0;
        if (syn && !ack) {
            // 新连接：丢弃同键旧连接后重新建立
            TcpConnection existing = connections.remove(key);
            if (existing != null) {
                existing.closeByRelay();
            }
            if (connections.size() >= MAX_CONNECTIONS) {
                closeOldest();
            }
            TcpConnection connection = new TcpConnection(service, this, key,
                    srcIp, srcPort, dstIp, dstPort, seq);
            connections.put(key, connection);
            connection.start();
            return;
        }

        TcpConnection connection = connections.get(key);
        if (connection == null) {
            // 连接未被跟踪（可能已清理），后续包直接忽略
            return;
        }
        long ackSeq = PacketUtils.readU32(packet, tcpOffset + 8);
        connection.onPacket(seq, ackSeq, flags, packet, payloadOffset, payloadLength);
    }

    void remove(String key, TcpConnection connection) {
        connections.remove(key, connection);
    }

    /** 关闭最旧的一个连接，控制内存与线程数量 */
    private void closeOldest() {
        for (TcpConnection connection : connections.values()) {
            connection.closeByRelay();
            return;
        }
    }

    /** 服务停止时释放所有连接 */
    void closeAll() {
        List<TcpConnection> snapshot = new ArrayList<>(connections.values());
        for (TcpConnection connection : snapshot) {
            connection.closeByRelay();
        }
        connections.clear();
    }
}
