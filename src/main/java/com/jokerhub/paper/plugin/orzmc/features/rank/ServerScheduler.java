package com.jokerhub.paper.plugin.orzmc.features.rank;

/** 主线程调度端口：异步链路回主线程执行 LP 变更。 */
@FunctionalInterface
public interface ServerScheduler {

    void runSync(Runnable action);
}
