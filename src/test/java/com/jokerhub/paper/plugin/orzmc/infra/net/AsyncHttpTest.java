package com.jokerhub.paper.plugin.orzmc.infra.net;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsyncHttpTest {
    private HttpServer server;
    private URI baseUri;
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesOnConnectionErrorThenSucceeds() throws Exception {
        server.createContext("/ping", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        new Thread(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    server.start();
                })
                .start();
        String url = baseUri.resolve("/ping").toString();

        CompletableFuture<HttpResponse<String>> fut =
                AsyncHttp.get(url, Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), 2);
        HttpResponse<String> resp = fut.join();
        assertEquals(200, resp.statusCode());
        assertEquals("ok", resp.body());

        assertTrue(requestCount.get() >= 1);
    }

    @Test
    void setsHeadersAndContentTypeOnPostJson() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> bodyText = new AtomicReference<>();
        server.createContext("/post", exchange -> {
            try {
                capture(exchange, authHeader, contentType, userAgent, bodyText);
            } catch (Exception ignored) {
            }
            byte[] body = "posted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String url = baseUri.resolve("/post").toString();

        CompletableFuture<HttpResponse<String>> fut = AsyncHttp.postJson(
                url,
                "{\"a\":1}",
                Map.of("Authorization", "Bearer token123"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1);
        HttpResponse<String> resp = fut.join();
        assertEquals(200, resp.statusCode());
        assertEquals("posted", resp.body());

        assertEquals("application/json", contentType.get());
        assertEquals("OrzMC-EasyBot/1", userAgent.get());
        assertEquals("Bearer token123", authHeader.get());
        assertEquals("{\"a\":1}", bodyText.get());
    }

    @Test
    void zeroRetriesMakesSingleAttemptOnServerError() {
        server.createContext("/fail", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        HttpResponse<String> response = AsyncHttp.get(
                        baseUri.resolve("/fail").toString(), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), 0)
                .join();

        assertEquals(503, response.statusCode());
        assertEquals(1, requestCount.get());
    }

    @Test
    void retriesRetryableServerError() {
        server.createContext("/eventual", exchange -> {
            int attempt = requestCount.incrementAndGet();
            byte[] body = (attempt == 1 ? "retry" : "ok").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(attempt == 1 ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HttpResponse<String> response = AsyncHttp.get(
                        baseUri.resolve("/eventual").toString(),
                        Map.of(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1)
                .join();

        assertEquals(200, response.statusCode());
        assertEquals(2, requestCount.get());
    }

    @Test
    void retriesRateLimitUsingRetryAfterHeader() {
        server.createContext("/rate-limit", exchange -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
            } else {
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();

        HttpResponse<String> response = AsyncHttp.get(
                        baseUri.resolve("/rate-limit").toString(),
                        Map.of(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        1)
                .join();

        assertEquals(200, response.statusCode());
        assertEquals(2, requestCount.get());
    }

    @Test
    void shutdown_clearsClientCacheAndAllowsReuse() throws Exception {
        server.createContext("/cache", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        // 首次请求填充客户端缓存（按 connectTimeout 分桶）
        AsyncHttp.get(baseUri.resolve("/cache").toString(), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), 0)
                .join();
        assertTrue(AsyncHttp.clientCount() > 0, "请求后应缓存 HttpClient");

        // shutdown 关闭所有客户端并清空缓存（回收线程池，防泄漏）
        AsyncHttp.shutdown();
        assertEquals(0, AsyncHttp.clientCount(), "shutdown 后缓存应清空");

        // 关闭后再次请求仍可重建客户端（幂等）
        AsyncHttp.get(baseUri.resolve("/cache").toString(), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), 0)
                .join();
        assertTrue(AsyncHttp.clientCount() > 0, "shutdown 后再次请求应重建客户端");
    }

    private void capture(
            HttpExchange exchange,
            AtomicReference<String> authHeader,
            AtomicReference<String> contentType,
            AtomicReference<String> userAgent,
            AtomicReference<String> bodyText)
            throws Exception {
        authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        bodyText.set(out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void postJson_withProxy_routesRequestThroughProxy() throws Exception {
        // 黑洞代理：只捕获请求行（absolute-form），证明 HTTP 请求确实经代理而非直连
        ServerSocket proxySocket = new ServerSocket(0);
        CountDownLatch seen = new CountDownLatch(1);
        AtomicReference<String> requestLine = new AtomicReference<>();
        Thread acceptor = new Thread(() -> {
            try (Socket socket = proxySocket.accept()) {
                socket.setSoTimeout(3000);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                requestLine.set(reader.readLine());
                seen.countDown();
                socket.getOutputStream()
                        .write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"
                                .getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            } catch (Exception ignored) {
                // 测试路径：忽略读取异常即可
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();

        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxySocket.getLocalPort()));
            CompletableFuture<HttpResponse<String>> future = AsyncHttp.postJson(
                    "http://127.0.0.1:9/proxy-target",
                    "{}",
                    Map.of(),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2),
                    0,
                    proxy);

            assertTrue(seen.await(5, TimeUnit.SECONDS), "请求未到达代理端口");
            String line = requestLine.get();
            assertNotNull(line, "代理未读到请求行");
            assertTrue(line.contains("/proxy-target"), "代理应收到含目标的请求行: " + line);
            future.get(3, TimeUnit.SECONDS); // 502 也属正常完成路径（不抛异常）
        } finally {
            proxySocket.close();
        }
    }
}
