package com.jokerhub.paper.plugin.orzmc.features.prison;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 坐牢权限执行器端口：委托 LuckPerms 完成 prison 组切换与「原组记忆」元数据读写。
 *
 * <p>prison 完全独立于四级 track：不在 TRACK_GROUPS、不参与 promote/demote 链。
 * 坐牢 = parent/primary 全部切换为 prison 组；解除坐牢 = 恢复 meta 记录的原组。
 * 元数据键：
 * <ul>
 *   <li>{@code prisoner_original_group} — 坐牢前的四级组（不在 track 则 primary group）</li>
 *   <li>{@code prisoner_original_location} — 坐牢前位置（world,x,y,z,yaw,pitch，在线才记录）</li>
 * </ul>
 *
 * <p><b>本接口及实现不得引用任何 {@code net.luckperms.api} 类型</b>——LuckPerms 是软依赖，
 * LP 未安装时类加载必须零依赖（实现为直接 LP API 版 {@link LuckPermsPrisonStore} +
 * 条件实例化，见 FeatureModule.createPrisonGateway）。{@link #isAvailable()} 返回 false
 * 时调用方<b>不得</b>执行坐牢/解除操作。</p>
 */
public interface PrisonLpGateway {

    /** prison 组名（LP 组，完全独立于四级 track）。 */
    String PRISON_GROUP = "prison";

    /** LuckPerms 是否可用（软依赖检测）。 */
    boolean isAvailable();

    /**
     * 玩家当前是否为坐牢状态（primary group 或任一 parent 为 prison 组）。
     *
     * <p>在线玩家读 LP 在线缓存（零往返）；离线玩家在非服务器线程加载并等待（服务器
     * 调度线程离线缓存未命中时降级返回 false，避免同步等 LP future 自锁）。</p>
     */
    boolean isPrisoner(UUID playerId);

    /**
     * 执行坐牢：记录原组（+可选原位置）到元数据 → parent/primary 切换为 prison → 落库。
     *
     * <p>已在牢房的玩家幂等返回（保留既有原组记忆，不覆盖）。LP 操作在异步执行器
     * （非服务器线程）执行，调用线程不阻塞。</p>
     *
     * @param originalLocation 坐牢前位置（序列化字符串，玩家在线才传；离线传 null）
     * @return 完成时给出结果；{@code originalGroup} 为记录的原组
     */
    CompletableFuture<ImprisonOutcome> imprison(UUID playerId, String originalLocation);

    /**
     * 执行解除坐牢：读原组/原位置元数据 → parent/primary 恢复原组 → 清除元数据 → 落库。
     *
     * @return 完成时给出结果；非坐牢玩家 {@code wasPrisoner=false}（原组=当前组，不传送）
     */
    CompletableFuture<ReleaseOutcome> release(UUID playerId);

    /** 坐牢结果：成功与否 + 记录的原组（用于通知「原组 X」）。 */
    record ImprisonOutcome(boolean success, String originalGroup) {}

    /** 解除坐牢结果：成功与否 + 是否曾是坐牢玩家 + 恢复的原组 + 原位置（元数据里的）。 */
    record ReleaseOutcome(boolean success, boolean wasPrisoner, String originalGroup, String originalLocation) {}
}
