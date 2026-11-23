import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 长连接「入站断流」参考探针（JDK 版，issue #616 实验 C 用，零依赖）。
 *
 * <p>为什么有 Python / Node 版还要一个 JDK 版：Minecraft 实例容器里通常**只有 JRE**（`eclipse-temurin:*`），
 * 而实验 C 的关键是「在插件同一个网络命名空间里跑一个**别的**客户端」。JDK 自带 {@code java.net.http.WebSocket}
 * 支持发 ping 控制帧，行为比 Node 的全局 WebSocket（不能发 ping）更接近真实现场。</p>
 *
 * <p>用法（在宿主编译一次，JRE 容器里跑）：</p>
 * <pre>{@code
 * javac -d /tmp/probe scripts/dev/ImLonglinkProbe.java
 * cp -r /tmp/probe ./probe-classes
 * docker run --rm --network orzmc_default -v "$PWD/probe-classes:/p:ro" eclipse-temurin:25-jre \
 *     java -cp /p ImLonglinkProbe wss://api.sgroup.qq.com/websocket 60 10
 * }</pre>
 *
 * <p>判读：零入站 = 该出网路径对长连接不友好（与插件无关）；有入站而插件静默 = 回插件侧排查。</p>
 */
public final class ImLonglinkProbe {

    private static final AtomicLong frames = new AtomicLong();
    private static volatile long lastAt = 0L;
    private static volatile String closeReason = null;
    private static final long startedAt = System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: java ImLonglinkProbe <wss://…> [观察秒数=60] [ping间隔秒=10]");
            System.exit(2);
        }
        String url = args[0];
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        int pingEverySec = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        log("目标 " + url);
        CountDownLatch closed = new CountDownLatch(1);
        WebSocket ws;
        try {
            ws = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(url), new Listener(closed))
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            log("!! 建连失败: " + e);
            System.exit(3);
            return;
        }
        log("WS 已连接（" + (System.currentTimeMillis() - startedAt) + "ms）");

        long deadline = System.currentTimeMillis() + seconds * 1000L;
        long nextPing = System.currentTimeMillis() + pingEverySec * 1000L;
        long nextTick = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline && closeReason == null) {
            long now = System.currentTimeMillis();
            if (pingEverySec > 0 && now >= nextPing) {
                ws.sendPing(ByteBuffer.wrap("orzmc-probe".getBytes()));
                log("-> ping");
                nextPing = now + pingEverySec * 1000L;
            }
            if (now >= nextTick) {
                long since = lastAt == 0 ? -1 : (now - lastAt) / 1000;
                log(".. 入站累计 " + frames.get() + " 帧，距上次入站 " + (since < 0 ? "-" : since + "s"));
                nextTick = now + 1000L;
            }
            Thread.sleep(100);
        }

        log("---- 汇总 ----");
        log("观察 " + ((System.currentTimeMillis() - startedAt) / 1000) + "s：入站帧 " + frames.get() + " 个"
                + (closeReason == null ? "" : "（连接已关闭: " + closeReason + "）"));
        if (frames.get() == 0) {
            log("结论：同一路径上参考客户端也零入站 → 该出网路径对长连接不友好（与插件无关）。");
        } else {
            log("结论：该路径入站可用；若插件仍静默，回到插件侧排查（帧构造/读线程）。");
        }
        try {
            ws.abort();
        } catch (RuntimeException ignored) {
            // 已关闭
        }
        System.exit(0);
    }

    private static void log(String message) {
        System.out.println("[" + LocalTime.now().withNano(0) + "] " + message);
    }

    private static final class Listener implements WebSocket.Listener {
        private final CountDownLatch closed;

        Listener(CountDownLatch closed) {
            this.closed = closed;
        }

        private void mark(String kind, CharSequence payload) {
            long n = frames.incrementAndGet();
            lastAt = System.currentTimeMillis();
            String text = payload == null ? "" : payload.toString();
            if (text.length() > 120) {
                text = text.substring(0, 120) + "...";
            }
            log("<- 入站帧 #" + n + " " + kind + ": " + text);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            mark("text", data);
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            mark("binary", "<" + data.remaining() + "B>");
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            mark("pong", "<" + message.remaining() + "B>");
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            mark("ping", "<" + message.remaining() + "B>");
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeReason = "code=" + statusCode + " reason=" + reason;
            log("!! 连接关闭: " + closeReason);
            closed.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeReason = "error: " + error;
            log("!! WS 错误: " + error);
            closed.countDown();
        }
    }
}
