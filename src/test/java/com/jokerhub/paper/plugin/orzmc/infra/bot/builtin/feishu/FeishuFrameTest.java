package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuFrame.Header;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FeishuFrame protobuf 帧编解码单测（F3a）：ping/pong/event/ack 各帧 encode→decode 往返、字段值保持、
 * 未知字段跳过（向前兼容）、非法输入拒绝。帧格式对照官方 SDK proto/ws.proto（proto2 wire format）。
 */
class FeishuFrameTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void pingFrame_roundTrip() {
        byte[] bytes = FeishuFrame.ping(42).encode();
        FeishuFrame back = FeishuFrame.decode(bytes);
        assertEquals(0L, back.seqId());
        assertEquals(0L, back.logId());
        assertEquals(42, back.service());
        assertEquals(FeishuFrame.METHOD_CONTROL, back.method());
        assertEquals(FeishuFrame.TYPE_PING, back.type());
        assertNull(back.payload());
    }

    @Test
    void pongFrame_echoesServerPingFields() {
        // 服务端 ping：seq/log 非 0（SDK 构型）
        FeishuFrame serverPing = FeishuFrame.decode(new FrameBuilder()
                .field(1, 0x1122334455L)
                .field(2, 0xAABBCCDDL)
                .field(3, 7)
                .field(4, 0)
                .header(FeishuFrame.HEADER_TYPE, FeishuFrame.TYPE_PING)
                .build());

        FeishuFrame pong = FeishuFrame.pong(serverPing);
        FeishuFrame back = FeishuFrame.decode(pong.encode());
        assertEquals(0x1122334455L, back.seqId(), "pong 回显服务端 ping 的 seq");
        assertEquals(0xAABBCCDDL, back.logId(), "pong 回显 log");
        assertEquals(7, back.service());
        assertEquals(FeishuFrame.METHOD_CONTROL, back.method());
        assertEquals(FeishuFrame.TYPE_PONG, back.type());
    }

    @Test
    void eventFrame_roundTrip_preservesHeadersAndPayload() {
        String payload = "{\"schema\":\"2.0\",\"header\":{\"event_type\":\"im.message.receive_v1\"}}";
        FeishuFrame event = FeishuFrame.decode(new FrameBuilder()
                .field(1, 99L)
                .field(2, 88L)
                .field(3, 12)
                .field(4, FeishuFrame.METHOD_DATA)
                .header(FeishuFrame.HEADER_TYPE, FeishuFrame.TYPE_EVENT)
                .header(FeishuFrame.HEADER_SUM, "1")
                .field(8, utf8(payload))
                .build());

        assertEquals("event", event.type());
        assertEquals("1", event.header(FeishuFrame.HEADER_SUM));
        assertEquals(payload, new String(event.payload(), StandardCharsets.UTF_8));
        assertEquals(FeishuFrame.METHOD_DATA, event.method());
    }

    @Test
    void eventAck_keepsSeqLogAndAddsBizRt() {
        FeishuFrame event = FeishuFrame.decode(new FrameBuilder()
                .field(1, 7L)
                .field(2, 9L)
                .field(3, 3)
                .field(4, FeishuFrame.METHOD_DATA)
                .header(FeishuFrame.HEADER_TYPE, FeishuFrame.TYPE_EVENT)
                .build());
        byte[] ackPayload = utf8("{\"code\":200}");

        FeishuFrame ack = FeishuFrame.eventAck(event, 12L, ackPayload);
        FeishuFrame back = FeishuFrame.decode(ack.encode());
        assertEquals(7L, back.seqId(), "ACK 回显事件 seq");
        assertEquals(9L, back.logId());
        assertEquals(FeishuFrame.METHOD_DATA, back.method(), "ACK method 保持 DATA");
        assertEquals("12", back.header(FeishuFrame.HEADER_BIZ_RT));
        assertArrayEquals(ackPayload, back.payload());
    }

    @Test
    void unknownField_isSkipped_forwardCompatible() {
        byte[] bytes = new FrameBuilder()
                .field(1, 5L)
                .field(99, utf8("future-field")) // 未知字段号（length-delimited）
                .field(4, FeishuFrame.METHOD_DATA)
                .build();
        FeishuFrame frame = FeishuFrame.decode(bytes);
        assertEquals(5L, frame.seqId());
        assertEquals(FeishuFrame.METHOD_DATA, frame.method());
    }

    @Test
    void truncatedInput_isRejected() {
        byte[] good = FeishuFrame.ping(1).encode();
        byte[] truncated = new byte[good.length - 1];
        System.arraycopy(good, 0, truncated, 0, truncated.length);
        assertThrows(IllegalArgumentException.class, () -> FeishuFrame.decode(truncated));
    }

    @Test
    void emptyInput_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> FeishuFrame.decode(null));
    }

    @Test
    void headerListOrderPreserved() {
        FeishuFrame f = FeishuFrame.decode(new FrameBuilder()
                .header("a", "1")
                .header("b", "2")
                .field(4, FeishuFrame.METHOD_CONTROL)
                .build());
        List<Header> hs = f.headers();
        assertEquals(2, hs.size());
        assertEquals(new Header("a", "1"), hs.get(0));
        assertEquals(new Header("b", "2"), hs.get(1));
    }

    /** 便捷帧构造器：按字段号写 wire 数据（编码正确性自身由往返测试兜底，此处侧重结构组合）。 */
    private static final class FrameBuilder {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        FrameBuilder field(int fieldNo, long varintValue) {
            writeVarint((long) fieldNo << 3);
            writeVarint(varintValue);
            return this;
        }

        FrameBuilder field(int fieldNo, byte[] bytes) {
            writeVarint(((long) fieldNo << 3) | 2);
            writeVarint(bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        FrameBuilder header(String key, String value) {
            java.io.ByteArrayOutputStream h = new java.io.ByteArrayOutputStream();
            writeString(h, 1, key);
            writeString(h, 2, value);
            field(5, h.toByteArray());
            return this;
        }

        private void writeString(java.io.ByteArrayOutputStream dest, int fieldNo, String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeVarint(dest, ((long) fieldNo << 3) | 2);
            writeVarint(dest, bytes.length);
            dest.writeBytes(bytes);
        }

        private void writeVarint(long value) {
            writeVarint(out, value);
        }

        private void writeVarint(java.io.ByteArrayOutputStream dest, long value) {
            long v = value;
            while (true) {
                if ((v & ~0x7FL) == 0) {
                    dest.write((int) v);
                    return;
                }
                dest.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
        }

        byte[] build() {
            return out.toByteArray();
        }
    }
}
