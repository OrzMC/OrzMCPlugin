package com.jokerhub.paper.plugin.orzmc.infra.scheduler;

import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import org.junit.jupiter.api.Test;

class SchedulersTest {

    @Test
    void runMain_delegates() {
        ServerScheduler server = mock(ServerScheduler.class);
        Runnable task = mock(Runnable.class);
        Schedulers.runMain(server, task);
        verify(server).runSync(task);
    }

    @Test
    void runAsync_delegates() {
        ServerScheduler server = mock(ServerScheduler.class);
        Runnable task = mock(Runnable.class);
        Schedulers.runAsync(server, task);
        verify(server).runAsync(task);
    }

    @Test
    void runLater_delegates() {
        ServerScheduler server = mock(ServerScheduler.class);
        Runnable task = mock(Runnable.class);
        Schedulers.runLater(server, task, 20L);
        verify(server).runLater(task, 20L);
    }
}
