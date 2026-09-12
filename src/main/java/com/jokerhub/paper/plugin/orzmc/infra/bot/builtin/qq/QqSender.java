package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.google.gson.JsonObject;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * QQ 开放平台下行消息发送（builtin QQ adapter，方案 §6 / D7 / D14；协议参照 EasyBot lib.rs 发送路径）。
 *
 * <p>两类目标通道（均为 POST JSON）：</p>
 * <ul>
 *   <li>群消息 {@code POST {api}/v2/groups/{group_openid}/messages}；</li>
 *   <li>C2C 私聊 {@code POST {api}/v2/users/{user_openid}/messages}。</li>
 * </ul>
 *
 * <p>文本请求体 {@code {"content": text, "msg_type": 0}}；携带 {@code replyMsgId}（来源消息 id）时追加
 * {@code msg_id} —— 即 QQ 被动回复通道（D14：被动回复带 msg_id 走短窗口，主动广播不带 msg_id 受配额/频控，
 * 主动广播的节流沿用插件既有聚合，调用方负责）。</p>
 *
 * <p><b>发送语义（D7）</b>：尽力一次不重试（无持久化幂等，重试会造成重复通知）——例外仅两类：
 * ① 平台明确 token 失效（HTTP 401 / 业务码 11244/11242）时经 {@link TokenProvider#onAuthFailure()} 强制重换一次并
 * 重试一次（鉴权层重试，对齐方案 §4.2）；② <b>连接阶段失败</b>（{@link #isConnectPhaseFailure}：连接超时 / DNS /
 * TLS 握手 / 连接被拒——请求字节未到达平台，必然未投递）重试一次（JDK 只对 {@code ConnectException} 的幂等请求
 * 自动重试，连接超时不在其列，故此处自行兜底）。请求阶段超时（连上但无响应）结果未知，<b>不重试</b>。
 * 失败结果返回 false 供健康告警（S7 聚合）。凭据安全（R5）：任何日志不打 token。</p>
 */
public final class QqSender {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** 连接阶段重试的等待：给中间设备/边缘节点瞬态拒绝留恢复窗口（仅一次，不做指数退避）。 */
    private static final long CONNECT_RETRY_DELAY_MS = 300;

    private final Logger log;
    private final TokenProvider tokens;
    private final String apiBase;
    private final java.net.Proxy proxy;

    public QqSender(Logger log, TokenProvider tokens) {
        this(log, tokens, QqApiClient.DEFAULT_API_BASE, java.net.Proxy.NO_PROXY);
    }

    public QqSender(Logger log, TokenProvider tokens, String apiBase) {
        this(log, tokens, apiBase, java.net.Proxy.NO_PROXY);
    }

    /** 便捷：默认端点 + 指定代理（海外/受限网络经 HTTP 代理回国访问 QQ API，D13）。 */
    public QqSender(Logger log, TokenProvider tokens, java.net.Proxy proxy) {
        this(log, tokens, QqApiClient.DEFAULT_API_BASE, proxy);
    }

    public QqSender(Logger log, TokenProvider tokens, String apiBase, java.net.Proxy proxy) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        this.log = log;
        this.tokens = tokens;
        this.apiBase = apiBase == null || apiBase.isBlank() ? QqApiClient.DEFAULT_API_BASE : apiBase;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
    }

    /**
     * 发送群消息。
     *
     * @param groupOpenid QQ 群 openid
     * @param text 文本内容（仅文本，D6）
     * @param replyMsgId 被动回复的来源消息 id（无则 null）
     * @return 发送成功 true；失败 false（含 token 重换后仍被拒 / 非 2xx / 网络错误），调用方健康告警
     */
    public CompletableFuture<Boolean> sendGroupMessage(String groupOpenid, String text, String replyMsgId) {
        return send("/v2/groups/" + groupOpenid + "/messages", text, replyMsgId);
    }

    /**
     * 发送 C2C 私聊消息。
     *
     * @param userOpenid QQ 用户 openid
     * @param text 文本内容（仅文本，D6）
     * @param replyMsgId 被动回复的来源消息 id（无则 null）
     * @return 发送成功 true；失败 false
     */
    public CompletableFuture<Boolean> sendDirectMessage(String userOpenid, String text, String replyMsgId) {
        return send("/v2/users/" + userOpenid + "/messages", text, replyMsgId);
    }

    private CompletableFuture<Boolean> send(String path, String text, String replyMsgId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        String url = apiBase + path;
        String firstToken = tokens.fresh();
        if (firstToken == null) {
            log.warning("[qq] 发送无可用 access_token，丢弃: " + path);
            return CompletableFuture.completedFuture(false);
        }
        return postWithConnectRetry(url, firstToken, text, replyMsgId, path)
                .thenCompose(resp -> {
                    String body = resp.body() == null ? "" : resp.body();
                    if (is2xx(resp)) {
                        return CompletableFuture.completedFuture(true);
                    }
                    if (QqApiClient.isTokenRejected(resp.statusCode(), body)) {
                        // token 失效：强制重换一次并重试一次（鉴权层自愈；仍失败按投递失败告警）
                        String freshToken = tokens.onAuthFailure();
                        if (freshToken == null) {
                            log.warning("[qq] token 重换失败，消息投递失败（不再重试）: " + path);
                            return CompletableFuture.completedFuture(false);
                        }
                        log.info("[qq] token 失效已重换，重试一次投递: " + path);
                        return postWithConnectRetry(url, freshToken, text, replyMsgId, path)
                                .thenApply(resp2 -> {
                                    if (!is2xx(resp2)) {
                                        log.warning("[qq] 重试仍失败（HTTP " + resp2.statusCode() + "），丢弃: " + path);
                                        return false;
                                    }
                                    return true;
                                });
                    }
                    log.warning("[qq] 投递失败（HTTP " + resp.statusCode() + "，不重试）: " + path + " " + clip(body));
                    return CompletableFuture.completedFuture(false);
                })
                .exceptionally(ex -> {
                    Throwable cause = unwrap(ex);
                    if (isConnectPhaseFailure(cause)) {
                        log.warning("[qq] 投递失败（连接阶段异常，已重试一次）: " + path + " " + cause);
                    } else if (cause instanceof java.net.http.HttpTimeoutException) {
                        // 请求已发出但无响应：结果未知（可能已送达），禁止重试以免重复通知
                        log.warning("[qq] 投递网络异常（请求已发出但无响应，结果未知，不重试）: " + path + " " + cause);
                    } else {
                        log.warning("[qq] 投递网络异常（不重试）: " + path + " " + cause);
                    }
                    return false;
                });
    }

    /**
     * POST + 连接阶段失败兑底重试（仅一次）。
     *
     * <p>连接阶段失败意味着请求字节未到达平台（TCP/TLS 未完成或连接被拒），重试不会造成重复投递；请求阶段
     * 失败（含 {@link java.net.http.HttpTimeoutException}）直接上抛，交上层按「结果未知」告警且不重试。</p>
     */
    private CompletableFuture<HttpResponse<String>> postWithConnectRetry(
            String url, String token, String text, String replyMsgId, String path) {
        return post(url, token, text, replyMsgId)
                .handle((resp, ex) -> {
                    if (ex == null) {
                        return CompletableFuture.completedFuture(resp);
                    }
                    Throwable cause = unwrap(ex);
                    if (!isConnectPhaseFailure(cause)) {
                        return CompletableFuture.<HttpResponse<String>>failedFuture(cause);
                    }
                    log.info("[qq] 连接阶段失败（" + cause.getClass().getSimpleName() + "），重试一次投递: " + path);
                    return CompletableFuture.<Void>supplyAsync(
                                    () -> null,
                                    CompletableFuture.delayedExecutor(CONNECT_RETRY_DELAY_MS, TimeUnit.MILLISECONDS))
                            .thenCompose(ignored -> post(url, token, text, replyMsgId));
                })
                .thenCompose(f -> f);
    }

    /**
     * 是否为「连接阶段失败」（请求字节必然未到达平台 → 重试安全，不会产生重复通知）。
     *
     * <p>集合依据 JDK 25 行为核定：{@code HttpClient.connectTimeout} 到期抛
     * {@link java.net.http.HttpConnectTimeoutException}（**不在** JDK 自动重试集合内），DNS 失败为
     * {@link java.net.UnknownHostException}，连接被拒/无路由为 {@link java.net.ConnectException} /
     * {@link java.net.NoRouteToHostException}，TLS 握手失败为 {@link javax.net.ssl.SSLHandshakeException}。
     * 特意排除 {@link java.net.http.HttpTimeoutException} 基类（请求阶段超时=可能已投递）。</p>
     */
    static boolean isConnectPhaseFailure(Throwable cause) {
        return cause instanceof java.net.http.HttpConnectTimeoutException
                || cause instanceof java.net.ConnectException
                || cause instanceof java.net.NoRouteToHostException
                || cause instanceof java.net.UnknownHostException
                || cause instanceof javax.net.ssl.SSLHandshakeException;
    }

    private CompletableFuture<HttpResponse<String>> post(String url, String token, String text, String replyMsgId) {
        JsonObject body = new JsonObject();
        body.addProperty("content", text);
        body.addProperty("msg_type", 0);
        if (replyMsgId != null && !replyMsgId.isBlank()) {
            body.addProperty("msg_id", replyMsgId); // 被动回复通道（D14）
        }
        return AsyncHttp.postJson(
                url,
                body.toString(),
                Map.of("Authorization", "QQBot " + token),
                CONNECT_TIMEOUT,
                REQUEST_TIMEOUT,
                0,
                proxy);
    }

    private static boolean is2xx(HttpResponse<String> resp) {
        return resp.statusCode() >= 200 && resp.statusCode() < 300;
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur instanceof CompletionException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
