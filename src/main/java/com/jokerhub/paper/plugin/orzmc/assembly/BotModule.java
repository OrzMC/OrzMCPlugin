package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.BotCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageServiceProvider;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthAccessor;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;

/**
 * Bot 消息模块。
 *
 * <p>管理 QQ/Discord/Lark 机器人适配器、消息路由和通知派发。
 * 内部处理 BotCommandService ← Notifier ← BotMessageService ← BotCommandService
 * 的循环依赖关系。</p>
 */
public final class BotModule implements ServiceModule, Initializable {

    private final BotCommandService botCommandService;
    private final BotMessageService botMessageService;
    private final Notifier notifier;
    private final BotStatusService botStatusService;
    private final HealthRegistry healthRegistry;

    public BotModule(PlatformModule platform) {
        this.healthRegistry = new HealthRegistry();
        // Phase A: 先创建 BotCommandService（核心依赖来自 PlatformModule）
        this.botCommandService = new BotCommandService(platform.serverFacade(), platform.configs());

        // Phase C: 创建 BotMessageService（以 BotCommandService 作为 BotInboundHandler）
        this.botMessageService = BotMessageServiceProvider.create(
                platform.serverFacade(),
                platform.serverFacade(),
                platform.serverFacade(),
                platform.configService(),
                platform.throttledLogger(),
                botCommandService,
                healthRegistry);

        // Phase D: 创建 Notifier（依赖 BotMessageService）
        this.notifier = new Notifier(platform.serverAccess(), platform.configService(), botMessageService);

        // BotStatusService
        this.botStatusService = new BotStatusService(platform.textStyles(), new HealthAccessor(healthRegistry));
    }

    // 跨模块回引用（通过 afterPropertiesSet 注入）
    private volatile com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService
            pendingMaintenanceService;

    /**
     * 设置跨模块依赖，将在 {@link #afterPropertiesSet()} 阶段注入。
     */
    public void setWorldMaintenanceService(
            com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService maintenanceService) {
        this.pendingMaintenanceService = maintenanceService;
    }

    @Override
    public void afterPropertiesSet() {
        if (pendingMaintenanceService != null) {
            botCommandService.setMaintenanceService(pendingMaintenanceService);
            pendingMaintenanceService = null;
        }
    }

    @Override
    public void setup() {
        botMessageService.setup();
    }

    @Override
    public void tearDown() {
        botMessageService.tearDown();
    }

    // --- Getters ---

    public BotCommandService botCommandService() {
        return botCommandService;
    }

    public BotMessageService botMessageService() {
        return botMessageService;
    }

    public Notifier notifier() {
        return notifier;
    }

    public BotStatusService botStatusService() {
        return botStatusService;
    }

    public BotInboundHandler botInboundHandler() {
        return botCommandService;
    }
}
