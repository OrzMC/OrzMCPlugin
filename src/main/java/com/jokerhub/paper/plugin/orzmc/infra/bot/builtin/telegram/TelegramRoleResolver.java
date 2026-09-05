package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Telegram 群管理角色判定（builtin TG adapter，批次 5a；对照飞书 FeishuRoleResolver 模式）。
 *
 * <p>群聊（{@code group}）：查 {@code getChatAdministrators}（creator/administrator → 管理员，带 TTL 缓存 +
 * 并发单飞）；私聊（{@code user}）恒非管理（对齐 QQ/飞书跨平台语义——admin_dm 绑定私聊仅通知/非管理命令）。</p>
 */
public final class TelegramRoleResolver implements TelegramAdminResolver {

    /** 角色缓存 TTL（getChatAdministrators 结果缓存时长；TG 管理员变更低频）。 */
    private static final long CACHE_TTL_MS = 60_000;

    private static final int CACHE_MAX = 10_000;

    private final Logger log;
    private final TelegramApiClient api;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inflight = new ConcurrentHashMap<>();

    public TelegramRoleResolver(Logger log, TelegramApiClient api) {
        if (log == null || api == null) {
            throw new IllegalArgumentException("log/api must not be null");
        }
        this.log = log;
        this.api = api;
    }

    /** 是否管理员（异步：API 调用在自有线程执行，不阻塞服务器线程）。 */
    public CompletableFuture<Boolean> isAdmin(TelegramInboundMessage message) {
        if (message == null || !TelegramInboundMessage.CHAT_TYPE_GROUP.equals(message.chatType())) {
            return CompletableFuture.completedFuture(false); // 私聊无角色 → 非管理（跨平台一致）
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

    private boolean queryAndReturn(String key, TelegramInboundMessage message) {
        try {
            List<Long> admins = api.getChatAdministrators(message.chatId());
            if (admins == null) {
                return false; // 查询失败（非群/无权限/网络）按非管理处理
            }
            boolean admin = admins.contains(message.senderId());
            if (cache.size() >= CACHE_MAX) {
                cache.clear(); // 防缓存无限增长（低频场景粗暴清理即可）
            }
            cache.put(key, new CacheEntry(admin, System.currentTimeMillis()));
            return admin;
        } catch (RuntimeException e) {
            log.warning("[telegram] 角色查询异常: " + e);
            return false;
        }
    }

    private record CacheEntry(boolean admin, long queriedMs) {}
}
