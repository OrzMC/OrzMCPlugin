package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.Proxy;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * Telegram Bot API 客户端（builtin Telegram adapter，批次 5a；官方 Bot API 长轮询协议）。
 *
 * <p>承载四类 HTTP 调用（均同步阻塞返回——长轮询循环运行在自有线程，不触服务器线程红线）：</p>
 * <ul>
 *   <li><b>getUpdates</b>：长轮询取增量（{@code offset}/{@code timeout}），R8 语义由调用方推进；</li>
 *   <li><b>sendMessage</b>：出站文本（chat_id 直发）；</li>
 *   <li><b>getChatAdministrators</b>：群管理员列表（creator/administrator，角色判定用）；</li>
 *   <li><b>getMe</b>：启动自检（bot 名/401 配置错误识别）。</li>
 * </ul>
 *
 * <p>代理（D13）：全部调用透传 {@code Proxy}（null/NO_PROXY = 直连）。凭据安全（R5）：不打 token。
 * 单条上限对齐官方 4096（R7），由上层分段。</p>
 */
public final class TelegramApiClient {

    /** Bot API 默认端点（R11 域名清单据此；可注入覆盖）。 */
    public static final String DEFAULT_API_BASE = "https://api.telegram.org";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** getUpdates 长轮询请求超时（须 > 轮询挂起秒数，否则自身超时先掐断长轮询）。 */
    private static final Duration POLL_REQUEST_TIMEOUT = Duration.ofSeconds(50);

    private final String apiBase;
    private final String token;
    private final Proxy proxy;
    private final Logger log;

    public TelegramApiClient(String token, Logger log) {
        this(token, DEFAULT_API_BASE, Proxy.NO_PROXY, log);
    }

