package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 飞书群角色判定（方案 §6；实现 {@link FeishuAdminResolver}）。
 *
 * <p>飞书消息事件不含发送者角色（区别于 QQ），须查 {@code GET /im/v1/chats/{chat_id}}（owner_id +
 * user_manager_id_list）判定。语义（fail-closed，对齐 AGENTS「判断不了即降级为非管理」）：</p>
 * <ul>
 *   <li><b>单聊（chatType=user）恒非管理</b>（管理指令仅群内，与 QQ/EasyBot 语义一致）；</li>
 *   <li><b>缓存</b>：key={@code chatId:senderId}，TTL 30s（EasyBot ROLE_CACHE_TTL_SECS=30）；
 *       容量上限 10_000（ROLE_CACHE_LIMIT），超限清空防无界增长；</li>
 *   <li><b>并发单飞</b>：同 key 并发只发一次 API（inflight map 去重，完成后清除）；</li>
 *   <li><b>失败降级</b>：token 失效经 onAuthFailure 重换一次（方案 §4.2）；仍失败/网络异常 → 非管理
 *       （fail-closed 不刷屏，缓存负结果由 TTL 自然过期）；</li>
 *   <li><b>线程纪律</b>：查询在 {@link CompletableFuture#supplyAsync} 后台线程执行（AsyncHttp join），
 *       绝不阻塞 WS 回调线程 / 服务器线程（调用方 = FeishuInboundProcessor 编排）。</li>
 * </ul>
 */
public final class FeishuRoleResolver implements FeishuAdminResolver {

    /** 角色缓存 TTL（毫秒，对齐 EasyBot ROLE_CACHE_TTL_SECS=30）。 */
    private static final long CACHE_TTL_MS = 30_000;
    /** 角色缓存容量上限（对齐 EasyBot ROLE_CACHE_LIMIT=10_000，防无界增长）。 */
    private static final int CACHE_MAX = 10_000;

    private final Logger log;
    private final FeishuApiClient api;
    private final TokenProvider tokens;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> inflight = new ConcurrentHashMap<>();

    public FeishuRoleResolver(Logger log, FeishuApiClient api, TokenProvider tokens) {
        if (log == null || api == null || tokens == null) {
            throw new IllegalArgumentException("log/api/tokens must not be null");
        }
        this.log = log;
        this.api = api;
        this.tokens = tokens;
    }

    @Override
    public CompletableFuture<Boolean> isAdmin(FeishuInboundMessage message) {
        if (message == null || !FeishuInboundMessage.CHAT_TYPE_GROUP.equals(message.chatType())) {
            return CompletableFuture.completedFuture(false); // 单聊无角色 → 非管理
        }
        String key = message.chatId() + ":" + message.senderId();
        CacheEntry hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.queriedMs < CACHE_TTL_MS) {
            return CompletableFuture.completedFuture(hit.admin);
        }
        // 并发单飞：同 key 只发起一次查询（进行中 → 复用同一 future）
        CompletableFuture<Boolean> existing = inflight.get(key);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Boolean> task = CompletableFuture.supplyAsync(() -> queryAndReturn(key, message));
        CompletableFuture<Boolean> raced = inflight.putIfAbsent(key, task);
        if (raced != null) {
            return raced;
        }
        task.whenComplete((admin, error) -> inflight.remove(key, task));
        return task;
    }

    /** 后台线程同步查询角色；token 失效重换一次；失败 → 非管理。 */
    private boolean queryAndReturn(String key, FeishuInboundMessage message) {
        try {
            String token = tokens.fresh();
            if (token == null) {
                return false;
            }
            FeishuApiClient.ChatRoles roles = api.fetchChatRoles(message.chatId(), token);
            if (roles == null) {
                // token 可能失效：重换一次再查（方案 §4.2 即时重换语义）；仍失败按非管理
                String fresh = tokens.onAuthFailure();
                if (fresh != null && !fresh.equals(token)) {
                    roles = api.fetchChatRoles(message.chatId(), fresh);
                }
            }
            boolean admin = roles != null && roles.isOwnerOrManager(message.senderId());
            put(key, admin);
            return admin;
        } catch (RuntimeException e) {
            log.warning("[feishu] 角色查询异常（按非管理降级）: " + e);
            return false;
        }
    }

    private void put(String key, boolean admin) {
        if (cache.size() >= CACHE_MAX) {
            cache.clear(); // 超限清空（EasyBot 近似 FIFO 淘汰；飞书角色查询低频，可接受）
        }
        cache.put(key, new CacheEntry(admin, System.currentTimeMillis()));
    }

    /** 测试钩子：清空缓存（进行中查询不受影响，由 whenComplete 自清）。 */
    void clearCache() {
        cache.clear();
    }

    private record CacheEntry(boolean admin, long queriedMs) {}
}
