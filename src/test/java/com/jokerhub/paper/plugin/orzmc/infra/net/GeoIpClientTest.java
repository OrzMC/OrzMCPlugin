package com.jokerhub.paper.plugin.orzmc.infra.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * GeoIpClient 多源兜底测试（2026-11，#538）。
 *
 * <p>回归事故：只打 {@code get.geojs.io} 一家，该源超时时每次登录都走「解析失败」分支
 * （默认 fail-close → 正常玩家被踢；线上实测 14:24 被拒、14:25 重试才进来）。
 * 现在按链依次尝试，单源故障不再直接导致登录失败。</p>
 */
class GeoIpClientTest {

    private GeoIpClient client;

    @BeforeEach
    void setUp() {
        client = new GeoIpClient();
    }

    @Test
    void constructor_createsInstance() {
        assertNotNull(client);
    }

    @Test
    void lookup_returnsCompletableFuture() {
        CompletableFuture<GeoIpClient.GeoIpResult> future = client.lookup("8.8.8.8");
        assertNotNull(future);
    }

    @Test
    void defaultChain_prefersCountryIs_thenGeojs() {
        // 默认链顺序 = 免费条款最宽松者优先（country.is：无配额 + 允许商业使用 + 可自托管），
        // ipwho.is 免费额度有限，不进默认链
        assertEquals(
                List.of(GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.GEOJS), GeoIpClient.DEFAULT_PROVIDERS);
    }

