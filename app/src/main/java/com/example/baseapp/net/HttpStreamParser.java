package com.example.baseapp.net;

import com.example.baseapp.data.netcapture.CaptureRecord;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 增量解析单个方向的 HTTP 报文（请求或响应）。
 *
 * <p>只做最基础的解析：请求行/状态行、头部、以及可读文本正文。
 * chunked 分块正文会先解码再保存；gzip、br 压缩与二进制正文不会被解码，只标记不可读并记录大小。</p>
 */
class HttpStreamParser {

    /** 单方向最多缓冲 64KB，避免长连接无限增长 */
    private static final int MAX_BUFFER = 64 * 1024;
    /** 可读正文最多保存 48KB */
    private static final int MAX_TEXT_BODY = 48 * 1024;
    /** 头部最多允许 32KB，超过视为异常 */
    private static final int MAX_HEADERS = 32 * 1024;

    private final boolean request;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private boolean headersParsed;
    private boolean truncated;
    private int bodyStart;
    private String contentType = "";
    private long contentLength = -1;
    private boolean chunked;
    private boolean compressed;

    HttpStreamParser(boolean request) {
        this.request = request;
    }

    /** 追加一个方向的数据并尝试解析 */
    void onData(byte[] data, int offset, int length, CaptureRecord record) {
        if (truncated) {
            return;
        }
        int room = MAX_BUFFER - buffer.size();
        if (room <= 0) {
            truncated = true;
            return;
        }
        buffer.write(data, offset, Math.min(length, room));
        parse(record);
    }

    private void parse(CaptureRecord record) {
        byte[] all = buffer.toByteArray();
        if (!headersParsed) {
            int end = findHeaderEnd(all);
            if (end < 0) {
                if (all.length >= MAX_HEADERS) {
                    truncated = true;
                    record.note = "HTTP 头部超过 32KB，已停止解析";
                }
                return;
            }
            headersParsed = true;
            bodyStart = end;
            parseHeaders(all, end, record);
        }
        parseBody(all, record);
    }

