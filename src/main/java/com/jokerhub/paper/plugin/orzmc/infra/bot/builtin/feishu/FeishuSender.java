package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * 飞书开放平台下行消息发送（builtin 飞书 adapter，方案 §6 / D7；协议参照 EasyBot lib.rs send）。
 *
 * <p>统一目标通道 {@code POST {apiBase}/im/v1/messages?receive_id_type=chat_id}（群聊/单聊的 chat_id 均走
 * 同一端点，receive_id 即 chat_id）；文本请求体 {@code {"receive_id": chat_id, "msg_type": "text",
 * "content": "{\"text\":...}"}}——content 为 <b>JSON 字符串</b>（易 Bot 亦如此，勿双编码）。</p>
 *
 * <p><b>发送语义（D7）</b>：尽力一次不重试（无持久化幂等，重试会造成重复通知）——仅当平台明确 token 失效
 * （HTTP 401 / 业务码 99991663 等）时经 {@link TokenProvider#onAuthFailure()} 强制重换一次并重试一次
 * （鉴权层重试，对齐方案 §4.2「即时重换 + 重试一次」，非投递层重试）；失败结果返回 false 供健康告警。
 * 凭据安全（R5）：任何日志不打 token/secret。</p>
 */
public final class FeishuSender {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final Logger log;
    private final TokenProvider tokens;
    private final String apiBase;
    private final java.net.Proxy proxy;

    public FeishuSender(Logger log, TokenProvider tokens) {
        this(log, tokens, FeishuApiClient.DEFAULT_API_BASE, java.net.Proxy.NO_PROXY);
    }

    public FeishuSender(Logger log, TokenProvider tokens, String apiBase) {
        this(log, tokens, apiBase, java.net.Proxy.NO_PROXY);
    }

    /** 便捷：默认端点 + 指定代理（海外/受限网络经 HTTP 代理回国访问飞书 API，D13）。 */
    public FeishuSender(Logger log, TokenProvider tokens, java.net.Proxy proxy) {
        this(log, tokens, FeishuApiClient.DEFAULT_API_BASE, proxy);
    }

    public FeishuSender(Logger log, TokenProvider tokens, String apiBase, java.net.Proxy proxy) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        this.log = log;
        this.tokens = tokens;
        this.apiBase = apiBase == null || apiBase.isBlank() ? FeishuApiClient.DEFAULT_API_BASE : apiBase;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
    }

    /**
     * 发送文本消息到指定会话。
     *
     * @param chatId 群聊/单聊 chat_id（与入站事件 message.chat_id 同值）
     * @param text 文本内容（仅文本，D6）
     * @return 发送成功 true；失败 false（含 token 重换后仍被拒 / 非 2xx / 网络错误），调用方健康告警
     */
    public CompletableFuture<Boolean> sendMessage(String chatId, String text) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        String url = apiBase + "/im/v1/messages?receive_id_type=chat_id";
        String firstToken = tokens.fresh();
        if (firstToken == null) {
            log.warning("[feishu] 发送无可用 tenant token，丢弃: " + url);
            return CompletableFuture.completedFuture(false);
        }
        return post(url, firstToken, chatId, text)
                .thenCompose(resp -> {
                    String body = resp.body() == null ? "" : resp.body();
                    // 飞书 API 恒 HTTP 200 + 业务 code：2xx 且 body 无业务错误（code!=0）才算成功
                    if (isSuccess(resp, body)) {
                        return CompletableFuture.completedFuture(true);
                    }
                    if (FeishuApiClient.isTokenRejected(resp.statusCode(), body)) {
                        // token 失效：强制重换一次并重试一次（鉴权层自愈；仍失败按投递失败告警）
                        String freshToken = tokens.onAuthFailure();
                        if (freshToken == null) {
                            log.warning("[feishu] token 重换失败，消息投递失败（不再重试）: " + chatId);
                            return CompletableFuture.completedFuture(false);
                        }
                        log.info("[feishu] token 失效已重换，重试一次投递: " + chatId);
                        return post(url, freshToken, chatId, text).thenApply(resp2 -> {
                            String body2 = resp2.body() == null ? "" : resp2.body();
                            if (!isSuccess(resp2, body2)) {
                                log.warning("[feishu] 重试仍失败（HTTP " + resp2.statusCode() + "），丢弃: " + chatId + " "
                                        + clip(body2));
                                return false;
                            }
                            return true;
                        });
                    }
                    log.warning("[feishu] 投递失败（HTTP " + resp.statusCode() + "，不重试）: " + chatId + " " + clip(body));
                    return CompletableFuture.completedFuture(false);
                })
                .exceptionally(ex -> {
                    log.warning("[feishu] 投递网络异常（不重试）: " + chatId + " " + unwrap(ex));
                    return false;
                });
    }

    private CompletableFuture<HttpResponse<String>> post(String url, String token, String chatId, String text) {
        JsonObject content = new JsonObject();
        content.addProperty("text", text);
        JsonObject body = new JsonObject();
        body.addProperty("receive_id", chatId);
        body.addProperty("msg_type", "text");
        body.addProperty("content", content.toString()); // content 是 JSON 字符串，勿双编码
        return AsyncHttp.postJson(
                url,
                body.toString(),
                Map.of("Authorization", "Bearer " + token),
                CONNECT_TIMEOUT,
                REQUEST_TIMEOUT,
                0,
                proxy);
    }

    /** 飞书成功判定：HTTP 2xx 且 body 无业务错误（{@code code} 缺失或为 0）。 */
    private static boolean isSuccess(HttpResponse<String> resp, String body) {
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return false;
        }
        try {
            if (body != null && body.contains("\"code\"")) {
                return JsonParser.parseString(body)
                                .getAsJsonObject()
                                .get("code")
                                .getAsInt()
                        == 0;
            }
            return true; // 无 code 字段（理论不发生）按成功处理
        } catch (RuntimeException e) {
            return false; // 响应非 JSON：按失败处理
        }
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
