package com.jokerhub.paper.plugin.orzmc.infra.scheduler;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 安全调度器包装器，确保异步任务中的异常被记录而不是被 Bukkit 调度器静默吞噬。
 *
 * <p>Bukkit 的 {@code runTaskAsynchronously} 不会向开发者传播未捕获异常；
 * SafeScheduler 在每个异步和延迟任务周围添加 try-catch。</p>
 */
public final class SafeScheduler implements ServerScheduler {

    private final ServerScheduler delegate;
    private final Logger logger;

    public SafeScheduler(ServerScheduler delegate, Logger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public void runSync(Runnable task) {
        delegate.runSync(task);
    }

    @Override
    public void runAsync(Runnable task) {
        delegate.runAsync(wrap(task));
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        delegate.runLater(wrap(task), delayTicks);
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "异步任务执行异常", e);
            }
        };
    }
}
