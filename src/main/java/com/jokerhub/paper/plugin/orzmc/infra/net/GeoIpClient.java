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
        // 单次短超时 + 零重试：GeoIP 查询的决策窗口只有 3s（GeoIpAccessService.DECISION_TIMEOUT_MS），
        // 若沿用通用请求的 3s 超时 + 2 次重试（总预算约 10.5s），决策层必然等不到结果而 fail-open，
        // 且重试在后台空跑。这里把单次尝试上限压到 2s，稳定落在决策窗口内，失败交由调用方 fail-open。
        return AsyncHttp.get(url, Map.of(), Duration.ofSeconds(2), Duration.ofSeconds(2), 0)
                .thenApply(HttpResponse::body)
                .thenApply(raw -> {
                    JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                    String cc =
                            json.has("country_code") ? json.get("country_code").getAsString() : "";
                    return new GeoIpResult(cc, raw);
                });
    }
}
