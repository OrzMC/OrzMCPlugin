package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 飞书长连接 WS 二进制帧编解码（批次4 F3，proto2 wire format 手写最小实现；协议对照官方
 * larksuite-oapi-sdk-rs 0.3.11 {@code proto/ws.proto} + {@code src/ws.rs}，无 protobuf 依赖）。
 *
 * <p>平台帧结构（二进制 protobuf，非文本）：{@code Frame{SeqID=1 uint64, LogID=2 uint64, service=3 int32,
 * method=4 int32, headers=5 repeated Header{key=1,value=2 string}, payload_encoding=6 string,
 * payload_type=7 string, payload=8 bytes, LogIDNew=9 string}}。method: {@value #METHOD_CONTROL}（控制帧，
 * headers.type ∈ ping/pong）/ {@value #METHOD_DATA}（数据帧，headers.type=event，payload 为事件 JSON v2）。
 * 客户端心跳与对端 ping 回 pong、事件处理后回 ACK 帧均按 SDK 构型实现（{@link #encode}）。</p>
 *
 * <p>只实现本网关所需字段的定长编解码（未知字段按 wire type 跳过，向前兼容）。</p>
 */
public final class FeishuFrame {

    // method
    public static final int METHOD_CONTROL = 0;
    public static final int METHOD_DATA = 1;

    // header keys / type 值（SDK HEADER_* / MSG_TYPE_* 常量）
    public static final String HEADER_TYPE = "type";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    public static final String HEADER_SUM = "sum";
    public static final String HEADER_SEQ = "seq";
    public static final String HEADER_MESSAGE_ID = "message_id";
    public static final String HEADER_BIZ_RT = "biz_rt";

    // Frame 字段号
    private static final int FIELD_SEQ_ID = 1;
    private static final int FIELD_LOG_ID = 2;
    private static final int FIELD_SERVICE = 3;
    private static final int FIELD_METHOD = 4;
    private static final int FIELD_HEADERS = 5;
    private static final int FIELD_PAYLOAD_ENCODING = 6;
    private static final int FIELD_PAYLOAD_TYPE = 7;
    private static final int FIELD_PAYLOAD = 8;
    private static final int FIELD_LOG_ID_NEW = 9;

    // Header 字段号
    private static final int HEADER_FIELD_KEY = 1;
    private static final int HEADER_FIELD_VALUE = 2;

    // wire types
    private static final int WIRE_VARINT = 0;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_LENGTH = 2;
    private static final int WIRE_FIXED32 = 5;

    /** 帧头（headers 有序，SDK 按 key 查找不要求有序；重复 key 仅首个生效——本网关不发送重复 key）。 */
    public record Header(String key, String value) {}

    private final long seqId;
    private final long logId;
    private final int service;
    private final int method;
    private final List<Header> headers;
    private final String payloadEncoding;
    private final String payloadType;
    private final byte[] payload;
    private final String logIdNew;

    private FeishuFrame(
            long seqId,
            long logId,
            int service,
            int method,
            List<Header> headers,
            String payloadEncoding,
            String payloadType,
            byte[] payload,
            String logIdNew) {
        this.seqId = seqId;
        this.logId = logId;
        this.service = service;
        this.method = method;
        this.headers = headers;
        this.payloadEncoding = payloadEncoding;
        this.payloadType = payloadType;
        this.payload = payload;
        this.logIdNew = logIdNew;
    }

    // =====================================================================
    // 工厂（对齐 SDK 各帧构型）
    // =====================================================================

    /** 客户端心跳 ping（SDK new_ping_frame：service=当前 service_id，headers=[{type,ping}]，seq/log 为 0）。 */
    public static FeishuFrame ping(int serviceId) {
        return new FeishuFrame(
                0, 0, serviceId, METHOD_CONTROL, List.of(new Header(HEADER_TYPE, TYPE_PING)), null, null, null, null);
    }

    /** 对服务端 ping 回 pong（SDK：method=CONTROL，service/seq/log 回显原帧，headers=[{type,pong}]）。 */
    public static FeishuFrame pong(FeishuFrame serverPing) {
        return new FeishuFrame(
                serverPing.seqId,
                serverPing.logId,
                serverPing.service,
                METHOD_CONTROL,
                List.of(new Header(HEADER_TYPE, TYPE_PONG)),
                null,
                null,
                null,
                null);
    }

    /**
     * 事件处理后的 ACK 帧（SDK handle_data_frame：回显 seq/log/service/method，headers=原帧 headers +
     * biz_rt，payload=响应 JSON 字节）。code 200 表示已成功处理（平台停止重推）；500 触发平台重推。
     *
     * @param payload ack JSON（如 {@code {"code":200}}）UTF-8 字节
     */
    public static FeishuFrame eventAck(FeishuFrame event, long bizRtMs, byte[] payload) {
        List<Header> headers = new ArrayList<>(event.headers);
        headers.add(new Header(HEADER_BIZ_RT, Long.toString(bizRtMs)));
        return new FeishuFrame(
                event.seqId,
                event.logId,
                event.service,
                event.method,
                headers,
                event.payloadEncoding,
                event.payloadType,
                payload,
                event.logIdNew);
    }

    // =====================================================================
    // 访问器
    // =====================================================================

    public long seqId() {
        return seqId;
    }

    public long logId() {
        return logId;
    }

    public int service() {
        return service;
    }

    public int method() {
        return method;
    }

    public List<Header> headers() {
        return headers;
    }

    public byte[] payload() {
        return payload;
    }

    /** 按 key 取首个 header 值；缺失返回 null。 */
    public String header(String key) {
        if (headers == null) {
            return null;
        }
        for (Header h : headers) {
            if (h.key().equals(key)) {
                return h.value();
            }
        }
        return null;
    }

    public String type() {
        return header(HEADER_TYPE);
    }

    // =====================================================================
    // protobuf 编解码（proto2 wire format，最小实现）
    // =====================================================================

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        writeVarintField(out, FIELD_SEQ_ID, WIRE_VARINT, seqId);
        writeVarintField(out, FIELD_LOG_ID, WIRE_VARINT, logId);
        writeVarintField(out, FIELD_SERVICE, WIRE_VARINT, service);
        writeVarintField(out, FIELD_METHOD, WIRE_VARINT, method);
        if (headers != null) {
            for (Header h : headers) {
                writeLengthDelimited(out, FIELD_HEADERS, encodeHeader(h));
            }
        }
        if (payloadEncoding != null) {
            writeStringField(out, FIELD_PAYLOAD_ENCODING, payloadEncoding);
        }
        if (payloadType != null) {
            writeStringField(out, FIELD_PAYLOAD_TYPE, payloadType);
        }
        if (payload != null) {
            writeLengthDelimited(out, FIELD_PAYLOAD, payload);
        }
        if (logIdNew != null) {
            writeStringField(out, FIELD_LOG_ID_NEW, logIdNew);
        }
        return out.toByteArray();
    }

    private static byte[] encodeHeader(Header h) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16);
        writeStringField(out, HEADER_FIELD_KEY, h.key());
        writeStringField(out, HEADER_FIELD_VALUE, h.value());
        return out.toByteArray();
    }

    /** 解码帧；非法输入（截断/字段类型不符）抛 {@link IllegalArgumentException}。 */
    public static FeishuFrame decode(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("frame data must not be null");
        }
        Reader r = new Reader(data);
        long seqId = 0;
        long logId = 0;
        int service = 0;
        int method = 0;
        List<Header> headers = new ArrayList<>();
        String payloadEncoding = null;
        String payloadType = null;
        byte[] payload = null;
        String logIdNew = null;
        while (r.remaining() > 0) {
            long tag = r.readVarint();
            int field = (int) (tag >> 3);
            int wire = (int) (tag & 0x7);
            switch (field) {
                case FIELD_SEQ_ID -> seqId = readUnsignedVarint(r, wire, field);
                case FIELD_LOG_ID -> logId = readUnsignedVarint(r, wire, field);
                case FIELD_SERVICE -> service = (int) readUnsignedVarint(r, wire, field);
                case FIELD_METHOD -> method = (int) readUnsignedVarint(r, wire, field);
                case FIELD_HEADERS -> {
                    requireWire(wire, WIRE_LENGTH, field);
                    headers.add(decodeHeader(r.readBytes()));
                }
                case FIELD_PAYLOAD_ENCODING -> payloadEncoding = readString(r, wire, field);
                case FIELD_PAYLOAD_TYPE -> payloadType = readString(r, wire, field);
                case FIELD_PAYLOAD -> {
                    requireWire(wire, WIRE_LENGTH, field);
                    payload = r.readBytes();
                }
                case FIELD_LOG_ID_NEW -> logIdNew = readString(r, wire, field);
                default -> skip(r, wire);
            }
        }
        return new FeishuFrame(seqId, logId, service, method, headers, payloadEncoding, payloadType, payload, logIdNew);
    }

    private static Header decodeHeader(byte[] bytes) {
        Reader r = new Reader(bytes);
        String key = null;
        String value = null;
        while (r.remaining() > 0) {
            long tag = r.readVarint();
            int field = (int) (tag >> 3);
            int wire = (int) (tag & 0x7);
            switch (field) {
                case HEADER_FIELD_KEY -> key = readString(r, wire, field);
                case HEADER_FIELD_VALUE -> value = readString(r, wire, field);
                default -> skip(r, wire);
            }
        }
        return new Header(key == null ? "" : key, value == null ? "" : value);
    }

    // ---------------------------------------------------------------------
    // writer helpers
    // ---------------------------------------------------------------------

    private static void writeVarintField(ByteArrayOutputStream out, int field, int wire, long value) {
        writeVarint(out, ((long) field << 3) | wire);
        writeVarint(out, value);
    }

    private static void writeStringField(ByteArrayOutputStream out, int field, String value) {
        writeLengthDelimited(out, field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeLengthDelimited(ByteArrayOutputStream out, int field, byte[] value) {
        writeVarint(out, ((long) field << 3) | WIRE_LENGTH);
        writeVarint(out, value.length);
        out.writeBytes(value);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long v = value;
        while (true) {
            if ((v & ~0x7FL) == 0) {
                out.write((int) v);
                return;
            }
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
    }

    // ---------------------------------------------------------------------
    // reader helpers
    // ---------------------------------------------------------------------

    private static long readUnsignedVarint(Reader r, int wire, int field) {
        requireWire(wire, WIRE_VARINT, field);
        return r.readVarint();
    }

    private static String readString(Reader r, int wire, int field) {
        requireWire(wire, WIRE_LENGTH, field);
        return new String(r.readBytes(), StandardCharsets.UTF_8);
    }

    private static void requireWire(int wire, int expected, int field) {
        if (wire != expected) {
            throw new IllegalArgumentException(
                    "field " + field + " wire type mismatch: expected " + expected + " got " + wire);
        }
    }

    private static void skip(Reader r, int wire) {
        switch (wire) {
            case WIRE_VARINT -> r.readVarint();
            case WIRE_FIXED64 -> r.skip(8);
            case WIRE_LENGTH -> r.skipBytes(r.readVarint());
            case WIRE_FIXED32 -> r.skip(4);
            default -> throw new IllegalArgumentException("unsupported wire type " + wire);
        }
    }

    /** 输入字节游标读取器。 */
    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) {
            this.buf = buf;
        }

        int remaining() {
            return buf.length - pos;
        }

        long readVarint() {
            long value = 0;
            int shift = 0;
            while (true) {
                if (pos >= buf.length) {
                    throw new IllegalArgumentException("truncated varint");
                }
                int b = buf[pos++] & 0xFF;
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
                if (shift >= 64) {
                    throw new IllegalArgumentException("varint too long");
                }
            }
        }

        byte[] readBytes() {
            long len = readVarint();
            if (len < 0 || len > buf.length - pos) {
                throw new IllegalArgumentException("length-delimited out of range: " + len);
            }
            return readBytesExact((int) len);
        }

        private byte[] readBytesExact(int len) {
            byte[] out = new byte[len];
            System.arraycopy(buf, pos, out, 0, len);
            pos += len;
            return out;
        }

        void skip(long n) {
            if (n < 0 || n > remaining()) {
                throw new IllegalArgumentException("skip out of range: " + n);
            }
            pos += (int) n;
        }

        void skipBytes(long n) {
            skip(n);
        }
    }
}
