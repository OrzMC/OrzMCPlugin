package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GeoIpAccessService 测试：内网 IP 直接放行（不触发 GeoIP 查询）。
 *
 * <p>回归场景（2026-08-06）：MCSM 配置 allow_country_code=[CN,JP,TW] 时，
 * 内网用户（192.168.x/10.x）被 GeoIP 查询判定为未知国家码而拦截。
 * 修复：RFC1918 私有段/环回/CGNAT 直接放行，不查 GeoIP。</p>
 */
class GeoIpAccessServiceTest {

    private GeoIpClient client;
    private TypedConfigProvider configs;
    private GeoIpAccessService service;

    @BeforeEach
    void setUp() {
        client = mock(GeoIpClient.class);
        configs = mock(TypedConfigProvider.class);
        service = new GeoIpAccessService(client, configs);
    }

    // ---- 多源兜底 + 失败短缓存（2026-11，#538）----

    /** 桩：服务现在走 4 参重载（provider 链 + 单源超时 + 总预算）。 */
    private void stubLookup(String ip, CompletableFuture<GeoIpClient.GeoIpResult> future) {
        when(client.lookup(eq(ip), anyList(), any(java.time.Duration.class), any(java.time.Duration.class)))
                .thenReturn(future);
    }

    private void verifyLookup(String ip, int times) {
        verify(client, times(times))
                .lookup(eq(ip), anyList(), any(java.time.Duration.class), any(java.time.Duration.class));
    }

    @Test
    void decide_passesConfiguredProviderChainAndTimeoutsToClient() {
        when(configs.ipWhitelist())
                .thenReturn(new IpWhitelist(List.of("CN"), false, List.of("countryis", "ipwhois"), 800L, 30));
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}", "countryis")));

        assertTrue(service.decide("1.2.3.4").join().allowed());

