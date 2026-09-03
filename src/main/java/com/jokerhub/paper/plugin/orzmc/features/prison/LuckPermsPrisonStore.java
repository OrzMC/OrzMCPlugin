package com.jokerhub.paper.plugin.orzmc.features.prison;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

/**
 * LuckPerms 坐牢执行器（直接 LP API，类型安全）。
 *
 * <p>prison 组完全独立于四级 track：坐牢时 parent/primary 全部切换为 prison 组，
 * 原组/原位置写入 LP 用户元数据（{@code prisoner_original_group}/{@code prisoner_original_location}），
 * 解除坐牢时恢复并清除。原组判定：玩家在四级 track 的组（global 继承节点 ∩ track 链）优先，
 * 不在 track 则回退 primary group。</p>
 *
 * <p><b>软依赖加载约束</b>：本类直接引用 {@code net.luckperms.api} 类型，<b>仅在 LP 已启用时
 * 由装配层实例化</b>（见 FeatureModule.createPrisonGateway）——LP 未安装时本类永远不会被加载，
 * 不会触发 NoClassDefFoundError；LP 缺失时改用 {@link NoopPrisonStore} 降级。</p>
 *
 * <p><b>线程模型（核心约束）</b>：LP 的 loadUser/saveUser 异步 future 完成回调调度到服务器同步
 * 调度线程执行——服务器调度线程绝不能同步等待 LP future（回调排在自己后面 → 自锁超时）。
 * 写路径（{@link #imprison}/{@link #release}）在注入的 {@code asyncExecutor}（非服务器线程）
 * 上执行 LP 操作；读路径 {@link #isPrisoner} 在线玩家取 LP 在线缓存（零 future 等待），离线玩家
 * 在非服务器线程加载并等待（服务器调度线程降级返回 false 避免自锁）。</p>
 */
public final class LuckPermsPrisonStore implements PrisonLpGateway {

    /** 四级 track 名（与 {@code LuckPermsPromoter.TRACK} 一致；prison 独立不在此 track）。 */
    private static final String RANK_TRACK = "rank";

    private static final String LUCKPERMS_PLUGIN = "LuckPerms";
    private static final long LOAD_USER_TIMEOUT_SECONDS = 3;
    private static final String META_ORIGINAL_GROUP = "prisoner_original_group";
    private static final String META_ORIGINAL_LOCATION = "prisoner_original_location";
    private static final Logger LOG = Logger.getLogger("OrzMC.PrisonLp");

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

    /** 同玩家操作串行化分条带锁数（按 UUID 散列，条带数固定无增长，不同玩家极少碰撞）。 */
    private static final int LOCK_STRIPES = 32;

    private final Executor asyncExecutor;
    /** 同玩家 imprison/release 串行化条带锁：同一玩家的两个异步任务不会并发改同一 LP User。 */
    private final Object[] playerLocks;

    public LuckPermsPrisonStore() {
        this(null);
    }

