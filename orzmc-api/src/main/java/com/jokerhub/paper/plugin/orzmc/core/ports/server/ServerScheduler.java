package com.jokerhub.paper.plugin.orzmc.core.ports.server;

/**
 * 服务端调度门面。
 *
 * <p>Feature 层通过此接口执行异步或延时任务，不直接依赖 Bukkit 调度器。</p>
 */
public interface ServerScheduler {

    /**
     * 在主线程同步执行任务。
     *
     * @param task 待执行的任务
     */
    void runSync(Runnable task);

    /**
     * 在异步线程执行任务。
     *
     * @param task 待执行的任务
     */
    void runAsync(Runnable task);

    /**
     * 延迟指定 tick 后执行任务。
     *
     * @param task       待执行的任务
     * @param delayTicks 延迟的 tick 数（20 tick = 1 秒）
     */
    void runLater(Runnable task, long delayTicks);
}
