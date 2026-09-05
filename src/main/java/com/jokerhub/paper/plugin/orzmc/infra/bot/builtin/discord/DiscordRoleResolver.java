package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Discord 群管理角色判定（builtin DC adapter，批次 5b；对照 TG TelegramRoleResolver / 飞书 FeishuRoleResolver 模式）。
 *
 * <p>群聊（{@code group}）：REST 判定发送者是否为群管理——<b>群主（guild.owner_id）或成员所持角色含
 * ADMINISTRATOR / MANAGE_GUILD 权限位</b>。DM 恒非管理（对齐跨平台语义——admin_dm 绑定私聊仅通知/非管理命令）。</p>
 *
 * <p>缓存（60s TTL，权限变更低频；容量上限粗暴全清）：群主/角色权限表按 guild 缓存，成员角色按
 * {@code guild:user} 缓存，判定结果按 {@code guild:user} 缓存；查询并发单飞（同 key 只发起一次请求）。
 * 任何查询失败按非管理处理（fail-closed）。</p>
 */
public final class DiscordRoleResolver implements DiscordAdminResolver {

    private static final long CACHE_TTL_MS = 60_000;
    private static final int CACHE_MAX = 10_000;

    private final Logger log;
    private final DiscordApiClient api;
    private final ConcurrentHashMap<String, CacheValue<?>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inflight = new ConcurrentHashMap<>();

    public DiscordRoleResolver(Logger log, DiscordApiClient api) {
        if (log == null || api == null) {
            throw new IllegalArgumentException("log/api must not be null");
        }
        this.log = log;
        this.api = api;
    }

    /** 是否管理员（异步：API 调用在自有线程执行，不阻塞服务器线程 R12）。 */
    @Override
    public CompletableFuture<Boolean> isAdmin(DiscordInboundMessage message) {
        if (message == null || !DiscordInboundMessage.CHAT_TYPE_GROUP.equals(message.chatType())) {
            return CompletableFuture.completedFuture(false); // DM 无角色 → 非管理（跨平台一致）
        }
        String guildId = message.guildId();
        if (guildId == null || guildId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        String resultKey = guildId + ":" + message.senderId();
        Boolean cached = getCached(resultKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        // 并发单飞：同 key 只发起一次完整判定（进行中 → 复用同一 future）
        CompletableFuture<Boolean> existing = inflight.get(resultKey);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Boolean> task =
                CompletableFuture.supplyAsync(() -> evaluate(resultKey, guildId, message.senderId()));
        CompletableFuture<Boolean> raced = inflight.putIfAbsent(resultKey, task);
        if (raced != null) {
            return raced;
        }
        task.whenComplete((admin, error) -> inflight.remove(resultKey, task));
        return task;
    }

    /** 服务器线程外执行完整判定（可能多次 REST；任一查询失败 → 非管理 fail-closed）。 */
    private boolean evaluate(String resultKey, String guildId, String userId) {
        try {
            // 1) 群主判定（owner 按 guild 缓存）
            String owner = getOrLoad(guildId + ":owner", () -> api.getGuildOwner(guildId));
            if (owner != null && owner.equals(userId)) {
                putCached(resultKey, true);
                return true;
            }
            // 2) 成员角色列表（查询失败 → 非管理）
            List<String> memberRoles =
                    getOrLoad(guildId + ":roles:" + userId, () -> api.getGuildMemberRoles(guildId, userId));
            if (memberRoles == null || memberRoles.isEmpty()) {
                putCached(resultKey, false);
                return false;
            }
            // 3) 群角色权限表（查询失败 → 无法判定 → 非管理）
            Map<String, BigInteger> rolePerms =
                    getOrLoad(guildId + ":roleperms", () -> api.getGuildRolesPermissions(guildId));
            if (rolePerms == null) {
                putCached(resultKey, false);
                return false;
            }
            boolean admin = false;
            for (String roleId : memberRoles) {
                BigInteger perms = rolePerms.get(roleId);
                if (perms != null && (perms.testBit(3) || perms.testBit(5))) { // ADMINISTRATOR / MANAGE_GUILD
                    admin = true;
                    break;
                }
            }
            putCached(resultKey, admin);
            return admin;
        } catch (RuntimeException e) {
            log.warning("[discord] 角色判定异常: " + e);
            return false;
        }
    }

    // =====================================================================
    // 缓存工具（键前缀区分；失败结果不缓存——null 即时重查）
    // =====================================================================

    @SuppressWarnings("unchecked")
    private <T> T getOrLoad(String key, Supplier<T> loader) {
        CacheValue<?> hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.queriedMs < CACHE_TTL_MS) {
            return (T) hit.value;
        }
        T value = loader.get();
        if (value != null) {
            putCached(key, value);
        }
        return value;
    }

    private void putCached(String key, Object value) {
        if (cache.size() >= CACHE_MAX) {
            cache.clear(); // 防缓存无限增长（低频场景粗暴清理即可）
        }
        cache.put(key, new CacheValue<>(value, System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    private Boolean getCached(String key) {
        CacheValue<?> hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.queriedMs < CACHE_TTL_MS) {
            return (Boolean) hit.value;
        }
        return null;
    }

    private record CacheValue<T>(T value, long queriedMs) {}
}
