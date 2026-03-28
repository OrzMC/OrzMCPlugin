package com.jokerhub.paper.plugin.orzmc.infra.scheduler;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;

public final class Schedulers {
    private Schedulers() {}

    public static void runMain(ServerScheduler server, Runnable r) {
        server.runSync(r);
    }

    public static void runAsync(ServerScheduler server, Runnable r) {
        server.runAsync(r);
    }

    public static void runLater(ServerScheduler server, Runnable r, long delayTicks) {
        server.runLater(r, delayTicks);
    }
}