    @Test
    void lookup_primarySuccessParsesCountryCodeAndNormalizesCase() throws Exception {
        // 先构造响应再进入静态桩（在 when(...) 里再 mock 会触发 UnfinishedStubbing）
        CompletableFuture<HttpResponse<String>> primary = respond(200, "{\"ip\":\"1.2.3.4\",\"country\":\"cn\"}");
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://api.country.is/1.2.3.4"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(primary);

            GeoIpClient.GeoIpResult result = client.lookup("1.2.3.4").join();

            assertEquals("CN", result.countryCode(), "国家码应归一化为大写");
            assertEquals("countryis", result.provider());
            // 单源超时默认 1.2s（决策窗 3s 内要能切下一家）
            asyncHttp.verify(() -> AsyncHttp.get(
                    startsWith("https://api.country.is/1.2.3.4"),
                    anyMap(),
                    eq(Duration.ofMillis(1200)),
                    eq(Duration.ofMillis(1200)),
                    eq(0)));
        }
    }

    @Test
    void lookup_primaryTimeout_fallsBackToNextProvider() throws Exception {
        CompletableFuture<HttpResponse<String>> timeout = fail(new java.net.http.HttpTimeoutException("timed out"));
        CompletableFuture<HttpResponse<String>> secondary = respond(200, "{\"ip\":\"1.2.3.4\",\"country\":\"JP\"}");
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://get.geojs.io/"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(timeout);
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://api.country.is/1.2.3.4"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(secondary);

            GeoIpClient.GeoIpResult result = lookupWith(
                            List.of(GeoIpClient.Provider.GEOJS, GeoIpClient.Provider.COUNTRY_IS))
                    .join();

            assertEquals("JP", result.countryCode());
            assertEquals("countryis", result.provider(), "主源超时应由第二家兜底");
        }
    }

    @Test
    void lookup_primaryHttpError_fallsBackToThird() throws Exception {
        CompletableFuture<HttpResponse<String>> busy = respond(503, "upstream busy");
        CompletableFuture<HttpResponse<String>> noCountry = respond(200, "{\"ip\":\"1.2.3.4\"}");
        CompletableFuture<HttpResponse<String>> third =
                respond(200, "{\"ip\":\"1.2.3.4\",\"country_code\":\"SG\",\"success\":true}");
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://get.geojs.io/"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(busy);
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://api.country.is/"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(noCountry); // 无国家码 → 视为本次失败
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://ipwho.is/1.2.3.4"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(third);

            GeoIpClient.GeoIpResult result = lookupWith(List.of(
                            GeoIpClient.Provider.GEOJS, GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.IPWHOIS))
                    .join();

            assertEquals("SG", result.countryCode());
            assertEquals("ipwhois", result.provider());
        }
    }

    @Test
    void lookup_ipwhoisSuccessFalseIsTreatedAsFailedAttempt() throws Exception {
        // ipwho.is 用 HTTP 200 + success:false 表达失败（保留段等），不能当成「拿到响应」
        CompletableFuture<HttpResponse<String>> reserved = respond(200, "{\"success\":false,\"message\":\"reserved\"}");
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(reserved);

            GeoIpClient.LookupException ex = assertLookupFailure(
                    List.of(GeoIpClient.Provider.GEOJS, GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.IPWHOIS));
            assertEquals(3, ex.attempts().size(), "三家都应尝试过: " + ex.attempts());
        }
    }

    @Test
    void lookup_allProvidersFail_throwsWithPerProviderReasons() throws Exception {
        CompletableFuture<HttpResponse<String>> refused = fail(new java.net.ConnectException("connection refused"));
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(refused);

            GeoIpClient.LookupException ex = assertLookupFailure(
                    List.of(GeoIpClient.Provider.GEOJS, GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.IPWHOIS));

            assertEquals(3, ex.attempts().size());
            assertTrue(ex.attempts().get(0).startsWith("geojs:"), ex.attempts().toString());
            assertTrue(
                    ex.attempts().get(1).startsWith("countryis:"), ex.attempts().toString());
            assertTrue(
                    ex.attempts().get(2).startsWith("ipwhois:"), ex.attempts().toString());
            assertTrue(ex.getMessage().contains("1.2.3.4"), ex.getMessage());
        }
    }

    @Test
    void lookup_budgetExhausted_stopsTryingInsteadOfOverrunningDecisionWindow() throws Exception {
        // 总预算不足最短尝试预算 → 立刻失败（不能把 3s 决策窗撑爆，否则玩家照样被踢）
        java.util.List<String> attempts = new java.util.ArrayList<>();
        CompletableFuture<GeoIpClient.GeoIpResult> f = client.lookup(
                "1.2.3.4", List.of(GeoIpClient.Provider.GEOJS), Duration.ofMillis(1200), Duration.ofMillis(1));
        assertTrue(f.isCompletedExceptionally(), "预算耗尽应立即失败而不是挂起");
        f.handle((r, ex) -> {
                    attempts.add(String.valueOf(ex.getMessage()));
                    return null;
                })
                .join();
        assertTrue(attempts.get(0).contains("budget"), attempts.toString());
    }

    @Test
    void lookup_usesConfiguredChainOnly() throws Exception {
        CompletableFuture<HttpResponse<String>> third = respond(200, "{\"country_code\":\"AU\",\"success\":true}");
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.get(
                            startsWith("https://ipwho.is/9.9.9.9"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()))
                    .thenReturn(third);

            GeoIpClient.GeoIpResult result = client.lookup(
                            "9.9.9.9",
                            List.of(GeoIpClient.Provider.IPWHOIS),
                            Duration.ofMillis(1200),
                            Duration.ofMillis(2500))
                    .join();

            assertEquals("AU", result.countryCode());
            asyncHttp.verify(
                    () -> AsyncHttp.get(
                            startsWith("https://get.geojs.io/"),
                            anyMap(),
                            any(Duration.class),
                            any(Duration.class),
                            anyInt()),
                    never());
        }
    }

    @Test
    void parseProviders_ignoresUnknownAndKeepsOrder() {
        assertEquals(
                List.of(GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.GEOJS),
                GeoIpClient.parseProviders(List.of("COUNTRYIS", "nope", "geojs")));
        assertEquals(List.of(), GeoIpClient.parseProviders(List.of()));
        assertNull(GeoIpClient.Provider.parse("ipapi"));
        assertEquals(GeoIpClient.Provider.GEOJS, GeoIpClient.Provider.parse(" Geojs "));
    }

    /** join() 会把失败包进 CompletionException，这里统一剥出 {@link GeoIpClient.LookupException} 断言。 */
    private GeoIpClient.LookupException assertLookupFailure(List<GeoIpClient.Provider> chain) {
        java.util.concurrent.CompletionException ce = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> lookupWith(chain).join());
        assertInstanceOf(GeoIpClient.LookupException.class, ce.getCause(), "根因应为 LookupException: " + ce);
        return (GeoIpClient.LookupException) ce.getCause();
    }

    /** 用显式 provider 链查询（默认链顺序另有专门用例，测试不依赖它）。 */
    private CompletableFuture<GeoIpClient.GeoIpResult> lookupWith(List<GeoIpClient.Provider> chain) {
        return client.lookup("1.2.3.4", chain, Duration.ofMillis(1200), Duration.ofMillis(2500));
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<String>> respond(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return CompletableFuture.completedFuture(response);
    }

    private static CompletableFuture<HttpResponse<String>> fail(Throwable ex) {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }
}
