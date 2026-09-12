package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IM 通道「阻塞式 IO 卸载」专用线程池（线程卫生 R12 配套）。
 *
 * <p>用途：平台适配层存在两类必须在<b>非服务器线程</b>执行、且内部会 {@code join()} 等待 HTTP 的短任务：</p>
 * <ul>
 *   <li>平台角色判定（TG/Discord/飞书：一次判定可能串行多次 REST，见各 {@code *RoleResolver}）；</li>
 *   <li>短期令牌刷新（QQ/飞书 {@code TokenProvider.fresh()} 临期时会同步换发 token）。</li>
 * </ul>
 *
 * <p>这些任务此前跑在 {@link java.util.concurrent.ForkJoinPool#commonPool()}（{@code supplyAsync} 默认执行器）：
 * commonPool 并行度仅为「核数 - 1」，一旦被若干阻塞任务占满，会连带饿死同池的其它使用方（并行流、其他插件）。
 * 本池提供<b>有界、具名、守护</b>的专用线程，隔离该风险；线程数取 4（远大于实际并发：入站命令为低频人工触发，
 * 令牌刷新单飞）。</p>
 *
 * <p>生命周期：守护线程，插件卸载时经 {@link #shutdown()} 回收（{@link PlatformModule} 与
 * {@code AsyncHttp.shutdown()} 同处调用）；关闭后再次 {@link #executor()} 会惰性重建（幂等，便于测试）。</p>
 */
public final class ImWorkerPool {

    /** 池大小：阻塞任务并发上限（低频场景，无需更大）。 */
    private static final int POOL_SIZE = 4;

    private static final Object LOCK = new Object();

    private static volatile ExecutorService executor;

    private ImWorkerPool() {}

    /** 获取（必要时惰性创建）专用执行器。 */
    public static Executor executor() {
        ExecutorService local = executor;
        if (local != null && !local.isShutdown()) {
            return local;
        }
        synchronized (LOCK) {
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newFixedThreadPool(POOL_SIZE, new NamedDaemonFactory());
            }
            return executor;
        }
    }

    /** 关闭池（插件卸载）；幂等，关闭后下次 {@link #executor()} 重建。 */
    public static void shutdown() {
        synchronized (LOCK) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }

    /** 具名守护线程工厂（诊断友好：jstack 内可直接识别 IM 阻塞任务）。 */
    private static final class NamedDaemonFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "orzmc-im-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
