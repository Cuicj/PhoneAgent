package com.example.baseapp.net;

import com.example.baseapp.data.netcapture.CaptureRecord;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * TLS 元数据观察器：从 ClientHello 中提取 SNI，并根据服务端 TLS 记录类型判断握手结果。
 *
 * <p>本工具不持有任何密钥，无法解密 HTTPS 内容，只做连接层面的观察。</p>
 */
class TlsInspector {

    /** ClientHello 最多缓存 16KB，足够容纳常见扩展 */
    private static final int MAX_CLIENT_BUFFER = 16 * 1024;
    /** 服务端记录最多观察前 8KB */
    private static final int MAX_SERVER_BUFFER = 8 * 1024;

    private final ByteArrayOutputStream clientBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream serverBuffer = new ByteArrayOutputStream();
    private boolean sniParsed;
    private boolean handshakeDecided;

    /** 客户端数据：尝试解析 SNI */
    void onClientData(byte[] data, int offset, int length, CaptureRecord record) {
        if (sniParsed || clientBuffer.size() >= MAX_CLIENT_BUFFER) {
            return;
        }
        clientBuffer.write(data, offset, Math.min(length, MAX_CLIENT_BUFFER - clientBuffer.size()));
        String sni = extractSni(clientBuffer.toByteArray());
        if (sni != null && !sni.isEmpty()) {
            // 域名比 IP 更有排查价值，直接作为展示主机名
            record.host = sni;
            sniParsed = true;
        }
    }

    /** 服务端数据：观察 TLS 记录类型，判断握手是否完成 */
    void onServerData(byte[] data, int offset, int length, CaptureRecord record) {
        if (handshakeDecided || serverBuffer.size() >= MAX_SERVER_BUFFER) {
            return;
        }
        serverBuffer.write(data, offset, Math.min(length, MAX_SERVER_BUFFER - serverBuffer.size()));
        scanRecords(serverBuffer.toByteArray(), record);
    }

    /** 连接结束时补齐默认状态，避免一直停留在「握手中」 */
    void finish(CaptureRecord record) {
        if (record.tlsState == null || "握手中".equals(record.tlsState)) {
            record.tlsState = "未知（连接结束前未观察到应用数据）";
        }
    }

    private void scanRecords(byte[] data, CaptureRecord record) {
        int offset = 0;
        while (offset + 5 <= data.length) {
            int type = data[offset] & 0xFF;
            int length = PacketUtils.readU16(data, offset + 3);
            if (offset + 5 + length > data.length) {
                // 记录尚未收全，等待后续数据
                break;
            }
            if (type == 21) {
                // Alert：服务端拒绝或证书校验失败
                record.tlsState = "失败（服务端 TLS 告警）";
                record.failed = true;
                handshakeDecided = true;
                return;
            }
            if (type == 23) {
                // Application Data：说明握手已完成
                record.tlsState = "成功";
                handshakeDecided = true;
                return;
            }
            offset += 5 + length;
        }
    }

    /**
     * 从 ClientHello 中提取 SNI；数据不足返回 null。
     */
    static String extractSni(byte[] data) {
        if (data.length < 5) {
            return null;
        }
        // TLS 记录类型 22 = Handshake
        if ((data[0] & 0xFF) != 22) {
            return null;
        }
        int recordLength = PacketUtils.readU16(data, 3);
        if (data.length < 5 + recordLength) {
            // ClientHello 记录还没收全
            return null;
        }
        int recordEnd = 5 + recordLength;
        int pos = 5;
        if (pos + 4 > recordEnd || (data[pos] & 0xFF) != 1) {
            return null;
        }
        pos += 4;
        // version(2) + random(32)
        if (pos + 34 > recordEnd) {
            return null;
        }
        pos += 34;
        // session id
        int sessionIdLen = data[pos] & 0xFF;
        pos += 1 + sessionIdLen;
        if (pos + 2 > recordEnd) {
            return null;
        }
        // cipher suites
        int cipherLen = PacketUtils.readU16(data, pos);
        pos += 2 + cipherLen;
        if (pos + 1 > recordEnd) {
            return null;
        }
        // compression methods
        int compressionLen = data[pos] & 0xFF;
        pos += 1 + compressionLen;
        if (pos + 2 > recordEnd) {
            return null;
        }
        int extensionsLength = PacketUtils.readU16(data, pos);
        pos += 2;
        int extensionsEnd = Math.min(recordEnd, pos + extensionsLength);
        while (pos + 4 <= extensionsEnd) {
            int type = PacketUtils.readU16(data, pos);
            int length = PacketUtils.readU16(data, pos + 2);
            pos += 4;
            if (pos + length > extensionsEnd) {
                break;
            }
            if (type == 0) {
                // server_name 扩展
                int listEnd = Math.min(pos + length, pos + 2 + PacketUtils.readU16(data, pos));
                int p = pos + 2;
                while (p + 3 <= listEnd) {
                    int nameType = data[p] & 0xFF;
                    int nameLen = PacketUtils.readU16(data, p + 1);
                    p += 3;
                    if (p + nameLen > listEnd) {
                        break;
                    }
                    if (nameType == 0) {
                        return new String(data, p, nameLen, StandardCharsets.US_ASCII);
                    }
                    p += nameLen;
                }
            }
            pos += length;
        }
        return null;
    }
}
