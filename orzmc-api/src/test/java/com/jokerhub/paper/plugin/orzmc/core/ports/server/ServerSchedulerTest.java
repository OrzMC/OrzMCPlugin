package com.jokerhub.paper.plugin.orzmc.core.ports.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServerSchedulerTest {

    @Test
    void runSync_executesTask() {
        AtomicInteger executed = new AtomicInteger(0);

        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                task.run();
            }
        };

        scheduler.runSync(() -> executed.incrementAndGet());
        assertEquals(1, executed.get());
    }

    @Test
    void runAsync_executesTask() {
        List<String> results = new ArrayList<>();

        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                task.run();
            }
        };

        scheduler.runAsync(() -> results.add("async"));
        assertEquals(1, results.size());
        assertEquals("async", results.get(0));
    }

    @Test
    void runLater_executesWithDelay() {
        AtomicInteger delayCaptured = new AtomicInteger(-1);

        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                delayCaptured.set((int) delayTicks);
                task.run();
            }
        };

        AtomicInteger executed = new AtomicInteger(0);
        scheduler.runLater(() -> executed.incrementAndGet(), 20L);
        assertEquals(1, executed.get());
        assertEquals(20, delayCaptured.get());
    }

    @Test
    void runLater_zeroDelay_executesTask() {
        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                task.run();
            }
        };

        AtomicInteger executed = new AtomicInteger(0);
        scheduler.runLater(() -> executed.incrementAndGet(), 0L);
        assertEquals(1, executed.get());
    }

    @Test
    void allMethods_isolated() {
        List<String> executionOrder = new ArrayList<>();

        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                executionOrder.add("sync");
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                executionOrder.add("async");
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                executionOrder.add("later:" + delayTicks);
                task.run();
            }
        };

        scheduler.runSync(() -> {});
        scheduler.runAsync(() -> {});
        scheduler.runLater(() -> {}, 10L);

        assertEquals(3, executionOrder.size());
        assertEquals("sync", executionOrder.get(0));
        assertEquals("async", executionOrder.get(1));
        assertEquals("later:10", executionOrder.get(2));
    }

    @Test
    void runLater_negativeDelay_notSpecified() {
        // 合约未规定负数 delay 的行为，实现应能处理
        ServerScheduler scheduler = new ServerScheduler() {
            @Override
            public void runSync(Runnable task) {
                task.run();
            }

            @Override
            public void runAsync(Runnable task) {
                task.run();
            }

            @Override
            public void runLater(Runnable task, long delayTicks) {
                // 负数 delay 按立即执行处理
                task.run();
            }
        };

        assertDoesNotThrow(() -> scheduler.runLater(() -> {}, -1L));
    }
}
