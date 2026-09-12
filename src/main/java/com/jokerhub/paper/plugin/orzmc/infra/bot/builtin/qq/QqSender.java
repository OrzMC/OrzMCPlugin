package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.google.gson.JsonObject;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImWorkerPool;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * {@code msg_id} 与递增的 {@code msg_seq} —— 即 QQ 被动回复通道（D14：被动回复带 msg_id 走短窗口，主动广播不带 msg_id 受配额/频控，
 * 主动广播的节流沿用插件既有聚合，调用方负责）。</p>
 *
 * <p><b>msg_seq（2026-09 官方规格对齐）</b>：官方《群聊消息/单聊消息》发送接口要求被动回复携带 {@code msg_seq}，
 * 且“<b>相同的 msg_id + msg_seq 重复发送会失败</b>”（错误码 40054005 消息被去重，默认 msg_seq=1）。
 * 分页列表等场景会对<b>同一条入站消息</b>连发多条被动回复，故本类按 msg_id 维护递增序号（1,2,3…）——
 * 否则第 2 条起会被平台判重丢弃。被动回复还有次数上限（官方：群聊每条源消息最多 5 次、单聊最多 4 次），
 * 超限平台会返回 40034128；本类在超限时打出明确 WARN 便于定位“列表只发出一条”。</p>
 *
 * <p><b>发送语义（D7）</b>：尽力一次不重试（无持久化幂等，重试会造成重复通知）——例外仅两类：
 * ① 平台明确 token 失效（HTTP 401 / 业务码 11244/11242）时经 {@link TokenProvider#onAuthFailure()} 强制重换一次并
 * 重试一次（鉴权层重试，对齐方案 §4.2）；② <b>连接阶段失败</b>（{@link #isConnectPhaseFailure}：连接超时 / DNS /
 * TLS 握手 / 连接被拒——请求字节未到达平台，必然未投递）重试一次（JDK 只对 {@code ConnectException} 的幂等请求
 * 自动重试，连接超时不在其列，故此处自行兜底）。请求阶段超时（连上但无响应）结果未知，<b>不重试</b>。
 * <p>失败结果以 {@link Outcome} 区分「确定失败」与「结果未知」供上层告警（S7 聚合）：请求阶段超时意味平台可能
 * 已完成投递、仅响应迟到或丢失（2026-09 线上实测：主动消息响应可 >10s，但群里实际已收到），归为
 * {@link Outcome#UNKNOWN}——不可重试、不算平台故障（不污染健康 lastError）。凭据安全（R5）：任何日志不打 token。</p>
 */
public final class QqSender {

    /**
     * 投递结果。
     *
     * <ul>
     *   <li>{@link #SENT}：平台返回 2xx（含 token 重换后重试成功）；</li>
     *   <li>{@link #FAILED}：<b>确定失败</b>——平台返回非 2xx / token 不可用 / 连接阶段失败重试后仍失败；</li>
     *   <li>{@link #UNKNOWN}：<b>结果未知</b>——请求已发出但响应超时（平台可能已投递），不重试以免重复通知。</li>
     * </ul>
     */
    public enum Outcome {
        SENT,
        FAILED,
        UNKNOWN
    }

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /**
     * 请求超时：实测 QQ 主动消息（不带 msg_id）响应可超 10s（群里实际已收到）——过短会把已投递的消息报成失败，
     * 故给足 30s（投递是异步 fire-and-forget，等待长不影响服务器线程）。
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 连接阶段重试的等待：给中间设备/边缘节点瞬态拒绝留恢复窗口（仅一次，不做指数退避）。 */
    private static final long CONNECT_RETRY_DELAY_MS = 300;
    /** 被动回复次数上限（官方：群聊每条源消息最多 5 次、单聊 4 次；超限平台返回 40034128）。 */
    private static final int PASSIVE_REPLY_LIMIT_GROUP = 5;

    private static final int PASSIVE_REPLY_LIMIT_C2C = 4;
    /** 被动回复序号表上限（msg_id → 已用序号）：仅作防重复，条数极小，超限整体清空。 */
    private static final int REPLY_SEQ_MAX_ENTRIES = 256;

    /** msg_id → 已用被动回复序号（同一入站消息的多条回复需递增 msg_seq，否则被平台判重）。 */
    private final ConcurrentHashMap<String, AtomicInteger> replySeq = new ConcurrentHashMap<>();

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
     * @return {@link Outcome#SENT}（平台 2xx）/ {@link Outcome#FAILED}（确定失败）/ {@link Outcome#UNKNOWN}（响应超时，结果未知）
     */
    public CompletableFuture<Outcome> sendGroupMessage(String groupOpenid, String text, String replyMsgId) {
        return send("/v2/groups/" + groupOpenid + "/messages", text, replyMsgId, true);
    }

    /**
     * 发送 C2C 私聊消息。
     *
     * @param userOpenid QQ 用户 openid
     * @param text 文本内容（仅文本，D6）
     * @param replyMsgId 被动回复的来源消息 id（无则 null）
     * @return 同 {@link #sendGroupMessage}
     */
    public CompletableFuture<Outcome> sendDirectMessage(String userOpenid, String text, String replyMsgId) {
        return send("/v2/users/" + userOpenid + "/messages", text, replyMsgId, false);
    }

    private CompletableFuture<Outcome> send(String path, String text, String replyMsgId, boolean group) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        String url = apiBase + path;
        int msgSeq = nextReplySeq(path, replyMsgId, group);
        // token 获取卸载到 IM 专用线程池：tokens.fresh() 在临期/过期时会同步换发 token（阻塞 HTTP ≤15s），
        // 而本方法常由服务器线程调用（通知/命令回复）——不能在调用线程等网络（R12）。
        return CompletableFuture.supplyAsync(tokens::fresh, ImWorkerPool.executor())
                .thenCompose(firstToken -> {
                    if (firstToken == null) {
                        log.warning("[qq] 发送无可用 access_token，丢弃: " + path);
                        return CompletableFuture.completedFuture(Outcome.FAILED);
                    }
                    return postWithConnectRetry(url, firstToken, text, replyMsgId, msgSeq, path)
                            .thenCompose(resp -> {
                                String body = resp.body() == null ? "" : resp.body();
                                if (is2xx(resp)) {
                                    return CompletableFuture.completedFuture(Outcome.SENT);
                                }
                                if (QqApiClient.isTokenRejected(resp.statusCode(), body)) {
                                    // token 失效：强制重换一次并重试一次（鉴权层自愈；仍失败按投递失败告警）
                                    String freshToken = tokens.onAuthFailure();
                                    if (freshToken == null) {
                                        log.warning("[qq] token 重换失败，消息投递失败（不再重试）: " + path);
                                        return CompletableFuture.completedFuture(Outcome.FAILED);
                                    }
                                    log.info("[qq] token 失效已重换，重试一次投递: " + path);
                                    return postWithConnectRetry(url, freshToken, text, replyMsgId, msgSeq, path)
                                            .handle((resp2, ex) -> {
                                                if (ex != null) {
                                                    Throwable cause = unwrap(ex);
                                                    if (isUnknownOutcome(cause)) {
                                                        log.warning(
                                                                "[qq] 投递响应超时（结果未知：平台可能已投递，未重试）: " + path + " " + cause);
                                                        return Outcome.UNKNOWN;
                                                    }
                                                    log.warning("[qq] token 重试仍失败（"
                                                            + cause.getClass().getSimpleName() + "）: " + path + " "
                                                            + cause);
                                                    return Outcome.FAILED;
                                                }
                                                if (!is2xx(resp2)) {
                                                    log.warning(
                                                            "[qq] 重试仍失败（HTTP " + resp2.statusCode() + "），丢弃: " + path);
                                                    return Outcome.FAILED;
                                                }
                                                return Outcome.SENT;
                                            });
                                }
                                log.warning(
                                        "[qq] 投递失败（HTTP " + resp.statusCode() + "，不重试）: " + path + " " + clip(body));
                                return CompletableFuture.completedFuture(Outcome.FAILED);
                            })
                            .exceptionally(ex -> {
                                Throwable cause = unwrap(ex);
                                if (isConnectPhaseFailure(cause)) {
                                    log.warning("[qq] 投递失败（连接阶段异常，已重试一次）: " + path + " " + cause);
                                    return Outcome.FAILED;
                                }
                                if (isUnknownOutcome(cause)) {
                                    // 请求已发出但无响应（或总预算耗尽）：结果未知（线下实测平台多半已投递），禁止重试以免重复通知
                                    log.warning("[qq] 投递响应超时（结果未知：平台可能已投递，未重试）: " + path + " " + cause);
                                    return Outcome.UNKNOWN;
                                }
                                log.warning("[qq] 投递网络异常（不重试）: " + path + " " + cause);
                                return Outcome.FAILED;
                            });
                });
    }

    /**
     * 是否为「结果未知」：请求已发出但拿不到响应（{@link java.net.http.HttpTimeoutException} 请求阶段超时），
     * 或外层总预算看门狗超时（{@link java.util.concurrent.TimeoutException}）。
     *
     * <p>该结果<strong>不可重试</strong>（平台可能已投递，重试会造成重复通知），也不应计为平台故障；仅凭告警由服主
     * 核对群内是否收到。有意排除 {@link java.net.http.HttpConnectTimeoutException}（连接阶段，属确定未发出）。</p>
     */
    static boolean isUnknownOutcome(Throwable cause) {
        if (cause instanceof java.net.http.HttpConnectTimeoutException) {
            return false; // 连接阶段超时（HttpTimeoutException 子类）：请求未发出，属确定失败
        }
        return cause instanceof java.net.http.HttpTimeoutException
                || cause instanceof java.util.concurrent.TimeoutException;
    }

    /**
     * POST + 连接阶段失败兑底重试（仅一次）。
     *
     * <p>连接阶段失败意味着请求字节未到达平台（TCP/TLS 未完成或连接被拒），重试不会造成重复投递；请求阶段
     * 失败（含 {@link java.net.http.HttpTimeoutException}）直接上抛，交上层按「结果未知」告警且不重试。</p>
     */
    private CompletableFuture<HttpResponse<String>> postWithConnectRetry(
            String url, String token, String text, String replyMsgId, int msgSeq, String path) {
        return post(url, token, text, replyMsgId, msgSeq)
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
                            .thenCompose(ignored -> post(url, token, text, replyMsgId, msgSeq));
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

    private CompletableFuture<HttpResponse<String>> post(
            String url, String token, String text, String replyMsgId, int msgSeq) {
        JsonObject body = new JsonObject();
        body.addProperty("content", text);
        body.addProperty("msg_type", 0);
        if (replyMsgId != null && !replyMsgId.isBlank()) {
            body.addProperty("msg_id", replyMsgId); // 被动回复通道（D14）
            body.addProperty("msg_seq", msgSeq); // 必需：同一 msg_id 重复序号会被平台判重（40054005）
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
