package com.jokerhub.paper.plugin.orzmc.infra.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class GeoIpClient {
    public record GeoIpResult(String countryCode, String rawJson) {}

    public CompletableFuture<GeoIpResult> lookup(String ipAddress) {
        String url = "https://get.geojs.io/v1/ip/geo/" + ipAddress + ".json";
        return AsyncHttp.get(url, Map.of(), Duration.ofSeconds(3), Duration.ofSeconds(3), 2)
                .thenApply(HttpResponse::body)
                .thenApply(raw -> {
                    JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                    String cc =
                            json.has("country_code") ? json.get("country_code").getAsString() : "";
                    return new GeoIpResult(cc, raw);
                });
    }
}
