package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.math.BigInteger;
import java.net.Proxy;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Discord REST API 客户端（builtin Discord adapter，批次 5b；官方 v10 REST）。
 *
 * <p>承载入站通道引导与出站投递等 HTTP 调用（同步阻塞返回——仅被网关调度线程/自有线程调用，不触服务器线程
 * 红线 R12）：</p>
 * <ul>
 *   <li><b>fetchGatewayUrl</b>：/gateway/bot 取 WS 网关地址（鉴权 401 识别配置错误）；</li>
 *   <li><b>sendChannelMessage</b>：向频道发文本（群聊频道与 DM 通道同一 REST，回复统一走来源频道）；</li>
 *   <li><b>ensureDmChannel</b>：按用户 id 建/取 DM 通道（每用户缓存，私聊出站用）；</li>
 *   <li><b>getGuildOwner / getGuildRolesPermissions / getGuildMemberRoles</b>：群管理角色判定数据源。</li>
 * </ul>
 *
 * <p>认证头 {@code Authorization: Bot <token>}（REST 前缀；gateway identify 为裸 token，见 GatewayClient）。
 * 代理（D13）：全部调用透传 {@code Proxy}（null/NO_PROXY = 直连）。凭据安全（R5）：不打 token。
 * User-Agent 统一由 {@link AsyncHttp} 设置（Discord 要求 UA）。</p>
 */
public final class DiscordApiClient {

    /** Discord v10 REST 端点（R11 域名清单据此；可注入覆盖）。 */
    public static final String DEFAULT_API_BASE = "https://discord.com/api/v10";

    /** 网关 URL 缓存窗口：/gateway/bot 有频率限制（Discord 官方 session start limit），窗口内复用不重取。 */
    private static final long GATEWAY_URL_CACHE_MS = 60_000;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String apiBase;
    private final String token;
    private final String authHeader;
    private final Proxy proxy;
    private final Logger log;

    private volatile String cachedGatewayUrl;
    private volatile long cachedGatewayUrlMs;
    /** 用户 id → DM 通道 id 缓存（Discord DM 通道与用户唯一对应且稳定）。 */
    private final ConcurrentHashMap<String, String> dmChannels = new ConcurrentHashMap<>();

    public DiscordApiClient(String token, Logger log) {
        this(token, DEFAULT_API_BASE, Proxy.NO_PROXY, log);
    }

