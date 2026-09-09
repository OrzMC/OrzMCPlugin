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
 * <p>管理 EasyBot 网关适配器、消息路由和通知派发。
 * 内部处理 BotCommandService ← Notifier ← BotMessageService ← BotCommandService
 * 的循环依赖关系。跨模块依赖（维护/黑名单/审核/权限/日志窗口/命令守卫）由组合根在
 * {@link BotCommandService#injectDependencies} 中一次性注入，本模块不再持有二阶段 setter。</p>
 */
public final class BotModule implements ServiceModule {

    private final BotCommandService botCommandService;
    private final BotMessageService botMessageService;
    private final Notifier notifier;
    private final BotStatusService botStatusService;
    private final HealthRegistry healthRegistry;
    private final HealthAccessor healthAccessor;

    public BotModule(PlatformModule platform) {
        this.healthRegistry = new HealthRegistry();
        // Phase A: 先创建 BotCommandService（核心依赖来自 PlatformModule）。
        // 必须注入真实 I18nService（PlatformModule 已装配）：否则 BotCommandFeedbackService 回落 zh
        // fallback，$h / $cmd ? 等群帮助恒中文（2026-09-08 #41x 缺陷：$ 帮助不随 default_lang）。
        this.botCommandService =
                new BotCommandService(platform.serverFacade(), platform.configs(), platform.i18nService());

        // Phase C: 创建 BotMessageService（以 BotCommandService 作为 BotInboundHandler）
        this.botMessageService = BotMessageServiceProvider.create(
                platform.serverFacade(),
                platform.serverFacade(), // 同实例：ServerLogger + ServerScheduler（builtin 入站 R12 调度用）
                platform.configService(),
                platform.throttledLogger(),
                botCommandService,
                healthRegistry);

        // Phase D: 创建 Notifier（依赖 BotMessageService）
        this.notifier = new Notifier(platform.serverAccess(), botMessageService);

        // BotStatusService
        this.healthAccessor = new HealthAccessor(healthRegistry);
        this.botStatusService = new BotStatusService(platform.textStyles(), healthAccessor, platform.i18nService());
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

    /** 健康只读视图（/bot 与 im status 共用，key 如 easybot / builtin.qq）。 */
    public HealthAccessor healthAccessor() {
        return healthAccessor;
    }

    public BotInboundHandler botInboundHandler() {
        return botCommandService;
    }
}
