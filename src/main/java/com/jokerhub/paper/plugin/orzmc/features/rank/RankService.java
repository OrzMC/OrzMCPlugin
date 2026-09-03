package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.features.prison.PrisonService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * 玩家权限服务：自动晋升 + 手动升降级 + 当前权限组查询。
 *
 * <p>权限链（track "rank"，LP 为唯一事实源）：default → member → builder → admin。
 * <ul>
 *   <li>自动晋升：default→member（累计在线时长读服务器原生 stats，达阈值，上线时检查）</li>
 *   <li>手动升降级：{@link #promote} / {@link #demote} 每次一级，LP track 原生钳位</li>
 *   <li>申请晋升：member→builder 走通用审核框架（见 {@code ReviewService}），通过后调 {@link #promote}</li>
 * </ul>
 * 升降级委托 {@link RankPromoter}（LP track API），结果状态翻译为业务提示。</p>
 *
 * <p>当前权限组（{@link #currentGroup}）以 LP 真实组为准（在线缓存/离线加载）；
 * 无 LuckPerms 时一律回退 default（访客）——权限状态无本地推断，杜绝虚假展示。</p>
 */
public final class RankService {

    /** 默认晋升阈值（小时）。 */
    public static final int DEFAULT_MEMBER_THRESHOLD_HOURS = 10;

    private static final Logger LOGGER = Logger.getLogger("OrzMC.RankService");

    private final RankStore store;
    private final RankPromoter promoter;
    private final int memberThresholdHours;
    private final ReviewNotifier notifier;
    /** 回同步调度线程执行游戏模式矫正的执行器（Paper 主线程 / Folia global region 线程）；未注入则内联（单测）。 */
    private final Executor syncExecutor;
    /** 升降级后的游戏模式矫正（可空：不注入则跳过）。 */
    private final GamemodeCorrectionService gamemodeCorrection;
    /** 坐牢服务（可空：不注入则不做 prison 拦截）。 */
    private final PrisonService prisonService;

    public RankService(RankStore store, RankPromoter promoter) {
        this(store, promoter, DEFAULT_MEMBER_THRESHOLD_HOURS, null);
    }

    public RankService(RankStore store, RankPromoter promoter, int memberThresholdHours) {
        this(store, promoter, memberThresholdHours, null);
    }

    public RankService(RankStore store, RankPromoter promoter, int memberThresholdHours, ReviewNotifier notifier) {
        this(store, promoter, memberThresholdHours, notifier, null, null);
    }

    public RankService(
            RankStore store,
            RankPromoter promoter,
            int memberThresholdHours,
            ReviewNotifier notifier,
            Executor syncExecutor,
            GamemodeCorrectionService gamemodeCorrection) {
        this(store, promoter, memberThresholdHours, notifier, syncExecutor, gamemodeCorrection, null);
    }

    /**
     * @param syncExecutor      回同步调度线程执行游戏模式矫正（promote/demote 的 LP 操作在
     *     非服务器线程，矫正需切回同步线程）；生产传入 {@code serverFacade::runSync}。
     * @param gamemodeCorrection 升降级后的游戏模式矫正（可空；不注入则跳过）。
     * @param prisonService     坐牢服务（可空；不注入则不做 prison 玩家晋升拦截）。
     */
    public RankService(
            RankStore store,
            RankPromoter promoter,
            int memberThresholdHours,
            ReviewNotifier notifier,
            Executor syncExecutor,
            GamemodeCorrectionService gamemodeCorrection,
            PrisonService prisonService) {
        this.store = store;
        this.promoter = promoter;
        this.memberThresholdHours = memberThresholdHours;
        this.notifier = notifier;
        this.syncExecutor = syncExecutor;
        this.gamemodeCorrection = gamemodeCorrection;
        this.prisonService = prisonService;
    }

    /** 玩家在线则发游戏内消息；通知端口未注入或玩家离线时静默。 */
    private void notifyPlayer(UUID playerId, String message) {
        if (notifier != null) {
            notifier.gameMessage(playerId, message);
        }
    }

    /** 群广播权限变化（模板键 + 变量）。 */
    private void notifyGroup(String templateKey, Map<String, String> vars) {
        if (notifier != null) {
            notifier.groupEvent(templateKey, vars);
        }
    }

    /** 升降级后矫正游戏模式：correctAsync 经玩家实体调度器投递到其 region 线程（Folia 兼容），线程无关。 */
    private void correctGamemode(UUID playerId) {
        if (gamemodeCorrection == null) {
            return;
        }
        gamemodeCorrection.correctAsync(org.bukkit.Bukkit.getPlayer(playerId));
    }

    /** 检查玩家是否达到自动晋升条件（default→member）。
     *
     * <p>时长从服务器原生 stats 读取（玩家离线也有数据），因此可在任意时刻调用。
     * 幂等由 LP 保证：已在 member 及以上（track 非首组）不重复晋升。
     * 晋升走 {@link #promoteAsync} 异步执行（LP 操作在非服务器线程），调用线程不阻塞。</p>
     */
    public void checkPromotion(UUID playerId) {
        if (!promoter.isAvailable()) {
            return; // 无 LuckPerms：晋升不可用
        }
        // 坐牢玩家不参与四级晋升（prison 独立组，不在 track；重进也不自动回四级）
        if (prisonService != null && prisonService.isPrisoner(playerId)) {
            return;
        }
        long playtime = store.getPlaytimeMinutes(playerId);
        if (playtime < memberThresholdMinutes()) {
            return;
        }
        String current = promoter.currentTrackGroup(playerId);
        if (current == null || "default".equals(current)) {
            promoteAsync(playerId).whenComplete((to, err) -> {
                if (err != null) {
                    LOGGER.warning("自动晋升失败: " + playerId + " - " + err.getMessage());
                }
            });
        }
    }

    /** 玩家累计在线时长（分钟）——读服务器原生 stats。 */
    public long playtimeMinutes(UUID playerId) {
        return store.getPlaytimeMinutes(playerId);
    }

    /** 晋升 member 阈值（分钟）。 */
    public long memberThresholdMinutes() {
        return memberThresholdHours * 60L;
    }

    /** 玩家当前权限组：LP 为唯一事实源；无 LP 时一律回退 default（访客，诚实展示，不做本地推断）。 */
    public String currentGroup(UUID playerId) {
        if (promoter.isAvailable()) {
            String trackGroup = promoter.currentTrackGroup(playerId);
            if (trackGroup != null) {
                return trackGroup;
            }
        }
        return "default";
    }

    /**
     * 升级一级（LP track 钳位）：default→member→builder→admin（同步便捷版）。
     *
     * <p><b>仅测试/非服务器调度线程使用</b>；生产路径用 {@link #promoteAsync}——
     * 服务器调度线程同步等待 LP 异步 future 会自锁超时（Folia LP 适配器行为）。</p>
     *
     * @return 升级后的组名；链顶（admin）或不可用时返回 null
     */
    public String promote(UUID playerId) {
        guardSyncCall("promote");
        return promoteAsync(playerId).join();
    }

    /**
     * 异步升级一级（LP track 钳位）：default→member→builder→admin。
     *
     * <p>LP 操作（loadUser 等待 + 修改 + saveUser 等待）在非服务器线程执行，
     * 服务器调度线程（global/region）可自由处理 LP future 完成回调，杜绝自锁超时。
     * 完成后在原线程发双端通知；结果决定审核状态唯一依据（无漂移）。</p>
     *
     * @return 完成时给出升级后的组名；链顶（admin）或不可用时为 null
     */
    public CompletableFuture<String> promoteAsync(UUID playerId) {
        if (!promoter.isAvailable()) {
            return CompletableFuture.completedFuture(null); // 无 LuckPerms：升级不可用
        }
        // 坐牢玩家拒绝升降级（双层守卫：本层拦截业务调用 + LuckPermsPromoter 内层拦截 LP 直接调用）
        if (prisonService != null && prisonService.isPrisoner(playerId)) {
            return CompletableFuture.completedFuture(null);
        }
        return promoter.promoteAsync(playerId).thenApply(to -> {
            if (to == null) {
                return null; // 链顶（END_OF_TRACK）或失败
            }
            notifyPlayer(playerId, "你的权限已升级：" + groupDisplayName(to) + "。");
            notifyGroup(
                    "rank_promoted",
                    Map.of(
                            "player", promoter.playerName(playerId).orElse(playerId.toString()),
                            "group", groupDisplayName(to)));
            correctGamemode(playerId);
            return to;
        });
    }

    /**
     * 降级一级（LP track 钳位）：admin→builder→member→default（同步便捷版）。
     *
     * <p><b>仅测试/非服务器调度线程使用</b>；生产路径用 {@link #demoteAsync}。</p>
     *
     * @return 降级后的组名；链底（default）或不可用时返回 null
     */
    public String demote(UUID playerId) {
        guardSyncCall("demote");
        return demoteAsync(playerId).join();
    }

    /**
     * 异步降级一级（LP track 钳位）：admin→builder→member→default。
     *
     * <p>线程语义同 {@link #promoteAsync}。</p>
     *
     * @return 完成时给出降级后的组名；链底（default）或不可用时为 null
     */
    public CompletableFuture<String> demoteAsync(UUID playerId) {
        if (!promoter.isAvailable()) {
            return CompletableFuture.completedFuture(null); // 无 LuckPerms：降级不可用
        }
        // 坐牢玩家拒绝升降级（同 promoteAsync 双层守卫）
        if (prisonService != null && prisonService.isPrisoner(playerId)) {
            return CompletableFuture.completedFuture(null);
        }
        return promoter.demoteAsync(playerId).thenApply(to -> {
            if (to == null) {
                return null; // 链底（REMOVED_FROM_FIRST_GROUP / NOT_ON_TRACK）或失败
            }
            notifyPlayer(playerId, "你的权限已被降级：" + groupDisplayName(to) + "。");
            notifyGroup(
                    "rank_demoted",
                    Map.of(
                            "player", promoter.playerName(playerId).orElse(playerId.toString()),
                            "group", groupDisplayName(to)));
            correctGamemode(playerId);
            return to;
        });
    }

    /** LuckPerms 是否可用（软依赖检测）。 */
    public boolean isLuckPermsAvailable() {
        return promoter.isAvailable();
    }

    /**
     * 同步便捷版仅测试/非调度线程使用：服务器调度线程（global/region）同步等待 LP future 会自锁
     * （见 docs/dev/folia-luckperms-gotchas.md）。运行时守卫把「文档约定」变成硬约束。
     */
    private static void guardSyncCall(String op) {
        boolean onTickThread;
        try {
            onTickThread = LuckPermsPromoter.isServerTickThread();
        } catch (RuntimeException e) {
            // Bukkit 未初始化（纯单测环境）时无法判定，放行；生产环境 Bukkit 恒可用，守卫生效
            onTickThread = false;
        }
        if (onTickThread) {
            throw new IllegalStateException("禁止在服务器调度线程调用同步 " + op + "，请改用 " + op + "Async");
        }
    }

    /** 玩家名→UUID 解析（离线服需查缓存）。 */
    public UUID resolvePlayerId(String playerName) {
        return promoter.resolvePlayerId(playerName);
    }

    /**
     * 当前权限组展示名（权限组 → 中文名的<b>唯一事实源</b>）。
     *
     * <p>新增/修改组名只改这里；$l 在线列表、上下线广播、rank 通知、
     * /rank、$p 反馈全部走本方法。未知组一律回退「访客」。</p>
     */
    public static String groupDisplayName(String group) {
        return switch (group) {
            case "admin" -> "管理员";
            case "builder" -> "建造者";
            case "member" -> "成员";
            default -> "访客";
        };
    }
}
