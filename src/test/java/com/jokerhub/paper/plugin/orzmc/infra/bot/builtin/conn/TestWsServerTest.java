package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@link TestWsServer} 握手闸门回归（2026-09-10，QQ op9/Discord 4004 CI 偶发失败根因）。
 *
 * <p>不变量：服务端下行必须排在 HTTP 101 之后——连接 accept 后即进入 {@code connections()}，
 * 若调用方不等握手完成就 {@code sendText}，帧字节会先于 101 到达客户端，破坏客户端握手，
 * 表现为「客户端另起新连接，测试旧连接超时」。</p>
 */
class TestWsServerTest {

    @Test
    void sendText_waitsForHandshake_soHandshakeResponsePrecedesFrames() throws Exception {
        try (TestWsServer server = TestWsServer.start();
                Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream out = socket.getOutputStream();
            // 半截握手请求（缺结尾空行）：服务端 handshake() 阻塞在读，握手不会完成
            out.write(("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            TestWsServer.Conn conn = awaitConn(server);
            CountDownLatch sent = new CountDownLatch(1);
            Thread writer = new Thread(
                    () -> {
                        conn.sendText("payload");
                        sent.countDown();
                    },
                    "gate-writer");
            writer.setDaemon(true);
            writer.start();

            // 握手未完成：sendText 必须阻塞，不得写入任何字节
            assertFalse(sent.await(300, TimeUnit.MILLISECONDS), "握手完成前 sendText 不应下发帧");

            // 补完握手请求 → 服务端写 101 并放行下行
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertTrue(sent.await(5, TimeUnit.SECONDS), "握手完成后 sendText 应放行");

            byte[] wire = readHeaderPlusOneByte(socket.getInputStream());
            String head = new String(wire, StandardCharsets.UTF_8);
            assertTrue(head.startsWith("HTTP/1.1 101"), "首字节必须是握手响应: " + head);
            assertEquals(0x81, wire[wire.length - 1] & 0xFF, "101 之后才是 WS 文本帧（0x81）");
            writer.join(1000);
        }
    }

    private static TestWsServer.Conn awaitConn(TestWsServer server) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!server.connections().isEmpty()) {
                return server.connections().get(0);
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("连接未被 accept");
    }

    /** 读到 HTTP 头结束（\r\n\r\n）后再多读一个字节（期望是 WS 帧首字节）。 */
    private static byte[] readHeaderPlusOneByte(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] pattern = {'\r', '\n', '\r', '\n'};
        int matched = 0;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            buf.write(b);
            if ((byte) b == pattern[matched]) {
                matched++;
                if (matched == pattern.length) {
                    int frameByte = in.read();
                    if (frameByte >= 0) {
                        buf.write(frameByte);
                    }
                    break;
                }
            } else {
                matched = (byte) b == pattern[0] ? 1 : 0;
            }
        }
        return buf.toByteArray();
    }
}
