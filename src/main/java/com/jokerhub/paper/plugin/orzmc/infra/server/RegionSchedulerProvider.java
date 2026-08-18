package com.jokerhub.paper.plugin.orzmc.infra.server;

import org.bukkit.World;

/**
 * 把任务投递到指定区块所属的 region 线程执行。
 *
 * <p>Folia 的区域化线程模型要求方块/区块操作必须在拥有该区块的 region 线程上执行；
 * Paper 上 region 调度器即主线程执行，语义等价。抽象成可注入端口是为了在普通 JUnit
 * 测试中直接断言「方块/区块操作投递到了正确的 chunk 坐标」（{@code verify(provider).run(world, cx, cz, task)}），
 * 而不必启动真实 Folia。</p>
 */
@FunctionalInterface
public interface RegionSchedulerProvider {

    /** 将 {@code task} 投递到 {@code (chunkX, chunkZ)} 所属 region 线程；实现应返回前不保证已执行。 */
    void run(World world, int chunkX, int chunkZ, Runnable task);

    /** 同步直跑（不投递）：仅用于测试与仅支持单线程的构造兜底，生产路径使用 {@link BukkitRegionSchedulerProvider}。 */
    static RegionSchedulerProvider inline() {
        return (world, chunkX, chunkZ, task) -> task.run();
    }
}
