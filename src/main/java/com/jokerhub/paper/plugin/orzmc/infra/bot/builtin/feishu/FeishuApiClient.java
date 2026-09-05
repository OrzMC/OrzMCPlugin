package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * 飞书开放平台 REST 客户端（builtin 飞书 adapter，协议参照 EasyBot easybot-adapter-feishu lib.rs + 官方 SDK
 * larksuite-oapi-sdk-rs 0.3.11 ws.rs）。
 *
 * <p>承载三类上行 HTTP 调用：</p>
 * <ul>
 *   <li><b>换 tenant_access_token</b>：{@code POST {apiBase}/auth/v3/tenant_access_token/internal}
 *       （app_id + app_secret → tenant_access_token，官方有效期 2h，TTL/预刷新由 {@code RefreshableTokenProvider}
 *       管理）；换取失败返回 null（凭据问题/限流统一由调用方退避）；</li>
 *   <li><b>WS 长连接端点引导</b>：{@code POST {domain}/callback/ws/endpoint}（body {@code AppID/AppSecret}，
 *       返回 {@code data.URL} + {@code data.ClientConfig}——含服务端下发的 {@code PingInterval}），
 *       见官方 SDK {@code WsGateway::endpoint}——引导不依赖 tenant token（凭据直换）；</li>
 *   <li><b>群信息/角色查询</b>：{@code GET {apiBase}/im/v1/chats/{chatId}}（鉴权头 {@code Bearer <token>}，
 *       返回 {@code data.owner_id} + {@code data.user_manager_id_list}，EasyBot 依此判定 Owner/Admin/Member）。</li>
 * </ul>
 *
 * <p>默认端点：apiBase {@code https://open.feishu.cn/open-apis}、domain {@code https://open.feishu.cn}
 * （均支持注入覆盖，R11 域名清单据此）。凭据安全（R5）：任何日志不打 secret。</p>
 */
public final class FeishuApiClient {

    public static final String DEFAULT_DOMAIN = "https://open.feishu.cn";
    public static final String DEFAULT_API_BASE = DEFAULT_DOMAIN + "/open-apis";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final String appId;
    private final String appSecret;
    private final String domain;
    private final String apiBase;
    private final java.net.Proxy proxy;
    private final Logger log;

    public FeishuApiClient(String appId, String appSecret, Logger log) {
        this(appId, appSecret, DEFAULT_DOMAIN, DEFAULT_API_BASE, java.net.Proxy.NO_PROXY, log);
    }

    /** 便捷：默认端点 + 指定代理（海外/受限网络经 HTTP 代理回国访问飞书 API，D13）。 */
    public FeishuApiClient(String appId, String appSecret, java.net.Proxy proxy, Logger log) {
        this(appId, appSecret, DEFAULT_DOMAIN, DEFAULT_API_BASE, proxy, log);
    }

    public FeishuApiClient(String appId, String appSecret, String domain, String apiBase, Logger log) {
        this(appId, appSecret, domain, apiBase, java.net.Proxy.NO_PROXY, log);
    }

    public FeishuApiClient(
            String appId, String appSecret, String domain, String apiBase, java.net.Proxy proxy, Logger log) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret must not be blank");
        }
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        this.appId = appId;
        this.appSecret = appSecret;
        this.domain = domain == null || domain.isBlank() ? DEFAULT_DOMAIN : domain;
        this.apiBase = apiBase == null || apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
        this.log = log;
    }

    /**
     * 换发 tenant_access_token（{@code RefreshableTokenProvider} 的 fetch 闭包）。
     *
     * @return tenant_access_token；换取失败返回 null（凭据问题/网络错误统一按不可用处理，调用方退避）
     */
    public String fetchTenantToken() {
        JsonObject body = new JsonObject();
        body.addProperty("app_id", appId);
        body.addProperty("app_secret", appSecret);
        String url = apiBase + "/auth/v3/tenant_access_token/internal";
        try {
            HttpResponse<String> resp = AsyncHttp.postJson(
                            url, body.toString(), Map.of(), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 0, proxy)
                    .join();
            String text = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warning("[feishu] 换 tenant token 失败（HTTP " + resp.statusCode() + "）: " + clip(text));
                return null;
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int code = json.has("code") ? json.get("code").getAsInt() : -1;
            if (code != 0) {
                log.warning("[feishu] 换 tenant token 被拒 code=" + code + " msg="
                        + (json.has("msg") ? clip(json.get("msg").getAsString()) : "-"));
                return null;
            }
            if (!json.has("tenant_access_token")
                    || json.get("tenant_access_token").getAsString().isBlank()) {
                log.warning("[feishu] 换 tenant token 响应缺少 tenant_access_token");
                return null;
            }
            return json.get("tenant_access_token").getAsString();
        } catch (CompletionException e) {
            log.warning("[feishu] 换 tenant token 网络异常（将退避重试）: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[feishu] 换 tenant token 解析异常（将退避重试）: " + e);
            return null;
        }
    }

    /** WS 长连接端点引导结果（官方 SDK {@code WsEndpoint}）。 */
    public record WsEndpoint(String url, long pingIntervalSecs, long reconnectIntervalSecs, long reconnectNonceSecs) {}

    /**
     * 引导 WS 长连接端点（{@code POST {domain}/callback/ws/endpoint}，AppID/AppSecret 直换，不依赖 tenant token）。
     *
     * @return 端点信息；失败返回 null（调用方退避重试）
     */
    public WsEndpoint fetchWsEndpoint() {
        JsonObject body = new JsonObject();
        body.addProperty("AppID", appId);
        body.addProperty("AppSecret", appSecret);
        String url = domain + "/callback/ws/endpoint";
        try {
            HttpResponse<String> resp = AsyncHttp.postJson(
                            url, body.toString(), Map.of("locale", "zh"), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 0, proxy)
                    .join();
            String text = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warning("[feishu] WS 端点引导失败（HTTP " + resp.statusCode() + "）: " + clip(text));
                return null;
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int code = json.has("code") ? json.get("code").getAsInt() : -1;
            if (code != 0 || !json.has("data") || json.get("data").isJsonNull()) {
                log.warning("[feishu] WS 端点引导被拒 code=" + code + " msg="
                        + (json.has("msg") ? clip(json.get("msg").getAsString()) : "-"));
                return null;
            }
            JsonObject data = json.getAsJsonObject("data");
            if (!data.has("URL") || data.get("URL").getAsString().isBlank()) {
                log.warning("[feishu] WS 端点引导响应缺少 URL: " + clip(text));
                return null;
            }
            long ping = 120L;
            long reconnectInterval = 120L;
            long reconnectNonce = 30L;
            if (data.has("ClientConfig") && data.get("ClientConfig").isJsonObject()) {
                JsonObject cfg = data.getAsJsonObject("ClientConfig");
                ping = cfg.has("PingInterval") ? cfg.get("PingInterval").getAsLong() : ping;
                reconnectInterval = cfg.has("ReconnectInterval")
                        ? cfg.get("ReconnectInterval").getAsLong()
                        : reconnectInterval;
                reconnectNonce =
                        cfg.has("ReconnectNonce") ? cfg.get("ReconnectNonce").getAsLong() : reconnectNonce;
            }
            return new WsEndpoint(data.get("URL").getAsString(), ping, reconnectInterval, reconnectNonce);
        } catch (CompletionException e) {
            log.warning("[feishu] WS 端点引导网络异常（将退避重试）: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[feishu] WS 端点引导解析异常（将退避重试）: " + e);
            return null;
        }
    }

    /** 群角色查询结果（/im/v1/chats/{id}：群主 + 管理员）。owner/manager 与入站 sender open_id 同格式（EasyBot 语义，真机核验）。 */
    public record ChatRoles(String ownerId, List<String> managerIds) {
        public boolean isOwnerOrManager(String senderId) {
            if (senderId == null || senderId.isBlank()) {
                return false;
            }
            return senderId.equals(ownerId) || (managerIds != null && managerIds.contains(senderId));
        }
    }

    /**
     * 查询群信息/角色（{@code GET {apiBase}/im/v1/chats/{chatId}}，Bearer tenant token）。
     *
     * @return 群主+管理员 id 列表；失败/无权限返回 null（调用方按普通成员降级，不阻塞入站）
     */
    public ChatRoles fetchChatRoles(String chatId, String tenantToken) {
        if (chatId == null || chatId.isBlank()) {
            return null;
        }
        String url = apiBase + "/im/v1/chats/" + chatId;
        try {
            HttpResponse<String> resp = AsyncHttp.get(
                            url,
                            Map.of("Authorization", "Bearer " + tenantToken),
                            CONNECT_TIMEOUT,
                            REQUEST_TIMEOUT,
                            0,
                            proxy)
                    .join();
            String text = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warning("[feishu] 群信息查询失败（HTTP " + resp.statusCode() + "）: " + clip(text));
                return null;
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int code = json.has("code") ? json.get("code").getAsInt() : -1;
            if (code != 0 || !json.has("data") || json.get("data").isJsonNull()) {
                // 常见：无 im:chat 权限 / 应用不在群内 → 按无角色降级（不刷屏，调用方缓存负结果）
                log.warning("[feishu] 群信息查询被拒 code=" + code + " msg="
                        + (json.has("msg") ? clip(json.get("msg").getAsString()) : "-"));
                return null;
            }
            JsonObject data = json.getAsJsonObject("data");
            String ownerId = data.has("owner_id") && !data.get("owner_id").isJsonNull()
                    ? data.get("owner_id").getAsString()
                    : null;
            List<String> managers = new ArrayList<>();
            if (data.has("user_manager_id_list")
                    && data.get("user_manager_id_list").isJsonArray()) {
                JsonArray arr = data.getAsJsonArray("user_manager_id_list");
                for (int i = 0; i < arr.size(); i++) {
                    managers.add(arr.get(i).getAsString());
                }
            }
            return new ChatRoles(ownerId, managers);
        } catch (CompletionException e) {
            log.warning("[feishu] 群信息查询网络异常（按普通成员降级）: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[feishu] 群信息查询解析异常（按普通成员降级）: " + e);
            return null;
        }
    }

    /** token 失效谓词（对齐 EasyBot lib.rs）：HTTP 401 或 body 含飞书 token 错误码 99991663/99991665/20013/20005。 */
    static boolean isTokenRejected(int status, String body) {
        return status == 401
                || body.contains("99991663")
                || body.contains("99991665")
                || body.contains("20013")
                || body.contains("20005");
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
