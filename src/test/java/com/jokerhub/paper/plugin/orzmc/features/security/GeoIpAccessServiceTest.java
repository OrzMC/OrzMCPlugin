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
    void decide_geoIpQueryFailure_failsOpen() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        CompletableFuture<GeoIpClient.GeoIpResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        when(client.lookup("1.2.3.4")).thenReturn(failed);

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed(), "查询异常应 fail-open 放行");
    }
}
