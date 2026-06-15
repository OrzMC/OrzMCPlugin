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

    @Test
    void decide_allowsAll_whenAllowListEmpty() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of()));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed());
        assertEquals("", d.countryCode());
    }

    @Test
    void decide_allowsMatchingCountry() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(client.lookup("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("CN", "{}")));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertTrue(d.allowed());
        assertEquals("CN", d.countryCode());
    }

    @Test
    void decide_blocksNonMatchingCountry() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(client.lookup("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(new GeoIpClient.GeoIpResult("US", "{}")));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        assertFalse(d.allowed());
        assertEquals("US", d.countryCode());
    }

    @Test
    void decide_allowsOnLookupFailure() {
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(client.lookup("1.2.3.4")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        GeoIpAccessService.Decision d = service.decide("1.2.3.4").join();

        // Fail-open: allow on lookup error
        assertTrue(d.allowed());
    }
}
