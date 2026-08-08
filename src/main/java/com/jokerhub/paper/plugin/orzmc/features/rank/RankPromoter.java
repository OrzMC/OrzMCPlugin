package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.Optional;
import java.util.UUID;

/**
 * 权限执行器端口：委托 LuckPerms track API 完成升降级与组查询。
 *
 * <p>权限链（track "rank"）：default → member → builder → admin。
 * 全部操作走 LP track 原生钳位语义，插件侧不维护任何本地权限状态。</p>
 *
 * <p><b>本接口及实现不得引用任何 {@code net.luckperms.api} 类型</b>——LuckPerms 是软依赖，
 * LP 未安装时类加载必须零依赖（实现为直接 LP API 版 {@link LuckPermsPromoter} +
 * 无反射条件实例化，见 FeatureModule.createRankPromoter）。
 * {@link #isAvailable()} 返回 false 时调用方<b>不得</b>执行升降级，
 * 回退本地推断（reviews 记录）用于展示。</p>
 */
public interface RankPromoter {

    /** LuckPerms 是否可用（软依赖检测）。 */
    boolean isAvailable();

    /** 玩家是否在指定组（LP 真实查询，离线加载）。 */
    boolean isInGroup(UUID playerId, String groupName);

    /** 玩家当前所在 rank track 的组（不在 track / LP 不可用返回 null）。 */
    String currentTrackGroup(UUID playerId);

    /**
     * 沿 rank track 晋升一级（钳位）。
     *
     * @return 晋升后的目标组名（"member"/"builder"/"admin"）；链顶、失败或 LP 不可用返回 null
     */
    String promote(UUID playerId);

    /**
     * 沿 rank track 降级一级（钳位）。
     *
     * @return 降级后的目标组名（"member"/"default"）；链底、失败或 LP 不可用返回 null
     */
    String demote(UUID playerId);

    /** 玩家名→UUID 解析（离线服查最后已知 UUID）。 */
    UUID resolvePlayerId(String playerName);

    /** UUID→玩家名解析（LP 用户加载或本地缓存）。 */
    Optional<String> playerName(UUID playerId);
}
