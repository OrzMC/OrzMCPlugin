package com.jokerhub.paper.plugin.orzmc.features.rank;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import java.util.Map;
import java.util.UUID;

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

    private final RankStore store;
    private final RankPromoter promoter;
    private final int memberThresholdHours;
    private final ReviewNotifier notifier;

    public RankService(RankStore store, RankPromoter promoter) {
        this(store, promoter, DEFAULT_MEMBER_THRESHOLD_HOURS, null);
    }

    public RankService(RankStore store, RankPromoter promoter, int memberThresholdHours) {
        this(store, promoter, memberThresholdHours, null);
    }

    public RankService(RankStore store, RankPromoter promoter, int memberThresholdHours, ReviewNotifier notifier) {
        this.store = store;
        this.promoter = promoter;
        this.memberThresholdHours = memberThresholdHours;
        this.notifier = notifier;
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

    /** 检查玩家是否达到自动晋升条件（default→member）。
     *
     * <p>时长从服务器原生 stats 读取（玩家离线也有数据），因此可在任意时刻调用。
     * 幂等由 LP 保证：已在 member 及以上（track 非首组）不重复晋升。</p>
     */
    public void checkPromotion(UUID playerId) {
        if (!promoter.isAvailable()) {
            return; // 无 LuckPerms：晋升不可用
        }
        long playtime = store.getPlaytimeMinutes(playerId);
        if (playtime < memberThresholdMinutes()) {
            return;
        }
        String current = promoter.currentTrackGroup(playerId);
        if (current == null || "default".equals(current)) {
            promote(playerId);
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
     * 升级一级（LP track 钳位）：default→member→builder→admin。
     *
     * @return 升级后的组名；链顶（admin）或不可用时返回 null
     */
    public String promote(UUID playerId) {
        if (!promoter.isAvailable()) {
            return null; // 无 LuckPerms：升级不可用
        }
        String to = promoter.promote(playerId);
        if (to == null) {
            return null; // 链顶（END_OF_TRACK）或失败
        }
        notifyPlayer(playerId, "你的权限已升级：" + groupDisplayName(to) + "。");
        notifyGroup(
                "rank_promoted",
                Map.of(
                        "player", promoter.playerName(playerId).orElse(playerId.toString()),
                        "group", groupDisplayName(to)));
        return to;
    }

    /**
     * 降级一级（LP track 钳位）：admin→builder→member→default。
     *
     * @return 降级后的组名；链底（default）或不可用时返回 null
     */
    public String demote(UUID playerId) {
        if (!promoter.isAvailable()) {
            return null; // 无 LuckPerms：降级不可用
        }
        String to = promoter.demote(playerId);
        if (to == null) {
            return null; // 链底（REMOVED_FROM_FIRST_GROUP / NOT_ON_TRACK）或失败
        }
        notifyPlayer(playerId, "你的权限已被降级：" + groupDisplayName(to) + "。");
        notifyGroup(
                "rank_demoted",
                Map.of(
                        "player", promoter.playerName(playerId).orElse(playerId.toString()),
                        "group", groupDisplayName(to)));
        return to;
    }

    /** LuckPerms 是否可用（软依赖检测）。 */
    public boolean isLuckPermsAvailable() {
        return promoter.isAvailable();
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