        var chain = org.mockito.ArgumentCaptor.forClass(List.class);
        var perProvider = org.mockito.ArgumentCaptor.forClass(java.time.Duration.class);
        var budget = org.mockito.ArgumentCaptor.forClass(java.time.Duration.class);
        verify(client).lookup(eq("1.2.3.4"), chain.capture(), perProvider.capture(), budget.capture());
        assertEquals(
                List.of(GeoIpClient.Provider.COUNTRY_IS, GeoIpClient.Provider.IPWHOIS),
                chain.getValue(),
                "应把配置里的 provider 顺序原样传给客户端");
        assertEquals(800L, perProvider.getValue().toMillis(), "单源超时应取配置值");
        assertTrue(budget.getValue().toMillis() < GeoIpAccessService.DECISION_TIMEOUT_MS, "查询总预算必须小于登录决策窗，否则结果来不及用");
    }

    @Test
    void decide_failureCacheDisabled_requeriesEveryLogin() {
        // failure_cache_seconds=0 → 回到「每次都问」的旧行为（排查上游时用）
        when(configs.ipWhitelist())
                .thenReturn(new IpWhitelist(List.of("CN"), false, IpWhitelist.DEFAULT_PROVIDERS, 1200L, 0));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        stubLookup("1.2.3.4", failed);

        service.decide("1.2.3.4").join();
        service.decide("1.2.3.4").join();

        verifyLookup("1.2.3.4", 2);
    }

    @Test
    void decide_failureNotCachedAsSuccess_afterWindowRequeried() throws Exception {
        // 失败只进短缓存：过期后必须重新查询（不能把临时故障固定成 12h 误拦）
        when(configs.ipWhitelist())
                .thenReturn(new IpWhitelist(List.of("CN"), false, IpWhitelist.DEFAULT_PROVIDERS, 1200L, 1));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        stubLookup("1.2.3.4", failed);

        service.decide("1.2.3.4").join();
        org.junit.jupiter.api.Assertions.assertFalse(
                service.decide("1.2.3.4").join().allowed(), "窗口内沿用失败结果");
        verifyLookup("1.2.3.4", 1);

        Thread.sleep(1200);
        service.decide("1.2.3.4").join();
        verifyLookup("1.2.3.4", 2);
    }

    // ---- 内网 IP 直接放行（不查 GeoIP）----

    @Test
    void decide_privateIp192_168_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("192.168.1.100").join();

        assertTrue(d.allowed(), "内网 192.168.x 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_privateIp10_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("10.0.0.5").join();

        assertTrue(d.allowed(), "内网 10.x 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_privateIp172_16_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("172.16.0.1").join();

        assertTrue(d.allowed(), "内网 172.16/12 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_loopback_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("127.0.0.1").join();

        assertTrue(d.allowed(), "环回 127.x 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_ipv4MappedPrivateIp_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("::ffff:192.168.1.1").join();

        assertTrue(d.allowed(), "IPv4-mapped 内网 ::ffff:192.168.x 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_cgnat100_64_skipsGeoIpLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));

        GeoIpAccessService.Decision d = service.decide("100.64.0.10").join();

        assertTrue(d.allowed(), "运营商大内网 100.64/10 应直接放行");
        verify(client, never()).lookup(anyString());
    }

    // ---- 公网 IP 继续走 GeoIP 检查 ----

    @Test
    void decide_publicIp_stillQueriesGeoIp() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("CN", "{}");
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed());
        verifyLookup("1.2.3.4", 1);
    }

    @Test
    void decide_publicIpNotInAllowList_blocked() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("US", "{}");
        stubLookup("8.8.8.8", CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("8.8.8.8").join();

        assertFalse(d.allowed(), "公网非白名单国家应拦截");
        assertEquals("US", d.countryCode());
    }

    @Test
    void decide_emptyAllowList_alwaysAllowsWithoutLookup() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of()));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed());
        verify(client, never()).lookup(anyString());
    }

    @Test
    void decide_geoIpQueryFailure_defaultFailClose_deniesAndMarksLookupFailed() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        stubLookup("1.2.3.4", failed);

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertFalse(d.allowed(), "默认 fail-close：查询异常应拒绝进入（安全优先）");
        assertTrue(d.lookupFailed(), "查询异常应标记 lookupFailed 以便私信告警");
    }

    @Test
    void decide_geoIpQueryFailure_failOpen_allowsAndMarksLookupFailed() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        stubLookup("1.2.3.4", failed);

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed(), "fail_open=true：查询异常应放行（可用性优先）");
        assertTrue(d.lookupFailed(), "查询异常应标记 lookupFailed 以便私信告警");
    }

    // ---- 结果缓存（TTL）----

    @Test
    void decide_successfulLookup_cachesResult() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("CN", "{}");
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d1 = service.decide("1.2.3.4").join();
        GeoIpAccessService.Decision d2 = service.decide("1.2.3.4").join();

        assertTrue(d1.allowed());
        assertTrue(d2.allowed());
        verifyLookup("1.2.3.4", 1);
    }

    @Test
    void decide_cacheExpired_triggersNewLookup() throws Exception {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("CN", "{}");
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(res));
        // TTL 5ms：第一次成功后缓存立即过期，第二次应重新查询
        GeoIpAccessService shortTtlService = new GeoIpAccessService(client, configs, 5L);

        shortTtlService.decide("1.2.3.4").join();
        Thread.sleep(50);
        shortTtlService.decide("1.2.3.4").join();

        verifyLookup("1.2.3.4", 2);
    }

    @Test
    void decide_queryFailure_cachedBriefly_noRequeryWithinWindow() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        stubLookup("1.2.3.4", failed);

        GeoIpAccessService.Decision d1 = service.decide("1.2.3.4").join();
        GeoIpAccessService.Decision d2 = service.decide("1.2.3.4").join();

        assertFalse(d1.allowed());
        assertFalse(d2.allowed());
        // 失败短缓存（默认 30s）：抖动期同一 IP 不再反复打第三方（否则会把配额打光）
        verifyLookup("1.2.3.4", 1);
    }

    @Test
    void decide_emptyCountryCode_defaultFailClose_deniesAndAlerts() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("", "{}");
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertFalse(d.allowed(), "默认 fail-close：空国家码（上游无法定位）应拒绝进入");
        assertTrue(d.lookupFailed(), "空国家码应标记 lookupFailed 以便告警管理员");
        // 短缓存：窗口内不再重复查询（成功后才会 12h 缓存，失败绝不长缓存）
        service.decide("1.2.3.4").join();
        verifyLookup("1.2.3.4", 1);
    }

    @Test
    void decide_emptyCountryCode_failOpen_allowsAndAlerts() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("", "{}");
        stubLookup("1.2.3.4", CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed(), "fail_open=true：空国家码应放行，不误拦合法玩家");
        assertTrue(d.lookupFailed(), "空国家码应标记 lookupFailed 以便告警管理员");
        // 短缓存：窗口内不再重复查询（成功后才会 12h 缓存，失败绝不长缓存）
        service.decide("1.2.3.4").join();
        verifyLookup("1.2.3.4", 1);
    }

    @Test
    void decide_overCapacity_evictsExpiredEntries() throws Exception {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        // 容量 1 + TTL 5ms：写入第二条时 size=2 > 1，触发 evictExpired 清理已过期条目
        GeoIpAccessService tinyCache = new GeoIpAccessService(client, configs, 5L, 1);
        stubLookup("1.1.1.1", CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}")));
        stubLookup("2.2.2.2", CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}")));

        tinyCache.decide("1.1.1.1").join(); // 写入 A，5ms 后过期
        Thread.sleep(50);
        tinyCache.decide("2.2.2.2").join(); // 写入 B 触发清理，过期的 A 被移除

        // A 已被清理：再次查询应重新走上游
        tinyCache.decide("1.1.1.1").join();
        verify(client, times(2))
                .lookup(eq("1.1.1.1"), anyList(), any(java.time.Duration.class), any(java.time.Duration.class));
    }
}
