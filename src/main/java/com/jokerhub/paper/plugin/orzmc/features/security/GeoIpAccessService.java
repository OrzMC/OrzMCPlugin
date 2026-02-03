package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GeoIpAccessService {
    public record Decision(boolean allowed, String countryCode, List<String> allowList, String rawJson) {}

    private final GeoIpClient client;
    private final ConfigService configService;

    public GeoIpAccessService(ConfigService configService) {
        this(new GeoIpClient(), configService);
    }

    GeoIpAccessService(GeoIpClient client, ConfigService configService) {
        this.client = client;
        this.configService = configService;
    }

    public CompletableFuture<Decision> decide(String ipAddress) {
        List<String> allow = TypedConfigs.IpWhitelist.from(configService.getConfig("ip_whitelist"))
                .allowCountryCode();
        if (allow.isEmpty()) {
            return CompletableFuture.completedFuture(new Decision(true, "", allow, ""));
        }
        return client.lookup(ipAddress).handle((res, ex) -> {
            if (ex != null || res == null) {
                return new Decision(true, "", allow, "");
            }
            String cc = res.countryCode() == null ? "" : res.countryCode();
            boolean ok = allow.contains(cc);
            return new Decision(ok, cc, allow, res.rawJson());
        });
    }
}
