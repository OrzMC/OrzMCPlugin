package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

/**
 * LuckPerms 权限执行器（直接 LP API，类型安全）。
 *
 * <p>权限链（track "rank"）：default → member → builder → admin。
 * 升降级调用 LP track 的 {@code promote}/{@code demote}——LP 原生钳位语义：
 * 链顶再 promote 返回 {@code END_OF_TRACK}（不绕回），链底再 demote 返回
 * {@code REMOVED_FROM_FIRST_GROUP}/{@code NOT_ON_TRACK}。</p>
 *
 * <p><b>软依赖加载约束</b>：本类直接引用 {@code net.luckperms.api} 类型，
 * <b>仅在 LP 已启用时由装配层实例化</b>（见 FeatureModule 的条件实例化）——
 * LP 未安装时本类永远不会被加载，因此不会触发 NoClassDefFoundError。
 * 装配层在 LP 缺失时改用 {@link NoopRankPromoter} 降级。</p>
 *
 * <p>⚠️ LP API 修改用户是<b>内存态</b>，成功后须显式 {@code saveUser} 落库；
 * 变更须在主线程执行（本类经注入 scheduler 回主线程，无 scheduler 时同步）。</p>
 */
public final class LuckPermsPromoter implements RankPromoter {

    public static final String TRACK = "rank";
    private static final String LUCKPERMS_PLUGIN = "LuckPerms";
    private static final long LOAD_USER_TIMEOUT_SECONDS = 3;
    private static final Logger LOG = Logger.getLogger("OrzMC.LP");

    private final PlayerNameResolver nameResolver;
    private final ServerScheduler scheduler;

    public LuckPermsPromoter(PlayerNameResolver nameResolver) {
        this(nameResolver, null);
    }

    public LuckPermsPromoter(PlayerNameResolver nameResolver, ServerScheduler scheduler) {
        this.nameResolver = nameResolver;
        this.scheduler = scheduler;
    }

    /** LuckPerms 是否已启用（软依赖检测）。 */
    public boolean isLuckPermsEnabled() {
        PluginManager pm = Bukkit.getPluginManager();
        return pm != null && pm.isPluginEnabled(LUCKPERMS_PLUGIN);
    }

    @Override
    public boolean isAvailable() {
        return isLuckPermsEnabled();
    }

    // ---- LP API 访问 ----

    private LuckPerms api() {
        return LuckPermsProvider.get();
    }

    private TrackManager trackManager() {
        return api().getTrackManager();
    }

    private Track track() {
        return trackManager().getTrack(TRACK);
    }

    /** 加载用户（在线缓存同步取，离线异步加载并等待）。 */
    private User loadUser(UUID playerId) {
        UserManager um = api().getUserManager();
        User user = um.getUser(playerId);
        if (user != null) {
            return user;
        }
        try {
            CompletableFuture<User> future = um.loadUser(playerId);
            return future.get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warning("loadUser(" + playerId + ") 失败: " + e);
            return null;
        }
    }

    /**
     * OrzMC 权限操作统一上下文：global（空上下文）。
     *
     * <p>LP track 节点必须创建/查询在 global 上下文，否则玩家在线时（world/gamemode
     * 等上下文）$p 升降级会把节点写到带上下文的场景下，与离线操作（global）的节点
     * 混存 → track 节点重叠、currentTrackGroup 误判、promote/demote 报
     * AMBIGUOUS_CALL（实测 joker/TestMember 均踩中）。统一 global 后所有场景一致。</p>
     */
    private static ImmutableContextSet globalContext() {
        return ImmutableContextSet.empty();
    }

    /** 持久化用户变更（LP API 的 promote/demote 只改内存，须显式保存）。@return true=落库成功。 */
    private boolean saveUser(User user) {
        try {
            api().getUserManager().saveUser(user).get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.warning("saveUser 失败: " + e);
            return false;
        }
    }

    // ---- 接口实现 ----

    @Override
    public UUID resolvePlayerId(String playerName) {
        org.bukkit.OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return p.hasPlayedBefore() ? p.getUniqueId() : null;
    }

    @Override
    public Optional<String> playerName(UUID playerId) {
        return Optional.ofNullable(nameResolver.resolve(playerId));
    }

    @Override
    public boolean isInGroup(UUID playerId, String groupName) {
        User user = loadUser(playerId);
        if (user == null) {
            return false;
        }
        return user.getInheritedGroups(queryOptionsGlobal()).stream()
                .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
    }

    /** global 上下文的查询选项（track 判定/组查询统一用，见 GLOBAL 说明）。 */
    private static QueryOptions queryOptionsGlobal() {
        return QueryOptions.builder(net.luckperms.api.query.QueryMode.CONTEXTUAL)
                .context(globalContext())
                .build();
    }

