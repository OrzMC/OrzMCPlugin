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
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed());
        verify(client).lookup("1.2.3.4");
    }

    @Test
    void decide_publicIpNotInAllowList_blocked() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("US", "{}");
        when(client.lookup("8.8.8.8")).thenReturn(CompletableFuture.completedFuture(res));

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
        when(client.lookup("1.2.3.4")).thenReturn(failed);

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertFalse(d.allowed(), "默认 fail-close：查询异常应拒绝进入（安全优先）");
        assertTrue(d.lookupFailed(), "查询异常应标记 lookupFailed 以便私信告警");
    }

    @Test
    void decide_geoIpQueryFailure_failOpen_allowsAndMarksLookupFailed() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        when(client.lookup("1.2.3.4")).thenReturn(failed);

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed(), "fail_open=true：查询异常应放行（可用性优先）");
        assertTrue(d.lookupFailed(), "查询异常应标记 lookupFailed 以便私信告警");
    }

    // ---- 结果缓存（TTL）----

    @Test
    void decide_successfulLookup_cachesResult() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("CN", "{}");
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d1 = service.decide("1.2.3.4").join();
        GeoIpAccessService.Decision d2 = service.decide("1.2.3.4").join();

        assertTrue(d1.allowed());
        assertTrue(d2.allowed());
        verify(client, times(1)).lookup("1.2.3.4");
    }

    @Test
    void decide_cacheExpired_triggersNewLookup() throws Exception {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("CN", "{}");
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.completedFuture(res));
        // TTL 5ms：第一次成功后缓存立即过期，第二次应重新查询
        GeoIpAccessService shortTtlService = new GeoIpAccessService(client, configs, 5L);

        shortTtlService.decide("1.2.3.4").join();
        Thread.sleep(50);
        shortTtlService.decide("1.2.3.4").join();

        verify(client, times(2)).lookup("1.2.3.4");
    }

    @Test
    void decide_queryFailure_notCached() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        when(client.lookup("1.2.3.4")).thenReturn(failed);

        GeoIpAccessService.Decision d1 = service.decide("1.2.3.4").join();
        GeoIpAccessService.Decision d2 = service.decide("1.2.3.4").join();

        assertFalse(d1.allowed());
        assertFalse(d2.allowed());
        verify(client, times(2)).lookup("1.2.3.4");
    }

    @Test
    void decide_emptyCountryCode_defaultFailClose_deniesAndAlertsWithoutCaching() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("", "{}");
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertFalse(d.allowed(), "默认 fail-close：空国家码（上游无法定位）应拒绝进入");
        assertTrue(d.lookupFailed(), "空国家码应标记 lookupFailed 以便告警管理员");
        // 不缓存：再次查询仍走上游
        service.decide("1.2.3.4").join();
        verify(client, times(2)).lookup("1.2.3.4");
    }

    @Test
    void decide_emptyCountryCode_failOpen_allowsAndAlertsWithoutCaching() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
        GeoIpClient.GeoIpResult res = new GeoIpClient.GeoIpResult("", "{}");
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.completedFuture(res));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed(), "fail_open=true：空国家码应放行，不误拦合法玩家");
        assertTrue(d.lookupFailed(), "空国家码应标记 lookupFailed 以便告警管理员");
        // 不缓存：再次查询仍走上游
        service.decide("1.2.3.4").join();
        verify(client, times(2)).lookup("1.2.3.4");
    }

    @Test
    void decide_overCapacity_evictsExpiredEntries() throws Exception {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        // 容量 1 + TTL 5ms：写入第二条时 size=2 > 1，触发 evictExpired 清理已过期条目
        GeoIpAccessService tinyCache = new GeoIpAccessService(client, configs, 5L, 1);
        when(client.lookup("1.1.1.1"))
                .thenReturn(CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}")));
        when(client.lookup("2.2.2.2"))
                .thenReturn(CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}")));

        tinyCache.decide("1.1.1.1").join(); // 写入 A，5ms 后过期
        Thread.sleep(50);
        tinyCache.decide("2.2.2.2").join(); // 写入 B 触发清理，过期的 A 被移除

        // A 已被清理：再次查询应重新走上游
        tinyCache.decide("1.1.1.1").join();
        verify(client, times(2)).lookup("1.1.1.1");
    }
}
