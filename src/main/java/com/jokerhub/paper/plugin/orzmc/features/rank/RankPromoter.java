package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
 *
 * <p><b>异步约束</b>：LP 的 loadUser/saveUser future 完成回调调度到服务器同步调度线程
 * （Folia global / Paper 主线程）执行，因此服务器调度线程绝不能同步等待 LP future——
 * 生产路径一律用 {@link #promoteAsync} / {@link #demoteAsync}（在非服务器线程上执行 LP 操作）。
 * 同步 {@link #promote} / {@link #demote} 仅保留给测试与无阻塞场景。</p>
 */
public interface RankPromoter {

    /** LuckPerms 是否可用（软依赖检测）。 */
    boolean isAvailable();

    /** 玩家当前所在 rank track 的组（不在 track / LP 不可用返回 null）。 */
    String currentTrackGroup(UUID playerId);

    /**
     * 沿 rank track 晋升一级（钳位）。
     *
     * <p><b>仅测试/非服务器调度线程使用</b>；生产路径用 {@link #promoteAsync}。
     * 在服务器调度线程上同步等待会因 LP future 回调排队在自己后面而超时（Folia）。</p>
     *
     * @return 晋升后的目标组名（"member"/"builder"/"admin"）；链顶、失败或 LP 不可用返回 null
     */
    String promote(UUID playerId);

    /**
     * 沿 rank track 降级一级（钳位）。
     *
     * <p><b>仅测试/非服务器调度线程使用</b>；生产路径用 {@link #demoteAsync}。</p>
     *
     * @return 降级后的目标组名（"member"/"default"）；链底、失败或 LP 不可用返回 null
     */
    String demote(UUID playerId);

    /**
     * 异步沿 rank track 晋升一级（钳位）。
     *
     * <p>LP 操作（loadUser 等待 + 修改 + saveUser 等待）在<b>非服务器调度线程</b>执行，
     * 服务器调度线程（global/region）可自由处理 LP future 完成回调，杜绝自锁超时。
     * 默认实现同步委托 {@link #promote}（无阻塞的 mock/Noop 场景）。</p>
     *
     * @return 完成时给出晋升后的目标组名；链顶、失败或 LP 不可用为 null
     */
    default CompletableFuture<String> promoteAsync(UUID playerId) {
        return CompletableFuture.completedFuture(promote(playerId));
    }

    /**
     * 异步沿 rank track 降级一级（钳位）。线程语义同 {@link #promoteAsync}。
     *
     * @return 完成时给出降级后的目标组名；链底、失败或 LP 不可用为 null
     */
    default CompletableFuture<String> demoteAsync(UUID playerId) {
        return CompletableFuture.completedFuture(demote(playerId));
    }

    /** 玩家名→UUID 解析（离线服查最后已知 UUID）。 */
    UUID resolvePlayerId(String playerName);

    /** UUID→玩家名解析（LP 用户加载或本地缓存）。 */
    Optional<String> playerName(UUID playerId);
}