    /** @param asyncExecutor 非服务器线程执行器（坐牢/解除 LP 操作在此运行；null 时内联，测试用） */
    public LuckPermsPrisonStore(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        this.playerLocks = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.playerLocks[i] = new Object();
        }
    }

    /** LuckPerms 是否已启用（软依赖检测）。 */
    @Override
    public boolean isAvailable() {
        PluginManager pm = Bukkit.getPluginManager();
        return pm != null && pm.isPluginEnabled(LUCKPERMS_PLUGIN);
    }

    private LuckPerms api() {
        return LuckPermsProvider.get();
    }

    @Override
    public boolean isPrisoner(UUID playerId) {
        if (!isAvailable()) {
            return false;
        }
        User online = api().getUserManager().getUser(playerId);
        if (online != null) {
            return isPrisonUser(online);
        }
        // 离线缓存未命中：需 loadUser（LP 异步 future）。服务器调度线程同步等待会自锁 → 降级 false
        if (isServerTickThread()) {
            return false;
        }
        User loaded = loadUser(playerId);
        return loaded != null && isPrisonUser(loaded);
    }

    @Override
    public CompletableFuture<ImprisonOutcome> imprison(UUID playerId, String originalLocation) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new ImprisonOutcome(false, null));
        }
        return serializedSupply(playerId, () -> imprisonInternal(playerId, originalLocation));
    }

    @Override
    public CompletableFuture<ReleaseOutcome> release(UUID playerId) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new ReleaseOutcome(false, false, null, null));
        }
        return serializedSupply(playerId, () -> releaseInternal(playerId));
    }

    // ---- LP 操作主体（在非服务器线程执行）----

    private ImprisonOutcome imprisonInternal(UUID playerId, String originalLocation) {
        User user = loadUser(playerId);
        if (user == null) {
            LOG.warning("imprison 跳过: 用户加载失败 " + playerId);
            return new ImprisonOutcome(false, null);
        }
        if (isPrisonUser(user)) {
            // 已在牢房：幂等，保留既有原组记忆，不覆盖（防重复坐牢覆盖原组）
            return new ImprisonOutcome(true, metaValue(user, META_ORIGINAL_GROUP, "default"));
        }
        String originalGroup = resolveRankOrPrimaryGroup(user);
        // 变更前快照：saveUser 失败时回滚内存态（LP 只改内存，落库失败必须还原，避免
        // 内存态与磁盘态漂移——本局后续读/重算会基于错误的内存态判定）
        UserSnapshot snapshot = snapshot(user);
        setParents(user, PRISON_GROUP);
        user.setPrimaryGroup(PRISON_GROUP);
        setMeta(user, META_ORIGINAL_GROUP, originalGroup);
        if (originalLocation != null && !originalLocation.isBlank()) {
            setMeta(user, META_ORIGINAL_LOCATION, originalLocation);
        }
        if (!saveUser(user)) {
            restore(user, snapshot);
            LOG.warning("imprison(" + playerId + ") 落库失败，已回滚内存态，视为失败");
            return new ImprisonOutcome(false, originalGroup);
        }
        return new ImprisonOutcome(true, originalGroup);
    }

    private ReleaseOutcome releaseInternal(UUID playerId) {
        User user = loadUser(playerId);
        if (user == null) {
            LOG.warning("release 跳过: 用户加载失败 " + playerId);
            return new ReleaseOutcome(false, false, null, null);
        }
        UserSnapshot snapshot = snapshot(user);
        if (!isPrisonUser(user)) {
            // 非坐牢玩家：清理可能残留的元数据，返回当前组（不传送）
            String current = resolveRankOrPrimaryGroup(user);
            clearMeta(user, META_ORIGINAL_GROUP);
            clearMeta(user, META_ORIGINAL_LOCATION);
            if (!saveUser(user)) {
                restore(user, snapshot);
                LOG.warning("release(" + playerId + ") 清理残留元数据落库失败，已回滚内存态");
                return new ReleaseOutcome(false, false, current, null);
            }
            return new ReleaseOutcome(true, false, current, null);
        }
        String originalGroup = metaValue(user, META_ORIGINAL_GROUP, "default");
        String originalLocation = metaValue(user, META_ORIGINAL_LOCATION, null);
        setParents(user, originalGroup);
        user.setPrimaryGroup(originalGroup);
        clearMeta(user, META_ORIGINAL_GROUP);
        clearMeta(user, META_ORIGINAL_LOCATION);
        if (!saveUser(user)) {
            restore(user, snapshot);
            LOG.warning("release(" + playerId + ") 落库失败，已回滚内存态，视为失败");
            return new ReleaseOutcome(false, true, originalGroup, originalLocation);
        }
        return new ReleaseOutcome(true, true, originalGroup, originalLocation);
    }

    /** LP User 内存态快照（全节点 + primary group），供落库失败回滚。 */
    private static UserSnapshot snapshot(User user) {
        return new UserSnapshot(new ArrayList<>(user.data().toCollection()), user.getPrimaryGroup());
    }

    /** 把 LP User 内存态还原到快照（清空全部节点后重放快照节点 + 恢复 primary group）。 */
    private static void restore(User user, UserSnapshot snap) {
        NodeMap data = user.data();
        data.clear(node -> true);
        for (Node node : snap.nodes()) {
            data.add(node);
        }
        user.setPrimaryGroup(snap.primaryGroup());
    }

    /** LP User 内存态快照记录。 */
    private record UserSnapshot(List<Node> nodes, String primaryGroup) {}

    /** 玩家是否为坐牢状态：primary group 为 prison，或任一 parent 为 prison。 */
    private boolean isPrisonUser(User user) {
        if (PRISON_GROUP.equalsIgnoreCase(user.getPrimaryGroup())) {
            return true;
        }
        for (Node node : user.data().toCollection()) {
            if (node instanceof InheritanceNode inh && PRISON_GROUP.equalsIgnoreCase(inh.getGroupName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家当前四级组（用于记录原组）：global 继承节点 ∩ rank track 链取最高位；
     * 不在 track 则回退 primary group；均不可用时 default。
     */
    private String resolveRankOrPrimaryGroup(User user) {
        Track track = api().getTrackManager().getTrack(RANK_TRACK);
        Set<String> inherited = new HashSet<>();
        for (Node node : user.data().toCollection()) {
            if (node instanceof InheritanceNode inh) {
                inherited.add(inh.getGroupName().toLowerCase(Locale.ROOT));
            }
        }
        if (track != null) {
            String found = null;
            for (String group : track.getGroups()) {
                if (inherited.contains(group.toLowerCase(Locale.ROOT))) {
                    found = group;
                }
            }
            if (found != null) {
                return found;
            }
        }
        String primary = user.getPrimaryGroup();
        if (primary != null && !primary.isBlank() && !PRISON_GROUP.equalsIgnoreCase(primary)) {
            return primary;
        }
        return "default";
    }

    /** 把玩家 parent 清空后仅挂指定组（坐牢=prison，解除=原组）。 */
    private void setParents(User user, String parentGroup) {
        NodeMap data = user.data();
        data.clear(node -> node instanceof InheritanceNode);
        data.add(api().getNodeBuilderRegistry()
                .forInheritance()
                .group(parentGroup)
                .build());
    }

    private void setMeta(User user, String key, String value) {
        user.data()
                .add(api().getNodeBuilderRegistry()
                        .forMeta()
                        .key(key)
                        .value(value)
                        .build());
    }

    private String metaValue(User user, String key, String fallback) {
        for (Node node : user.data().toCollection()) {
            if (node instanceof MetaNode meta && key.equals(meta.getMetaKey())) {
                return meta.getMetaValue();
            }
        }
        return fallback;
    }

    private void clearMeta(User user, String key) {
        for (Node node : user.data().toCollection()) {
            if (node instanceof MetaNode meta && key.equals(meta.getMetaKey())) {
                user.data().remove(node);
            }
        }
    }

    // ---- LP 访问与线程工具（与 LuckPermsPromoter 同范式）----

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

    /** 持久化用户变更（LP 只改内存，须显式保存）。@return true=落库成功。 */
    private boolean saveUser(User user) {
        try {
            api().getUserManager().saveUser(user).get(LOAD_USER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.warning("saveUser 失败: " + e);
            return false;
        }
    }

    /** 当前线程是否为服务器调度线程（Paper 主线程 / Folia global 或 region 线程）。 */
    static boolean isServerTickThread() {
        return Bukkit.isGlobalTickThread() || isRegionTickThread();
    }

    /** Folia region 线程判定：反射调 Folia API 独有方法 {@code Bukkit.isRegionOwnedByCurrentThread()}；Paper 无此方法返回 false。 */
    private static boolean isRegionTickThread() {
        if (REGION_THREAD_CHECK == null) {
            return false;
        }
        try {
            return (boolean) REGION_THREAD_CHECK.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            return false; // 反射异常一律视为非 region 线程（Paper 路径/防御性降级）
        }
    }

    /** 同一玩家的条带锁（UUID 散列到固定条带，条带数固定不随玩家数增长）。 */
    private Object lockFor(UUID playerId) {
        return playerLocks[Math.floorMod(playerId.hashCode(), LOCK_STRIPES)];
    }

    /**
     * 在异步执行器（非服务器线程）上执行 LP 操作；无执行器时内联（测试/无阻塞场景）。
     *
     * <p><b>同玩家串行化</b>：imprison/release 按 UUID 条带锁串行执行（锁内完成 loadUser/
     * 修改/saveUser 等待），避免两个异步任务并发修改同一 LP User 造成状态竞争（如 release
     * 与 imprison 交错、saveUser 覆盖彼此的变更）。不同玩家散列到不同条带，互不阻塞。</p>
     */
    private <T> CompletableFuture<T> serializedSupply(UUID playerId, Supplier<T> action) {
        if (asyncExecutor == null) {
            synchronized (lockFor(playerId)) {
                return CompletableFuture.completedFuture(action.get());
            }
        }
        return CompletableFuture.supplyAsync(
                () -> {
                    synchronized (lockFor(playerId)) {
                        return action.get();
                    }
                },
                asyncExecutor);
    }
}