    public DiscordApiClient(String token, String apiBase, Proxy proxy, Logger log) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        this.token = token;
        this.apiBase = apiBase == null || apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
        this.authHeader = "Bot " + token;
        this.proxy = proxy == null ? Proxy.NO_PROXY : proxy;
        this.log = log;
    }

    // =====================================================================
    // 网关引导 / 出站投递
    // =====================================================================

    /**
     * 取 WS 网关地址（/gateway/bot；60s 缓存防限频）。
     *
     * @return wss URL（如 {@code wss://gateway.discord.gg/?v=10&encoding=json}）；失败（网络/401/解析）→ null
     */
    public String fetchGatewayUrl() {
        long now = System.currentTimeMillis();
        String cached = cachedGatewayUrl;
        if (cached != null && now - cachedGatewayUrlMs < GATEWAY_URL_CACHE_MS) {
            return cached;
        }
        HttpResponse<String> resp = sendGet(baseUrl("/gateway/bot"));
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonObject body = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (!body.has("url") || body.get("url").isJsonNull()) {
                return null;
            }
            String url = body.get("url").getAsString();
            if (!url.startsWith("ws") && url.startsWith("http")) {
                url = "ws" + url.substring(4); // https:// → wss://（官方返回 https，WS 客户端需 wss）
            }
            cachedGatewayUrl = url;
            cachedGatewayUrlMs = now;
            return url;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[discord] /gateway/bot 解析失败: " + e);
            return null;
        }
    }

    /**
     * 向频道发文本（群聊频道 / DM 通道通用；尽力一次 D7）。
     *
     * @return 成功 true；失败（HTTP 非 2xx/网络/空 content 被拒）false
     */
    public boolean sendChannelMessage(String channelId, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", text);
        HttpResponse<String> resp = sendPost(baseUrl("/channels/" + channelId + "/messages"), body.toString());
        if (resp == null) {
            return false;
        }
        if (!ok(resp)) {
            log.warning("[discord] 频道消息发送失败（HTTP " + resp.statusCode() + "）: " + clip(resp.body()));
            return false;
        }
        return true;
    }

    /**
     * 按用户 id 建/取 DM 通道（幂等：重复 POST 返回既有 DM 通道；结果按用户缓存）。
     *
     * @return DM channel id；失败 → null
     */
    public String ensureDmChannel(String userId) {
        String cached = dmChannels.get(userId);
        if (cached != null) {
            return cached;
        }
        JsonObject body = new JsonObject();
        body.addProperty("recipient_id", userId);
        HttpResponse<String> resp = sendPost(baseUrl("/users/@me/channels"), body.toString());
        if (resp == null || !ok(resp)) {
            log.warning("[discord] 创建 DM 通道失败 userId=" + userId + (resp == null ? "" : " HTTP " + resp.statusCode()));
            return null;
        }
        try {
            JsonObject data = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (!data.has("id") || data.get("id").isJsonNull()) {
                return null;
            }
            String dmId = data.get("id").getAsString();
            dmChannels.put(userId, dmId);
            return dmId;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[discord] DM 通道响应解析失败: " + e);
            return null;
        }
    }

    // =====================================================================
    // 群管理角色判定数据源（REST；缓存由 RoleResolver 层做）
    // =====================================================================

    /**
     * 群 owner 用户 id。
     *
     * @return owner 用户 id；失败（bot 不在群/网络）→ null
     */
    public String getGuildOwner(String guildId) {
        HttpResponse<String> resp = sendGet(baseUrl("/guilds/" + guildId));
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonObject data = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (data.has("owner_id") && !data.get("owner_id").isJsonNull()) {
                return data.get("owner_id").getAsString();
            }
            return null;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[discord] guild 查询解析失败: " + e);
            return null;
        }
    }

    /**
     * 群角色 id → 权限位（BigInteger 权限位串转数值）。
     *
     * @return 角色权限表；查询失败（403 无权限/bot 不在群/网络）→ null（RoleResolver 按失败处理）
     */
    public Map<String, BigInteger> getGuildRolesPermissions(String guildId) {
        HttpResponse<String> resp = sendGet(baseUrl("/guilds/" + guildId + "/roles"));
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonArray arr = JsonParser.parseString(resp.body() == null ? "[]" : resp.body())
                    .getAsJsonArray();
            Map<String, BigInteger> perms = new java.util.HashMap<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject role = el.getAsJsonObject();
                if (!role.has("id")
                        || !role.has("permissions")
                        || role.get("permissions").isJsonNull()) {
                    continue;
                }
                try {
                    perms.put(
                            role.get("id").getAsString(),
                            new BigInteger(role.get("permissions").getAsString()));
                } catch (NumberFormatException ignore) {
                    // 畸形权限位忽略该角色
                }
            }
            return perms;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[discord] guild roles 解析失败: " + e);
            return null;
        }
    }

    /**
     * 成员角色 id 列表。
     *
     * @return 成员角色 id 列表；失败 → null（RoleResolver 按非管理处理）
     */
    public List<String> getGuildMemberRoles(String guildId, String userId) {
        HttpResponse<String> resp = sendGet(baseUrl("/guilds/" + guildId + "/members/" + userId));
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonObject data = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            List<String> roles = new ArrayList<>();
            if (data.has("roles") && data.get("roles").isJsonArray()) {
                for (JsonElement el : data.getAsJsonArray("roles")) {
                    if (el.isJsonPrimitive()) {
                        roles.add(el.getAsString());
                    }
                }
            }
            return roles;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[discord] member 查询解析失败: " + e);
            return null;
        }
    }

    /** 指定角色是否具备管理员权限（ADMINISTRATOR 或 MANAGE_GUILD）。 */
    boolean hasAdminPermission(BigInteger rolePermissions) {
        if (rolePermissions == null) {
            return false;
        }
        return rolePermissions.testBit(3) || rolePermissions.testBit(5);
    }

    // =====================================================================
    // HTTP 工具
    // =====================================================================

    private String baseUrl(String path) {
        return apiBase + path;
    }

    private boolean ok(HttpResponse<String> resp) {
        if (resp == null) {
            return false;
        }
        int status = resp.statusCode();
        if (status >= 200 && status < 300) {
            return true;
        }
        // 401 = token 配置错误（长期凭据无刷新，GatewayClient 层据此停用）；其余错误由调用方区分
        if (status != 401) {
            log.warning("[discord] API 调用失败（HTTP " + status + "）: " + clip(resp.body()));
        }
        return false;
    }

    private HttpResponse<String> sendGet(String url) {
        try {
            return AsyncHttp.get(url, authHeaders(), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 0, proxy)
                    .join();
        } catch (CompletionException e) {
            log.warning("[discord] API 网络异常: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[discord] API 调用异常: " + e);
            return null;
        }
    }

    private HttpResponse<String> sendPost(String url, String json) {
        try {
            return AsyncHttp.postJson(url, json, authHeaders(), CONNECT_TIMEOUT, REQUEST_TIMEOUT, 0, proxy)
                    .join();
        } catch (CompletionException e) {
            log.warning("[discord] API 网络异常: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[discord] API 调用异常: " + e);
            return null;
        }
    }

    private Map<String, String> authHeaders() {
        return Map.of("Authorization", authHeader);
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
