package com.example.baseapp.net;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP/TCP/UDP 报文解析与校验和工具。
 *
 * <p>只覆盖 IPv4；IPv6 流量不会进入本 VPN 的转发面。</p>
 */
public final class PacketUtils {

    /** IP 头固定长度 */
    public static final int IP_HEADER_LEN = 20;
    /** TCP 头固定长度 */
    public static final int TCP_HEADER_LEN = 20;

    private static final AtomicInteger IP_ID = new AtomicInteger(1);

    private PacketUtils() {
    }

    /** 读取无符号 16 位整数（大端） */
    public static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /** 读取无符号 32 位整数（大端），以 long 返回 */
    public static long readU32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    /** 写入 16 位整数（大端） */
    public static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 8);
        data[offset + 1] = (byte) value;
    }

    /** 写入 32 位整数（大端） */
    public static void writeU32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }

    /** 点分十进制 IP 转字符串 */
    public static String ipv4ToString(byte[] data, int offset) {
        return (data[offset] & 0xFF) + "." + (data[offset + 1] & 0xFF) + "."
                + (data[offset + 2] & 0xFF) + "." + (data[offset + 3] & 0xFF);
    }

    /** 点分十进制 IP 转 4 字节数组 */
    public static byte[] ipv4ToBytes(String ip) {
        byte[] result = new byte[4];
        String[] parts = ip.split("\\.");
        for (int i = 0; i < 4 && i < parts.length; i++) {
            try {
                result[i] = (byte) Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    /** 计算 16 位反码校验和 */
    public static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        int end = offset + length;
        for (int i = offset; i < end; i += 2) {
            int high = data[i] & 0xFF;
            int low = (i + 1 < end) ? data[i + 1] & 0xFF : 0;
            sum += ((high << 8) | low);
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum) & 0xFFFF;
    }

    /** 计算 TCP 校验和：伪首部（源/目的 IP、协议号、TCP 长度）+ TCP 段 */
    public static int tcpChecksum(byte[] packet, int ipOffset, int tcpOffset, int tcpLength) {
        long sum = 0;
        // 伪首部：源地址 + 目的地址（IPv4 头中位于偏移 12–19）
        for (int i = 12; i < 20; i += 2) {
            sum += readU16(packet, ipOffset + i);
        }
        // 协议号 6 + TCP 长度
        sum += 6;
        sum += tcpLength;
        int end = tcpOffset + tcpLength;
        for (int i = tcpOffset; i < end; i += 2) {
            int high = packet[i] & 0xFF;
            int low = (i + 1 < end) ? packet[i + 1] & 0xFF : 0;
            sum += ((high << 8) | low);
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum) & 0xFFFF;
    }

    /** 生成自增 IP 标识 */
    public static int nextIpId() {
        return IP_ID.getAndIncrement() & 0xFFFF;
    }
}