    /** 返回 \r\n\r\n 之后的起始下标，未找到返回 -1 */
    private static int findHeaderEnd(byte[] data) {
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    private void parseHeaders(byte[] all, int end, CaptureRecord record) {
        String headerText = new String(all, 0, Math.max(0, end - 4), StandardCharsets.ISO_8859_1);
        if (request) {
            record.requestHeaders = headerText;
        } else {
            record.responseHeaders = headerText;
        }

        int lineEnd = headerText.indexOf("\r\n");
        String firstLine = lineEnd >= 0 ? headerText.substring(0, lineEnd) : headerText;
        if (request) {
            String[] parts = firstLine.split(" ");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                record.method = parts[0];
            }
            if (parts.length > 1) {
                record.path = parts[1];
            }
        } else {
            String[] parts = firstLine.split(" ");
            if (parts.length > 1) {
                try {
                    record.statusCode = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    record.statusCode = -1;
                }
            }
            if (record.statusCode >= 400) {
                record.failed = true;
            }
        }

        String[] lines = headerText.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            String value = lines[i].substring(colon + 1).trim();
            if ("host".equals(name) && request && !value.isEmpty()) {
                record.host = stripPort(value);
            } else if ("content-type".equals(name)) {
                contentType = value.toLowerCase(Locale.US);
            } else if ("content-length".equals(name)) {
                try {
                    contentLength = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    contentLength = -1;
                }
            } else if ("transfer-encoding".equals(name)) {
                chunked = value.toLowerCase(Locale.US).contains("chunked");
            } else if ("content-encoding".equals(name)) {
                // identity 表示未压缩
                compressed = !value.isEmpty() && !"identity".equalsIgnoreCase(value);
            }
        }
    }

    private void parseBody(byte[] all, CaptureRecord record) {
        int available = all.length - bodyStart;
        if (available <= 0 || (contentLength == 0 && !chunked)) {
            return;
        }
        int size = available;
        if (contentLength > 0 && size > contentLength) {
            size = (int) contentLength;
        }
        if (chunked) {
            if (compressed) {
                markUnreadable(record, "分块且压缩的正文未解码", available);
                return;
            }
            // 分块传输先解码再判断是否为可读文本
            if (!isTextLike(contentType, all, bodyStart, Math.min(available, 512))) {
                markUnreadable(record, "正文为二进制内容，不可读", available);
                return;
            }
            String decoded = decodeChunked(all, bodyStart, available);
            if (decoded == null) {
                return;
            }
            if (request) {
                record.requestBody = decoded;
                record.requestBodyReadable = true;
            } else {
                record.responseBody = decoded;
                record.responseBodyReadable = true;
            }
            return;
        }
        if (compressed) {
            markUnreadable(record, "正文为压缩编码（Content-Encoding），未解码", available);
            return;
        }
        if (!isTextLike(contentType, all, bodyStart, size)) {
            markUnreadable(record, "正文为二进制内容，不可读", available);
            return;
        }
        int textLen = Math.min(size, MAX_TEXT_BODY);
        String text = new String(all, bodyStart, textLen, StandardCharsets.UTF_8);
        if (size > textLen) {
            text = text + "\n…（正文超过 48KB，已截断）";
        }
        if (request) {
            record.requestBody = text;
            record.requestBodyReadable = true;
        } else {
            record.responseBody = text;
            record.responseBodyReadable = true;
        }
    }

    private void markUnreadable(CaptureRecord record, String reason, int size) {
        if (request) {
            record.requestBody = null;
            record.requestBodyReadable = false;
        } else {
            record.responseBody = null;
            record.responseBodyReadable = false;
        }
        record.note = reason + "，正文约 " + size + " 字节";
    }

    /** 解码 Transfer-Encoding: chunked 正文；数据不完整或格式异常时返回 null */
    private static String decodeChunked(byte[] data, int offset, int size) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = offset;
        int end = offset + size;
        boolean truncated = false;
        while (pos < end) {
            int lineEnd = indexOfCrlf(data, pos, end);
            if (lineEnd < 0) {
                return null;
            }
            String sizeLine = new String(data, pos, lineEnd - pos, StandardCharsets.ISO_8859_1).trim();
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) {
                sizeLine = sizeLine.substring(0, semicolon);
            }
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                return null;
            }
            pos = lineEnd + 2;
            if (chunkSize == 0) {
                break;
            }
            if (pos + chunkSize > end) {
                // 分块尚未接收完整，等下一批数据再解析
                return null;
            }
            out.write(data, pos, chunkSize);
            pos += chunkSize;
            // 跳过块尾的 CRLF
            if (pos + 2 <= end && data[pos] == '\r' && data[pos + 1] == '\n') {
                pos += 2;
            }
            if (out.size() >= MAX_TEXT_BODY) {
                truncated = true;
                break;
            }
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        return truncated ? text + "\n…（正文超过 48KB，已截断）" : text;
    }

    /** 在指定区间内查找 CRLF，返回 \r 的下标，未找到返回 -1 */
    private static int indexOfCrlf(byte[] data, int from, int end) {
        for (int i = from; i + 1 < end; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /** 按 Content-Type 判断是否文本；缺失时用可打印字符比例兜底 */
    private static boolean isTextLike(String type, byte[] data, int offset, int size) {
        if (!type.isEmpty()) {
            return type.startsWith("text/")
                    || type.contains("json")
                    || type.contains("xml")
                    || type.contains("javascript")
                    || type.contains("x-www-form-urlencoded")
                    || type.contains("graphql");
        }
        int sample = Math.min(size, 512);
        if (sample <= 0) {
            return true;
        }
        int control = 0;
        for (int i = offset; i < offset + sample; i++) {
            int value = data[i] & 0xFF;
            if (value < 0x09 || (value > 0x0D && value < 0x20)) {
                control++;
            }
        }
        return control * 10 < sample;
    }

    /** 去掉 Host 头里的端口 */
    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            return host.substring(0, colon);
        }
        return host;
    }
}
