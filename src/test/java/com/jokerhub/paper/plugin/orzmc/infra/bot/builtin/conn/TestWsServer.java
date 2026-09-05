package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用最小 RFC6455 WebSocket 服务端（S3 起各 adapter 测试复用：本地 WS 服务端 accept 后收发帧/断开验证重连）。
 *
 * <p>仅支持单文本帧（客户端小帧即单帧发送）、close/ping 控制帧；服务端→客户端发送不掩码。
 * 每连接一个读线程；可模拟：网络断开（关 socket，客户端见 1006）、服务端主动 close 帧（指定 code，如 QQ 鉴权 4004）、
 * 回声模式（收到文本帧回 "ack"，保持客户端活跃防止静默看门狗误判）。
 * 服务端→客户端帧用 {@link Conn#sendText(String)} 直接下发（如 QQ hello op10 / 事件 op0）。</p>
 */
public final class TestWsServer implements AutoCloseable {

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final CopyOnWriteArrayList<Conn> connections = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final boolean echo;

    private TestWsServer(ServerSocket serverSocket, boolean echo) {
        this.serverSocket = serverSocket;
        this.echo = echo;
        this.acceptThread = new Thread(this::acceptLoop, "test-ws-server-accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    public static TestWsServer start() throws IOException {
        return start(false);
    }

    public static TestWsServer start(boolean echo) throws IOException {
        return new TestWsServer(new ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1")), echo);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public List<Conn> connections() {
        return connections;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                Conn conn = new Conn(socket);
                connections.add(conn);
                conn.start();
            } catch (IOException e) {
                if (running.get()) {
                    // 忽略 accept 竞争关闭
                }
                return;
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 关闭中
        }
        connections.forEach(Conn::closeSocket);
    }

    /** 单连接：读线程负责握手 + 读帧；测试线程可发送文本 / 主动关闭。 */
    public final class Conn {
        private final Socket socket;
        private final List<String> received = new CopyOnWriteArrayList<>();
        private final List<byte[]> receivedBinary = new CopyOnWriteArrayList<>();
        private final Thread reader;

        private Conn(Socket socket) {
            this.socket = socket;
            this.reader = new Thread(this::readLoop, "test-ws-server-conn");
            this.reader.setDaemon(true);
        }

        private void start() {
            reader.start();
        }

        public List<String> receivedText() {
            return received;
        }

        /** 客户端发来的二进制帧负载（去掩码后原始字节）。 */
        public List<byte[]> receivedBinary() {
            return receivedBinary;
        }

        /** 服务端→客户端发文本帧（不掩码）。 */
        public void sendText(String text) {
            writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        /** 服务端→客户端发二进制帧（不掩码）。 */
        public void sendBinary(byte[] payload) {
            writeFrame(0x2, payload);
        }

        /** 服务端主动 close 帧（指定 code），随后关闭 TCP。 */
        public void sendClose(int code) {
            try {
                byte[] payload = new byte[] {(byte) (code >> 8), (byte) code};
                writeFrame(0x8, payload);
            } finally {
                closeSocket();
            }
        }

        /** 模拟网络断开：直接关闭 TCP（客户端将收到 1006 abnormal closure）。 */
        public void closeSocket() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 已关闭
            }
        }

        private void writeFrame(int opcode, byte[] payload) {
            try {
                OutputStream out = socket.getOutputStream();
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x80 | opcode);
                int len = payload.length;
                if (len < 126) {
                    frame.write(len);
                } else if (len < 65536) {
                    frame.write(126);
                    frame.write((len >> 8) & 0xFF);
                    frame.write(len & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int) ((len >> (8 * i)) & 0xFF));
                    }
                }
                frame.write(payload);
                out.write(frame.toByteArray());
                out.flush();
            } catch (IOException ignored) {
                // 对端已关闭
            }
        }

        private void readLoop() {
            try {
                InputStream in = socket.getInputStream();
                if (!handshake(in, socket.getOutputStream())) {
                    return;
                }
                while (true) {
                    int b0 = in.read();
                    if (b0 < 0) {
                        return;
                    }
                    int opcode = b0 & 0x0F;
                    int b1 = in.read();
                    if (b1 < 0) {
                        return;
                    }
                    boolean masked = (b1 & 0x80) != 0;
                    long len = b1 & 0x7F;
                    if (len == 126) {
                        len = (in.read() << 8) | in.read();
                    } else if (len == 127) {
                        long big = 0;
                        for (int i = 0; i < 8; i++) {
                            big = (big << 8) | in.read();
                        }
                        len = big;
                    }
                    if (len < 0 || len > 8 * 1024 * 1024) {
                        return;
                    }
                    byte[] mask = null;
                    if (masked) {
                        mask = in.readNBytes(4);
                        if (mask.length < 4) {
                            return;
                        }
                    }
                    byte[] payload = readFully(in, (int) len);
                    if (payload == null) {
                        return;
                    }
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= mask[i % 4];
                        }
                    }
                    switch (opcode) {
                        case 0x1 -> {
                            String text = new String(payload, StandardCharsets.UTF_8);
                            received.add(text);
                            if (echo) {
                                sendText("ack");
                            }
                        }
                        case 0x2 -> {
                            byte[] copy = new byte[payload.length];
                            System.arraycopy(payload, 0, copy, 0, payload.length);
                            receivedBinary.add(copy);
                        }
                        case 0x8 -> {
                            if (payload.length >= 2) {
                                writeFrame(0x8, payload);
                            }
                            return;
                        }
                        case 0x9 -> writeFrame(0xA, payload); // ping → pong
                        default -> {
                            // 0x0 续帧 / 0xA pong / 其它：忽略
                        }
                    }
                }
            } catch (IOException ignored) {
                // 连接关闭
            } finally {
                closeSocket();
            }
        }

        private boolean handshake(InputStream in, OutputStream out) throws IOException {
            StringBuilder header = new StringBuilder();
            int c;
            while (header.length() < 16384) {
                c = in.read();
                if (c < 0) {
                    return false;
                }
                header.append((char) c);
                if (header.toString().endsWith("\r\n\r\n")) {
                    break;
                }
            }
            String raw = header.toString();
            String key = null;
            for (String line : raw.split("\r\n")) {
                if (line.regionMatches(true, 0, "sec-websocket-key:", 0, "sec-websocket-key:".length())) {
                    key = line.substring("sec-websocket-key:".length()).trim();
                    break;
                }
            }
            if (key == null) {
                return false;
            }
            String accept;
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                accept = Base64.getEncoder()
                        .encodeToString(sha1.digest((key + WS_GUID).getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                return false;
            }
            out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: "
                            + accept
                            + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        }

        private static byte[] readFully(InputStream in, int length) throws IOException {
            byte[] buf = in.readNBytes(length);
            return buf.length == length ? buf : null;
        }
    }
}