    /**
     * 操作前归一键：清除玩家全部继承节点（含带 world/gamemode 上下文的脏节点），
     * 仅保留 global 上下文当前 track 组——根治历史多组残留导致的
     * AMBIGUOUS_CALL 与组节点累积（2026-08-08 joker 案例）。
     */
    private boolean normalizeSingleGroup(User user, Track trk) {
        var inherited = user.getInheritedGroups(queryOptionsGlobal()).stream()
                .map(g -> g.getName())
                .collect(java.util.stream.Collectors.toCollection(
                        () -> new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        String current = null;
        for (String group : trk.getGroups()) {
            if (inherited.contains(group)) {
                current = group;
            }
        }
        user.data().clear(node -> node instanceof InheritanceNode);
        if (current != null) {
            user.data()
                    .add(api().getNodeBuilderRegistry()
                            .forInheritance()
                            .group(current)
                            .build());
        }
        return saveUser(user);
    }

    @Override
    public String currentTrackGroup(UUID playerId) {
        User user = loadUser(playerId);
        Track trk = track();
        if (user == null || trk == null) {
            return null;
        }
        // 一次加载用户继承组集合（global 上下文），避免对每个 track 组重复 loadUser（N+1，离线玩家每次 3s 超时 × N）
        // 注：LP API 无 TrackNode 概念，track 组即普通继承节点——本方法按「继承组 ∩ track 组列表」
        // 取最高位。只认 global 上下文节点（统一见 GLOBAL 说明），带世界/游戏模式上下文的同名组
        // 不参与判定（它们是一期在玩家上下文下操作产生的脏数据，应清理）。
        var inherited = user.getInheritedGroups(queryOptionsGlobal()).stream()
                .map(g -> g.getName())
                .collect(java.util.stream.Collectors.toCollection(
                        () -> new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        // track 顺序：default→member→builder→admin；取玩家在 track 中位置最高的组
        String found = null;
        for (String group : trk.getGroups()) {
            if (inherited.contains(group)) {
                found = group;
            }
        }
        return found;
    }

    @Override
    public String promote(UUID playerId) {
        User user = loadUser(playerId);
        Track trk = track();
        if (user == null || trk == null) {
            LOG.warning("promote 跳过: user=" + (user != null) + " track=" + (trk != null));
            return null;
        }
        // 操作前归一键：清理历史多组残留（含带上下文的脏节点），仅保留 global 当前组，
        // 根治 LP track 操作在多组状态下的 AMBIGUOUS_CALL 与组节点累积
        if (!normalizeSingleGroup(user, trk)) {
            LOG.warning("promote(" + playerId + ") 归一组失败，视为失败");
            return null;
        }
        PromotionResult result = runSync(() -> trk.promote(user, globalContext()));
        if (result == null) {
            LOG.warning("promote(" + playerId + ") 结果为 null");
            return null;
        }
        String status = result.getStatus().name();
        boolean success = result.getStatus() == PromotionResult.Status.SUCCESS
                || result.getStatus() == PromotionResult.Status.ADDED_TO_FIRST_GROUP;
        LOG.info("promote(" + playerId + ") -> " + status);
        if (result.getStatus() == PromotionResult.Status.AMBIGUOUS_CALL) {
            LOG.warning("promote(" + playerId + ") track 节点歧义（存在多个 track 组节点），"
                    + "请用 lp user <name> parent info 检查并清理重叠/带上下文的 track 组");
        }
        if (!success) {
            return null; // END_OF_TRACK / AMBIGUOUS_CALL 等
        }
        String groupTo = result.getGroupTo().orElse(null);
        if (result.getStatus() == PromotionResult.Status.ADDED_TO_FIRST_GROUP
                && groupTo != null
                && !trk.getGroups().isEmpty()
                && groupTo.equalsIgnoreCase(trk.getGroups().get(0))) {
            // 用户此前不在 track：被加到链首（default），继续 promote 到下一级（member），
            // 避免「升级为访客」的误导（升级至少到 member）
            PromotionResult second = runSync(() -> trk.promote(user, globalContext()));
            if (second != null && second.getStatus() == PromotionResult.Status.SUCCESS) {
                groupTo = second.getGroupTo().orElse(groupTo);
                LOG.info("promote(" + playerId + ") 首入链（链首）→ 连续 promote -> "
                        + second.getStatus().name());
            }
        }
        if (!saveUser(user)) {
            LOG.warning("promote(" + playerId + ") 落库失败，视为失败"); // 内存已改但未持久化，不能报成功
            return null;
        }
        return groupTo;
    }

    @Override
    public String demote(UUID playerId) {
        User user = loadUser(playerId);
        Track trk = track();
        if (user == null || trk == null) {
            LOG.warning("demote 跳过: user=" + (user != null) + " track=" + (trk != null));
            return null;
        }
        // 操作前归一键（同 promote，见 normalizeSingleGroup 注释）
        if (!normalizeSingleGroup(user, trk)) {
            LOG.warning("demote(" + playerId + ") 归一组失败，视为失败");
            return null;
        }
        DemotionResult result = runSync(() -> trk.demote(user, globalContext()));
        if (result == null) {
            LOG.warning("demote(" + playerId + ") 结果为 null");
            return null;
        }
        String status = result.getStatus().name();
        boolean success = result.getStatus() == DemotionResult.Status.SUCCESS;
        LOG.info("demote(" + playerId + ") -> " + status);
        if (result.getStatus() == DemotionResult.Status.AMBIGUOUS_CALL) {
            LOG.warning("demote(" + playerId + ") track 节点歧义（存在多个 track 组节点），"
                    + "请用 lp user <name> parent info 检查并清理重叠/带上下文的 track 组");
        }
        if (!success) {
            return null; // REMOVED_FROM_FIRST_GROUP / NOT_ON_TRACK / AMBIGUOUS_CALL
        }
        if (!saveUser(user)) {
            LOG.warning("demote(" + playerId + ") 落库失败，视为失败"); // 内存已改但未持久化，不能报成功
            return null;
        }
        return result.getGroupTo().orElse(null);
    }

    /** 在主线程执行 LP 变更（异步线程时经 scheduler 回主线程）。 */
    private <T> T runSync(Supplier<T> action) {
        if (Bukkit.isPrimaryThread() || scheduler == null) {
            return action.get();
        }
        CompletableFuture<T> done = new CompletableFuture<>();
        scheduler.runSync(() -> {
            try {
                done.complete(action.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done.join();
    }
}
