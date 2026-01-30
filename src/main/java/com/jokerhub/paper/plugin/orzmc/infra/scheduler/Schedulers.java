package com.jokerhub.paper.plugin.orzmc.infra.scheduler;

import com.jokerhub.paper.plugin.orzmc.OrzMC;

public final class Schedulers {
    private Schedulers() {}

    public static void runMain(Runnable r) {
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), r);
    }

    public static void runAsync(Runnable r) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), r);
    }

    public static void runLater(Runnable r, long delayTicks) {
        OrzMC.server().getScheduler().runTaskLater(OrzMC.plugin(), r, delayTicks);
    }
}
