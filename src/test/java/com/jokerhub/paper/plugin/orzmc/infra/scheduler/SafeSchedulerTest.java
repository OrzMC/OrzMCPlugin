package com.jokerhub.paper.plugin.orzmc.infra.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SafeSchedulerTest {

    private ServerScheduler delegate;
    private Logger logger;
    private SafeScheduler scheduler;

    @BeforeEach
    void setUp() {
        delegate = mock(ServerScheduler.class);
        logger = mock(Logger.class);
        scheduler = new SafeScheduler(delegate, logger);
    }

    @Test
    void runSync_delegates() {
        Runnable task = mock(Runnable.class);
        scheduler.runSync(task);
        verify(delegate).runSync(task);
    }

    @Test
    void runAsync_delegates() {
        Runnable task = mock(Runnable.class);
        scheduler.runAsync(task);
        verify(delegate).runAsync(any(Runnable.class));
    }

    @Test
    void runLater_delegatesWithDelay() {
        Runnable task = mock(Runnable.class);
        scheduler.runLater(task, 20L);
        verify(delegate).runLater(any(Runnable.class), eq(20L));
    }

    @Test
    void asyncException_isLogged() {
        scheduler.runAsync(() -> {
            throw new RuntimeException("async error");
        });

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(delegate).runAsync(captor.capture());

        Runnable wrapped = captor.getValue();
        wrapped.run();

        verify(logger).log(eq(Level.SEVERE), eq("异步任务执行异常"), any(RuntimeException.class));
    }

    @Test
    void runLaterException_isLogged() {
        scheduler.runLater(
                () -> {
                    throw new RuntimeException("later error");
                },
                10L);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(delegate).runLater(captor.capture(), eq(10L));

        Runnable wrapped = captor.getValue();
        wrapped.run();

        verify(logger).log(eq(Level.SEVERE), eq("异步任务执行异常"), any(RuntimeException.class));
    }

    @Test
    void runAsync_normalTask_executesSuccessfully() {
        Runnable task = mock(Runnable.class);
        scheduler.runAsync(task);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(delegate).runAsync(captor.capture());

        captor.getValue().run();
        verify(task).run();
    }
}
