package com.jokerhub.paper.plugin.orzmc.infra.server;

/**
 * 把任务投递到 Folia 的 global region 线程执行（Paper 上即主线程）。
 *
 * <p>Folia 中「全局状态」查询/修改（如 {@code World.isChunkForceLoaded} / {@code setChunkForceLoaded}、
 * 玩家列表）必须在 global region 线程，否则抛 {@code IllegalStateException: ... off global region}；
 * 与 {@link RegionSchedulerProvider}（区块操作 → 所属 region 线程）互补。抽象成端口便于单测
 * 直接断言「全局操作经 global 调度」而不必启动真实 Folia。</p>
 */
@FunctionalInterface
public interface GlobalSchedulerProvider {

    /** 将 {@code task} 投递到 global region 线程执行；实现应返回前不保证已执行。 */
    void run(Runnable task);
}
