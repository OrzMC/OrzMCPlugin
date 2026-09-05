package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * QQ 开放平台 REST 客户端（builtin QQ adapter，协议参照 EasyBot easybot-adapter-qq auth.rs/lib.rs）。
 *
 * <p>承载两类上行 HTTP 调用：</p>
 * <ul>
 *   <li><b>换 access_token</b>：{@code POST {authBase}/app/getAppAccessToken}（app_id + client_secret →
 *       access_token，官方有效期 2h，TTL/预刷新由 {@code RefreshableTokenProvider} 管理）；换取失败返回 null
 *       （凭据问题/限流统一由调用方退避，EasyBot 亦如此）；</li>
 *   <li><b>解析网关 WS 地址</b>：{@code GET {apiBase}/gateway/bot}（鉴权头 {@code QQBot <token>}，返回
 *       {@code {url}}），实现 {@link QqGatewayUrlFetcher}——token 被拒（401/11244/11242）判为 AUTH，供
 *       {@link QqGatewayClient} 触发令牌强制重换。</li>
 * </ul>
 *
 * <p>默认端点对齐 EasyBot：authBase {@code https://bots.qq.com}、apiBase {@code https://api.bot.qq.com}
 * （均支持注入覆盖，R11 域名清单据此）。凭据安全（R5）：任何日志不打 secret。</p>
 */
public final class QqApiClient implements QqGatewayUrlFetcher {

    public static final String DEFAULT_AUTH_BASE = "https://bots.qq.com";
    public static final String DEFAULT_API_BASE = "https://api.bot.qq.com";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final String appId;
    private final String clientSecret;
    private final String authBase;
    private final String apiBase;
    private final java.net.Proxy proxy;
    private final Logger log;

    public QqApiClient(String appId, String clientSecret, Logger log) {
        this(appId, clientSecret, DEFAULT_AUTH_BASE, DEFAULT_API_BASE, java.net.Proxy.NO_PROXY, log);
    }

    /** 便捷：默认端点 + 指定代理（海外/受限网络经 HTTP 代理回国访问 QQ API，D13）。 */
    public QqApiClient(String appId, String clientSecret, java.net.Proxy proxy, Logger log) {
        this(appId, clientSecret, DEFAULT_AUTH_BASE, DEFAULT_API_BASE, proxy, log);
    }

    public QqApiClient(String appId, String clientSecret, String authBase, String apiBase, Logger log) {
        this(appId, clientSecret, authBase, apiBase, java.net.Proxy.NO_PROXY, log);
    }

    public QqApiClient(
            String appId, String clientSecret, String authBase, String apiBase, java.net.Proxy proxy, Logger log) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("clientSecret must not be blank");
        }
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        this.appId = appId;
        this.clientSecret = clientSecret;
        this.authBase = authBase == null || authBase.isBlank() ? DEFAULT_AUTH_BASE : authBase;
        this.apiBase = apiBase == null || apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
        this.log = log;
    }

    /**
     * 换发 access_token（{@code RefreshableTokenProvider} 的 fetch 闭包）。
     *
     * @return access_token；换取失败返回 null（限流 100001/凭据问题/网络错误统一按不可用处理，调用方退避，不泄漏 secret）
     */
    public String fetchAccessToken() {
        JsonObject body = new JsonObject();
        body.addProperty("appId", appId);
        body.addProperty("clientSecret", clientSecret);
        String url = authBase + "/app/getAppAccessToken";
        try {
            HttpResponse<String> resp = AsyncHttp.postJson(
                            url, body.toString(), Map.of(), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 0, proxy)
                    .join();
            String text = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warning("[qq] 换 token 失败（HTTP " + resp.statusCode() + "）: " + clip(text));
                return null;
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("code") && json.get("code").getAsInt() != 0) {
                // 成功响应无 code 字段；带非 0 code（如 100001 限流 / 100016 凭据问题）→ 不可用，退避重试
                log.warning("[qq] 换 token 被拒 code=" + json.get("code").getAsInt() + " message="
                        + (json.has("message") ? clip(json.get("message").getAsString()) : "-"));
                return null;
            }
            if (!json.has("access_token")
                    || json.get("access_token").getAsString().isBlank()) {
                log.warning("[qq] 换 token 响应缺少 access_token");
                return null;
            }
            return json.get("access_token").getAsString();
        } catch (CompletionException e) {
            log.warning("[qq] 换 token 网络异常（将退避重试）: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[qq] 换 token 解析异常（将退避重试）: " + e);
            return null;
        }
    }

    @Override
    public QqGatewayUrlFetcher.Result fetch(String accessToken) {
        String url = apiBase + "/gateway/bot";
        try {
            HttpResponse<String> resp = AsyncHttp.get(
                            url,
                            Map.of("Authorization", "QQBot " + accessToken),
                            CONNECT_TIMEOUT,
                            REQUEST_TIMEOUT,
                            0,
                            proxy)
                    .join();
            String text = resp.body() == null ? "" : resp.body();
            int status = resp.statusCode();
            if (isTokenRejected(status, text)) {
                log.warning("[qq] 网关地址被拒（token 失效）: HTTP " + status);
                return QqGatewayUrlFetcher.Result.authFailure();
            }
            if (status < 200 || status >= 300) {
                log.warning("[qq] 网关地址获取失败（HTTP " + status + "），退避重试: " + clip(text));
                return QqGatewayUrlFetcher.Result.transientFailure();
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("url") || json.get("url").getAsString().isBlank()) {
                log.warning("[qq] 网关地址响应缺少 url: " + clip(text));
                return QqGatewayUrlFetcher.Result.transientFailure();
            }
            return QqGatewayUrlFetcher.Result.success(json.get("url").getAsString());
        } catch (CompletionException e) {
            log.warning("[qq] 网关地址获取网络异常（将退避重试）: " + e.getCause());
            return QqGatewayUrlFetcher.Result.transientFailure();
        } catch (RuntimeException e) {
            log.warning("[qq] 网关地址解析异常（将退避重试）: " + e);
            return QqGatewayUrlFetcher.Result.transientFailure();
        }
    }

    /** token 失效谓词（对齐 EasyBot auth.rs）：HTTP 401 或 body 含业务码 11244 / 11242。同包 QqSender 复用（发送 401 重试一次）。 */
    static boolean isTokenRejected(int status, String body) {
        return status == 401
                || body.contains("11244")
                || body.contains("11242")
                || body.contains("token not exist or expire");
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
