package com.jokerhub.paper.plugin.orzmc.infra.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link HangarClient} 测试：URL 构造 + Hangar API v1 响应解析（JSON 直喂 parseLatest，
 * 不经真实外呼）。latest() 端到端路径用 MockedStatic 桩 AsyncHttp。
 */
class HangarClientTest {

    private HangarClient client;

    @BeforeEach
    void setUp() {
        client = new HangarClient("http://test.local/api");
    }

    @Test
    void defaultConstructor_usesProductionBaseUrl() {
        assertEquals(
                HangarClient.API_BASE + "/versions?limit=1&channel=release", new HangarClient().versionsUrl("release"));
    }

    @Test
    void versionsUrl_buildsLimitOneChannelQuery() {
        assertEquals(
                "http://test.local/api/versions?limit=1&channel=release",
                client.versionsUrl("release"),
                "limit=1 取通道内最新版，channel 原样拼接");
        assertEquals("http://test.local/api/versions?limit=1&channel=beta", client.versionsUrl("beta"));
    }

    @Test
    void versionsUrl_encodesChannel() {
        assertEquals("http://test.local/api/versions?limit=1&channel=release+beta", client.versionsUrl("release beta"));
    }

    @Test
    void parseLatest_fullPayload_readsAllFields() {
        String body = "{\"result\":[{\"name\":\"1.0.24-dev.360\",\"createdAt\":\"2026-08-20T10:00:00Z\","
                + "\"downloads\":{\"PAPER\":{\"fileInfo\":{\"name\":\"OrzMC-1.0.24-dev.360.jar\",\"sha256Hash\":\"abc123\"},"
                + "\"externalUrl\":\"https://cdn.example.com/OrzMC.jar\",\"downloadUrl\":\"fallback\"}}}]}";

        Optional<HangarClient.LatestVersion> result =
                client.parseLatest(body, "http://test.local/api/versions?limit=1&channel=beta");

        assertTrue(result.isPresent());
        HangarClient.LatestVersion v = result.get();
        assertEquals("1.0.24-dev.360", v.version());
        assertEquals(Instant.parse("2026-08-20T10:00:00Z"), v.publishedAt());
        assertEquals("OrzMC-1.0.24-dev.360.jar", v.fileName(), "落盘须保持与平台 fileInfo.name 一致");
        assertEquals("https://cdn.example.com/OrzMC.jar", v.downloadUrl(), "externalUrl（CDN 直链）优先于 downloadUrl");
        assertEquals("abc123", v.sha256());
    }

    @Test
    void parseLatest_missingExternalUrl_fallsBackToDownloadUrl() {
        String body = "{\"result\":[{\"name\":\"1.0.23\",\"createdAt\":\"2026-08-01T00:00:00Z\","
                + "\"downloads\":{\"PAPER\":{\"fileInfo\":{\"sha256Hash\":\"d4f\"},"
                + "\"externalUrl\":null,\"downloadUrl\":\"https://fallback.example.com/x.jar\"}}}]}";

        Optional<HangarClient.LatestVersion> result = client.parseLatest(body, "url");

        assertTrue(result.isPresent());
        assertEquals("1.0.23", result.get().version());
        assertEquals("https://fallback.example.com/x.jar", result.get().downloadUrl());
    }

    @Test
    void parseLatest_missingFileInfo_sha256NullButPresent() {
        String body = "{\"result\":[{\"name\":\"1.0.22\",\"createdAt\":null,"
                + "\"downloads\":{\"PAPER\":{\"downloadUrl\":\"https://cdn/x.jar\"}}}]}";

        Optional<HangarClient.LatestVersion> result = client.parseLatest(body, "url");

        assertTrue(result.isPresent());
        HangarClient.LatestVersion v = result.get();
        assertNull(v.publishedAt(), "createdAt 缺失/非法 → publishedAt 为 null");
        assertNull(v.fileName(), "fileInfo 缺失 → fileName 为 null（落盘兜底默认名）");
        assertNull(v.sha256(), "fileInfo 缺失 → sha256 为 null（调用方在下载前兜底）");
    }

    @Test
    void parseLatest_emptyResult_returnsEmpty() {
        Optional<HangarClient.LatestVersion> result = client.parseLatest("{\"result\":[]}", "url");
        assertTrue(result.isEmpty());
    }

    @Test
    void parseLatest_missingResultKey_returnsEmpty() {
        Optional<HangarClient.LatestVersion> result = client.parseLatest("{\"pagination\":{}}", "url");
        assertTrue(result.isEmpty());
    }

    @Test
    void parseLatest_noPaperDownload_returnsEmpty() {
        String body = "{\"result\":[{\"name\":\"1.0.21\",\"createdAt\":\"2026-08-01T00:00:00Z\","
                + "\"downloads\":{\"VELOCITY\":{}}}]}";
        Optional<HangarClient.LatestVersion> result = client.parseLatest(body, "url");
        assertTrue(result.isEmpty(), "无 PAPER 下载入口的版本不可用，返回空");
    }

    @Test
    void parseLatest_invalidJson_throws() {
        assertThrows(Exception.class, () -> client.parseLatest("not json", "url"));
    }

    @Test
    void latest_hitsVersionsUrlAndParsesResponse() {
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            String body = "{\"result\":[{\"name\":\"1.0.24\",\"createdAt\":\"2026-08-20T10:00:00Z\","
                    + "\"downloads\":{\"PAPER\":{\"fileInfo\":{\"sha256Hash\":\"abc\"},"
                    + "\"externalUrl\":\"https://cdn/x.jar\"}}}]}";
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            when(httpResponse.body()).thenReturn(body);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(httpResponse);
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            Optional<HangarClient.LatestVersion> result =
                    client.latest("release").join();

            assertTrue(result.isPresent());
            assertEquals("1.0.24", result.get().version());
            asyncHttp.verify(() -> AsyncHttp.get(
                    eq("http://test.local/api/versions?limit=1&channel=release"),
                    eq(Map.of("Accept", "application/json")),
                    eq(Duration.ofSeconds(5)),
                    eq(Duration.ofSeconds(10)),
                    eq(2)));
        }
    }
}
