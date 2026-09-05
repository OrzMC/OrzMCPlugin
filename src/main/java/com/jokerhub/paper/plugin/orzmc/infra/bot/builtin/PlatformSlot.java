package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * builtin 单平台生命周期槽（F1 装配泛化：BuiltinImDriver 由「硬编码单 QQ 平台」泛化为槽列表，逐平台挂载）。
 *
 * <p>职责：按当前 im.yml {@code platforms.<id>} 配置 reconcile 一个 {@link BuiltinPlatform} 实例——
 * 配置可用（{@code usable} 判定通过）则（首次）启动、凭据变化则停旧建新、不可用则停止并告警。
 * 会话绑定等运行时依赖经 factory 闭包实时读（不在此槽缓存），配置级变更才触发重建。</p>
 *
 * @param <C> 平台配置类型（record，含 usable 判定与值相等性；如 {@code QqPlatformConfig}）
 */
final class PlatformSlot<C> {

    private final String platform;
    private final Function<ConfigService, C> reader;
    private final Predicate<C> usable;
    private final Function<C, BuiltinPlatform> factory;
    private final ServerLogger logger;

    private volatile BuiltinPlatform current;
    private volatile C currentCfg;

    /**
     * @param platform 平台标识（如 {@code qq}；与会话绑定 sessions 键 / target 前缀同构）
     * @param reader 从 im.yml 读 {@code platforms.<id>} 段 → 配置对象（段缺失 → DISABLED 常量）
     * @param usable 配置可用性判定（enabled 且凭据齐备）
     * @param factory 配置 → 平台适配器（会话绑定等运行时依赖在闭包内实时读）
     */
    PlatformSlot(
            String platform,
            Function<ConfigService, C> reader,
            Predicate<C> usable,
            Function<C, BuiltinPlatform> factory,
            ServerLogger logger) {
        this.platform = platform;
        this.reader = reader;
        this.usable = usable;
        this.factory = factory;
        this.logger = logger;
    }

    String platform() {
        return platform;
    }

    /** 当前运行中的平台适配器（未启动/已停止 → null）。 */
    BuiltinPlatform current() {
        return current;
    }

    /** 按最新 im.yml 配置 reconcile：可用 →（首次）启动 / 凭据变化停旧建新；不可用 → 停止。 */
    void reconcile(ConfigService configService) {
        C cfg = reader.apply(configService);
        boolean ok = cfg != null && usable.test(cfg);
        if (ok) {
            if (current == null) {
                start(cfg);
            } else if (!currentCfg.equals(cfg)) {
                logger.logger().warning("[builtin] " + platform + " 平台凭据/配置变化，停旧建新");
                stop();
                start(cfg);
            }
        } else if (current != null) {
            logger.logger().warning("[builtin] " + platform + " 平台不可用（enabled 或凭据缺失），已停用该平台");
            stop();
        }
    }

    /** 停止并清理（幂等）。 */
    void stop() {
        if (current != null) {
            current.stop();
            current = null;
            currentCfg = null;
        }
    }

    private void start(C cfg) {
        BuiltinPlatform created = factory.apply(cfg);
        created.start();
        current = created;
        currentCfg = cfg;
    }
}
