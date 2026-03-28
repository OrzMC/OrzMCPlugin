package com.jokerhub.paper.plugin.orzmc.infra.net;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        AtomicReference<String> bodyText = new AtomicReference<>();
        server.createContext("/post", exchange -> {
            try {
                capture(exchange, authHeader, contentType, bodyText);
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
        assertEquals("Bearer token123", authHeader.get());
        assertEquals("{\"a\":1}", bodyText.get());
    }

    private void capture(
            HttpExchange exchange,
            AtomicReference<String> authHeader,
            AtomicReference<String> contentType,
            AtomicReference<String> bodyText)
            throws Exception {
        authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        bodyText.set(out.toString(StandardCharsets.UTF_8));
    }
}
