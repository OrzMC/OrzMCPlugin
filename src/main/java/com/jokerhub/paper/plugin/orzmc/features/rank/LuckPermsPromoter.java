package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
 * <p>⚠️ LP API 修改用户是<b>内存态</b>，成功后须显式 {@code saveUser} 落库。</p>
 *
 * <p><b>线程模型（核心约束）</b>：LP 的 loadUser/saveUser 异步 future 完成回调调度到
 * 服务器同步调度线程（Paper 主线程 / Folia global region 线程）执行——因此<b>任何服务器
 * 调度线程都不能同步等待 LP future</b>（回调排在自己后面 → 自锁超时，/review approve 实测）。
 * 升降级（{@link #promoteAsync} / {@link #demoteAsync}）在注入的 {@code asyncExecutor}
 * （非服务器线程）上执行 LP 操作；无执行器时内联（测试/无阻塞场景）。读路径
 * （{@link #currentTrackGroup}）在线玩家先取 LP 在线缓存（零 future 等待），离线玩家
 * 在异步执行器加载并在调用线程等待——但<b>任何服务器调度线程（Paper 主线程 / Folia
 * global 或 region 线程）在离线缓存未命中时都降级返回 null 避免自锁</b>（详见
 * {@link #isServerTickThread()}；Folia region 线程此前漏判会同步等 LP future 阻塞区域 tick）。
 */
public final class LuckPermsPromoter implements RankPromoter {

    public static final String TRACK = "rank";
    private static final String LUCKPERMS_PLUGIN = "LuckPerms";
    private static final long LOAD_USER_TIMEOUT_SECONDS = 3;
    private static final Logger LOG = Logger.getLogger("OrzMC.LP");

    /**
     * Folia region 线程判定方法（Folia API 独有，paper-api 编译期不可见 → 反射缓存；
     * Paper 上无此方法为 null）。服务器调度线程判定用 {@link #isRegionTickThread()} 补充。
     */
    private static final Method REGION_THREAD_CHECK = resolveRegionThreadCheckMethod();

    private static Method resolveRegionThreadCheckMethod() {
        try {
            return org.bukkit.Bukkit.class.getMethod("isRegionOwnedByCurrentThread");
        } catch (NoSuchMethodException e) {
            return null; // Paper：无 Folia 独有方法，当前线程不可能是 region 线程
        }
    }

    private final PlayerNameResolver nameResolver;
    private final ServerScheduler scheduler;
    private final Executor asyncExecutor;
    /**
     * 坐牢（prison）判定端口（可空）：非 null 时升降级开头先查——坐牢玩家拒绝升降级，
     * 防 normalizeSingleGroup 清掉 prison 节点造成「坐牢玩家被放回四级」漂移。
     * 装配层传 {@code prisonGateway::isPrisoner}（LP 软依赖条件实例化，见 FeatureModule）。
     */
    private final Predicate<UUID> prisonCheck;

    public LuckPermsPromoter(PlayerNameResolver nameResolver) {
        this(nameResolver, null, null, null);
    }

    public LuckPermsPromoter(PlayerNameResolver nameResolver, ServerScheduler scheduler) {
        this(nameResolver, scheduler, null, null);
    }

    /**
     * @param scheduler    回同步调度线程的执行器（仅 {@link #resolvePlayerId} 用；可 null）
     * @param asyncExecutor 非服务器线程执行器（升降级 LP 操作在此运行；null 时内联，测试用）
     */
    public LuckPermsPromoter(PlayerNameResolver nameResolver, ServerScheduler scheduler, Executor asyncExecutor) {
        this(nameResolver, scheduler, asyncExecutor, null);
    }

    /**
     * @param scheduler     回同步调度线程的执行器（仅 {@link #resolvePlayerId} 用；可 null）
     * @param asyncExecutor 非服务器线程执行器（升降级 LP 操作在此运行；null 时内联，测试用）
     * @param prisonCheck   坐牢判定端口（可 null；非 null 时坐牢玩家拒绝升降级）
     */
    public LuckPermsPromoter(
            PlayerNameResolver nameResolver,
            ServerScheduler scheduler,
            Executor asyncExecutor,
            Predicate<UUID> prisonCheck) {
        this.nameResolver = nameResolver;
        this.scheduler = scheduler;
        this.asyncExecutor = asyncExecutor;
        this.prisonCheck = prisonCheck;
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

    /**
     * 当前线程是否为服务器调度线程（Paper 主线程 / Folia global 或 region 线程）。
     *
     * <p>服务器调度线程<b>绝不能同步等待 LP 异步 future</b>（回调排队在自己后面 → 自锁超时）：
     * Paper 主线程与 Folia global region 线程由 {@link Bukkit#isGlobalTickThread()} 判定；
     * Folia 的 region 线程（每个区块区域独立线程，命令/事件多跑在此）还需
     * {@link #isRegionTickThread()} 判定——只判 global 会让 {@code /rank <离线玩家>} 等
     * region 线程调用在离线缓存未命中时同步等 LP future，卡住该 region 内所有玩家 tick。</p>
     */
    static boolean isServerTickThread() {
        return Bukkit.isGlobalTickThread() || isRegionTickThread();
    }

    /** Folia region 线程判定：反射调 Folia API 独有方法 {@code Bukkit.isRegionOwnedByCurrentThread()}；Paper 无此方法返回 false。 */
    static boolean isRegionTickThread() {
        if (REGION_THREAD_CHECK == null) {
            return false;
        }
        try {
            return (boolean) REGION_THREAD_CHECK.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            return false; // 反射异常一律视为非 region 线程（Paper 路径/防御性降级）
        }
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
        // Folia 兼容：Bukkit.getOfflinePlayer(name) 在 Folia 下部分版本异步调用可能抛
        // IllegalStateException，转 global region 线程执行；超时/不可用时返回 null（找不到玩家）。
        return runSync(() -> {
            org.bukkit.OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
            return p.hasPlayedBefore() ? p.getUniqueId() : null;
        });
    }

    @Override
    public Optional<String> playerName(UUID playerId) {
        return Optional.ofNullable(nameResolver.resolve(playerId));
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
        // 读路径优化：在线玩家先取 LP 在线缓存（getUser 不阻塞、不产生 LP future），
        // 常见在线场景（$v l / /apply 资格预检 / checkPromotion / 上下线广播）零往返。
        User online = api().getUserManager().getUser(playerId);
        if (online != null) {
            return resolveTrackGroup(online);
        }
        // 离线玩家缓存未命中：需 loadUser（LP 异步 future，完成回调调度到服务器同步线程）。
        // 若在服务器调度线程（Paper 主线程 / Folia global 或 region 线程）上同步等待会自锁
        // （回调排在自己后面）→ 检测到任何调度线程时降级返回 null；
        // 否则在异步执行器上加载并在当前线程等待（服务器线程空闲，LP 回调可正常执行）。
        if (isServerTickThread()) {
            LOG.warning("currentTrackGroup 在服务器调度线程（global/region）上查询离线玩家（缓存未命中），跳过避免自锁: " + playerId);
            return null;
        }
        try {
            return supplyAsync(() -> {
                        User user = loadUser(playerId);
                        if (user == null) {
                            return null;
                        }
                        return resolveTrackGroup(user);
                    })
                    .get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warning("currentTrackGroup 离线加载失败（超时/中断/异常）: " + playerId + " - " + e);
            return null;
        }
    }

    /** 从已加载用户解析其在 rank track 的最高组（读路径主体，须持有已加载的 User）。 */
    private String resolveTrackGroup(User user) {
        Track trk = track();
        if (trk == null) {
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
        // 同步便捷版：仅测试/非服务器调度线程使用；生产路径一律用 promoteAsync。
        // 在服务器调度线程上 join 会因 LP future 回调排队在自己后面而超时（Folia）。
        return promoteAsync(playerId).join();
    }

    @Override
    public CompletableFuture<String> promoteAsync(UUID playerId) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        // LP 操作（loadUser/saveUser 的等待）在非服务器线程执行：global/region 线程空闲，
        // 可自由处理 LP future 完成回调，杜绝「调度线程同步等 LP future」自锁超时。
        return supplyAsync(() -> promoteInternal(playerId));
    }

    /** 玩家当前是否坐牢（未注入 prison 判定端口时一律 false）。 */
    private boolean isPrisoner(UUID playerId) {
        return prisonCheck != null && prisonCheck.test(playerId);
    }

    /** {@link #promoteAsync} 主体（在非服务器线程执行，内部直接调 LP API，不做任何跨线程调度）。 */
    private String promoteInternal(UUID playerId) {
        // 坐牢守卫：prison 组完全独立于四级 track，坐牢玩家拒绝升降级——一旦被下方
        // normalizeSingleGroup 清掉 prison 继承节点，玩家会漂移回四级（防「坐牢变放假」）。
        if (isPrisoner(playerId)) {
            LOG.warning("promote 拒绝: 玩家正在坐牢，无法升降级 " + playerId);
            return null;
        }
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
        PromotionResult result = trk.promote(user, globalContext());
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
            PromotionResult second = trk.promote(user, globalContext());
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
        // 同步便捷版：仅测试/非服务器调度线程使用；生产路径一律用 demoteAsync。
        return demoteAsync(playerId).join();
    }

    @Override
    public CompletableFuture<String> demoteAsync(UUID playerId) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        return supplyAsync(() -> demoteInternal(playerId));
    }

    /** {@link #demoteAsync} 主体（在非服务器线程执行，内部直接调 LP API，不做任何跨线程调度）。 */
    private String demoteInternal(UUID playerId) {
        // 坐牢守卫：同 promote（见 promoteInternal），防 normalizeSingleGroup 清掉 prison 节点
        if (isPrisoner(playerId)) {
            LOG.warning("demote 拒绝: 玩家正在坐牢，无法升降级 " + playerId);
            return null;
        }
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
        DemotionResult result = trk.demote(user, globalContext());
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

    /**
     * 在异步执行器（非服务器线程）上执行 LP 操作。
     *
     * <p>无注入执行器时内联（测试/无阻塞场景）；有执行器时经其调度，调用线程不等待。
     * LP 操作在此类线程上等待 future 时，服务器同步线程空闲可正常处理回调，无自锁。</p>
     */
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> action) {
        if (asyncExecutor == null) {
            return CompletableFuture.completedFuture(action.get());
        }
        return CompletableFuture.supplyAsync(action, asyncExecutor);
    }

    /**
     * 在同步调度线程（Paper 主线程 / Folia global region 线程）执行快速 Bukkit 调用
     * （当前仅 {@link #resolvePlayerId} 的 getOfflinePlayer）。带超时等待，停摆时返回 null 降级。
     * <b>不得</b>用于等待 LP 异步 future 的操作（会自锁，见类注释）。
     */
    private <T> T runSync(Supplier<T> action) {
        // Paper 主线程与 Folia global region 线程都是「同步调度线程」，直接执行；
        // 异步线程（LP loadUser 等）上则回调度器执行，避免 self-schedule + join 死锁。
        // （isGlobalTickThread 在 Paper 上等价于主线程判定，Folia 上判定 global region 线程）
        if (Bukkit.isGlobalTickThread() || scheduler == null) {
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
        // 带超时等待：global 调度器停摆时避免调用线程无限阻塞（join 无超时）。
        // 超时/中断/执行异常一律按失败处理返回 null，由读路径（null→default/未命中）
        // 与写路径（null→晋升失败）各自降级，并在此记日志保留现场。
        try {
            return done.get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warning("runSync 等待同步线程执行失败（超时/中断/异常）: " + e);
            return null;
        }
    }
}
