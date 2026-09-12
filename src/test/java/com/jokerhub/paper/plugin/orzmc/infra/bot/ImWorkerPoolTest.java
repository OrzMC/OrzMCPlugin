package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * IM 阻塞任务线程池单测：具名 + 守护线程（诊断友好、不阻止卸载），shutdown 幂等且可惰性重建。
 */
class ImWorkerPoolTest {

    @AfterEach
    void tearDown() {
        ImWorkerPool.shutdown();
    }

    @Test
    void executor_runsTasksOnNamedDaemonThreads() throws Exception {
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Executor pool = ImWorkerPool.executor();

        pool.execute(() -> {
            thread.set(Thread.currentThread());
            done.countDown();
        });

        assertTrue(done.await(3, TimeUnit.SECONDS), "任务应在专用池内执行");
        assertTrue(
                thread.get().getName().startsWith("orzmc-im-worker-"),
                "线程应具名: " + thread.get().getName());
        assertTrue(thread.get().isDaemon(), "线程应为守护线程（不阻止 JVM/插件卸载）");
    }

    @Test
    void shutdown_isIdempotentAndPoolIsRebuiltLazily() {
        Executor first = ImWorkerPool.executor();
        ImWorkerPool.shutdown();
        ImWorkerPool.shutdown(); // 幂等

        Executor second = ImWorkerPool.executor();
        assertNotSame(first, second, "关闭后应惰性重建，避免后续收到 RejectedExecutionException");
        assertFalse(((java.util.concurrent.ExecutorService) second).isShutdown());
    }

    @Test
    void executor_reusesSamePoolWhileAlive() {
        assertEquals(ImWorkerPool.executor(), ImWorkerPool.executor(), "存活期内应复用同一池（不无限建池）");
    }
}