    public TelegramApiClient(String token, String apiBase, Proxy proxy, Logger log) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        this.token = token;
        this.apiBase = apiBase == null || apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
        this.proxy = proxy == null ? Proxy.NO_PROXY : proxy;
        this.log = log;
    }

    /** Bot API 调用失败结果（HTTP/解析/网络错误）。 */
    public record ApiError(int httpStatus, String description) {
        @Override
        public String toString() {
            return "HTTP " + httpStatus + (description == null || description.isBlank() ? "" : " " + description);
        }
    }

    /** getUpdates 长轮询响应（ok + updates 数组 + 失败原因）。 */
    public record GetUpdatesResult(boolean ok, List<JsonObject> updates, ApiError error) {}

    /** 单条 Update 拉取（长轮询）：offset=上次处理最后 update_id+1，timeout 秒挂起等新事件。 */
    public GetUpdatesResult getUpdates(long offset, int timeoutSecs) {
        String url = methodUrl("getUpdates") + "&timeout=" + timeoutSecs + "&limit=50";
        if (offset > 0) {
            url += "&offset=" + offset;
        }
        HttpResponse<String> resp = sendGet(url, POLL_REQUEST_TIMEOUT);
        if (resp == null) {
            return new GetUpdatesResult(false, List.of(), new ApiError(-1, "network error"));
        }
        if (!ok(resp)) {
            return new GetUpdatesResult(false, List.of(), errorOf(resp));
        }
        List<JsonObject> updates = new ArrayList<>();
        try {
            JsonObject body = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (!body.has("ok") || !body.get("ok").getAsBoolean()) {
                return new GetUpdatesResult(false, List.of(), errorOf(resp));
            }
            if (body.has("result") && body.get("result").isJsonArray()) {
                for (JsonElement el : body.getAsJsonArray("result")) {
                    if (el.isJsonObject()) {
                        updates.add(el.getAsJsonObject());
                    }
                }
            }
            return new GetUpdatesResult(true, updates, null);
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[telegram] getUpdates 响应解析失败: " + e);
            return new GetUpdatesResult(false, List.of(), new ApiError(resp.statusCode(), "parse error"));
        }
    }

    /** getMe 自检：成功返回 bot 用户名；失败（含 401 配置错误）返回 null。 */
    public String getMe() {
        HttpResponse<String> resp = sendGet(methodUrl("getMe"));
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonObject body = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (!body.has("result") || body.get("result").isJsonNull()) {
                return null;
            }
            JsonObject me = body.getAsJsonObject("result");
            return me.has("username") ? me.get("username").getAsString() : null;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[telegram] getMe 解析失败: " + e);
            return null;
        }
    }

    /** sendMessage：投递文本（尽力一次 D7，失败返回 false 由调用方记健康告警）。 */
    public boolean sendMessage(long chatId, String text) {
        String url = methodUrl("sendMessage") + "&chat_id=" + chatId + "&text=" + urlEncode(text);
        HttpResponse<String> resp = sendGet(url);
        if (resp == null) {
            return false;
        }
        // TG 业务错误（bot 被踢/无权限等）返回 HTTP 200 + ok:false —— 须解析 body 判定
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            try {
                JsonObject body = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                        .getAsJsonObject();
                if (body.has("ok") && !body.get("ok").getAsBoolean()) {
                    log.warning("[telegram] sendMessage 被拒: " + clip(resp.body()));
                    return false;
                }
                return true;
            } catch (JsonSyntaxException | IllegalStateException e) {
                return false; // 响应非预期 JSON → 视为失败
            }
        }
        return false;
    }

    /**
     * 群管理员列表（角色判定 R10：creator/administrator → admin）。
     *
     * @return 管理员 user id 集合；查询失败（非群/无权限/网络）返回 null（由调用方按非管理处理）
     */
    public List<Long> getChatAdministrators(long chatId) {
        String url = methodUrl("getChatAdministrators") + "&chat_id=" + chatId;
        HttpResponse<String> resp = sendGet(url);
        if (resp == null || !ok(resp)) {
            return null;
        }
        try {
            JsonObject body = JsonParser.parseString(resp.body() == null ? "{}" : resp.body())
                    .getAsJsonObject();
            if (!body.has("ok") || !body.get("ok").getAsBoolean()) {
                return null;
            }
            List<Long> admins = new ArrayList<>();
            if (body.has("result") && body.get("result").isJsonArray()) {
                for (JsonElement el : body.getAsJsonArray("result")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject member = el.getAsJsonObject();
                    // 仅 creator / administrator 视为管理员（member 不是）
                    String status = member.has("status") ? member.get("status").getAsString() : "";
                    if ("creator".equals(status) || "administrator".equals(status)) {
                        if (member.has("user") && member.get("user").isJsonObject()) {
                            JsonObject user = member.getAsJsonObject("user");
                            if (user.has("id")) {
                                admins.add(user.get("id").getAsLong());
                            }
                        }
                    }
                }
            }
            return admins;
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.warning("[telegram] getChatAdministrators 解析失败: " + e);
            return null;
        }
    }

    private boolean ok(HttpResponse<String> resp) {
        if (resp == null) {
            return false;
        }
        int status = resp.statusCode();
        if (status >= 200 && status < 300) {
            return true;
        }
        // 401 = token 配置错误（长期凭据无刷新语义，告警由上层处理）
        log.warning("[telegram] Bot API 调用失败（HTTP " + status + "）: " + clip(resp.body()));
        return false;
    }

    private ApiError errorOf(HttpResponse<String> resp) {
        if (resp == null) {
            return new ApiError(-1, "network error");
        }
        return new ApiError(resp.statusCode(), clip(resp.body()));
    }

    private HttpResponse<String> sendGet(String url) {
        return sendGet(url, REQUEST_TIMEOUT);
    }

    private HttpResponse<String> sendGet(String url, Duration requestTimeout) {
        try {
            return AsyncHttp.get(url, Map.of(), CONNECT_TIMEOUT, requestTimeout, 0, proxy)
                    .join();
        } catch (CompletionException e) {
            log.warning("[telegram] Bot API 网络异常: " + e.getCause());
            return null;
        } catch (RuntimeException e) {
            log.warning("[telegram] Bot API 调用异常: " + e);
            return null;
        }
    }

    private String methodUrl(String method) {
        return apiBase + "/bot" + token + "/" + method + "?";
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }
}
