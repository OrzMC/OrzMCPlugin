package com.jokerhub.paper.plugin.orzmc.core.ports.server;

public interface ServerScheduler {
    void runSync(Runnable task);

    void runAsync(Runnable task);

    void runLater(Runnable task, long delayTicks);
}
