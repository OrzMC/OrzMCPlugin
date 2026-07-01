package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.assembly.BotModule;
import com.jokerhub.paper.plugin.orzmc.assembly.FeatureModule;
import com.jokerhub.paper.plugin.orzmc.assembly.Initializable;
import com.jokerhub.paper.plugin.orzmc.assembly.MaintenanceModule;
import com.jokerhub.paper.plugin.orzmc.assembly.PlatformModule;
import com.jokerhub.paper.plugin.orzmc.assembly.PortalModule;

/**
 * 组合根 (Composition Root)。
 *
 * <p>负责按照依赖顺序创建所有领域模块，协调跨模块引用（如 BotModule ← MaintenanceModule
 * 的循环依赖），并驱动插件的完整生命周期。</p>
 *
 * <p>模块装配顺序：</p>
 * <ol>
 *   <li><b>PlatformModule</b> — 零依赖的基础设施（配置、日志、样式等）</li>
 *   <li><b>BotModule</b> — 依赖 PlatformModule，内部处理 Bot ↔ Notifier 循环</li>
 *   <li><b>PortalModule</b> — 依赖 PlatformModule（ConfigService）</li>
 *   <li><b>MaintenanceModule</b> — 依赖 PlatformModule + BotModule (Notifier)</li>
 *   <li><b>跨模块链接</b> — BotModule.linkWorldMaintenanceService()</li>
 *   <li><b>FeatureModule</b> — 依赖所有其他模块，创建 Feature 服务并注册命令/事件</li>
 * </ol>
 */
public final class OrzServices {

    private final PlatformModule platformModule;
    private final BotModule botModule;
    private final PortalModule portalModule;
    private final MaintenanceModule maintenanceModule;
    private final FeatureModule featureModule;

    private OrzServices(
            PlatformModule platformModule,
            BotModule botModule,
            PortalModule portalModule,
            MaintenanceModule maintenanceModule,
            FeatureModule featureModule) {
        this.platformModule = platformModule;
        this.botModule = botModule;
        this.portalModule = portalModule;
        this.maintenanceModule = maintenanceModule;
        this.featureModule = featureModule;
    }

    public static OrzServices assemble(OrzMC plugin) {
        // Phase 1: 零依赖平台基础设施
        PlatformModule platform = new PlatformModule(plugin);
        platform.setup(); // 初始化配置系统

        // Phase 2: Bot 和传送门（依赖 Platform）
        BotModule bot = new BotModule(platform);
        PortalModule portal = new PortalModule(platform);

        // Phase 3: 维护模块（依赖 Platform + Bot 的 Notifier）
        MaintenanceModule maintenance = new MaintenanceModule(platform, bot);

        // Phase 4: 设置跨模块回引用 —— 在 afterPropertiesSet 阶段注入
        bot.setWorldMaintenanceService(maintenance.worldMaintenanceService());

        // Phase 5: 触发所有 Initializable 模块的二阶段初始化
        if (bot instanceof Initializable) ((Initializable) bot).afterPropertiesSet();

        // Phase 6: 功能模块（依赖所有其他模块）
        FeatureModule feature = new FeatureModule(platform, bot, portal, maintenance);

        // Phase 7: 设置跨模块回引用（BlacklistService → BotCommandService）
        bot.botCommandService().setBlacklistService(feature.blacklistService());

        return new OrzServices(platform, bot, portal, maintenance, feature);
    }

    public void setupAll(OrzMC plugin) {
        // 各模块 setup（按依赖顺序）
        botModule.setup();
        portalModule.setup();

        // 注册事件监听器和命令
        featureModule.setupEventListeners(plugin);
        featureModule.setupCommandHandlers(plugin);

        // 应用白名单配置
        featureModule.enableForceWhitelist(plugin);
    }

    public void shutdownAll() {
        // 通知服务端关闭
        featureModule.notifyServerStop();

        // 逆序销毁模块
        botModule.tearDown();
        portalModule.tearDown();
        platformModule.tearDown();
    }

    // ---- Testing support ----

    /** 测试用：暴露组合根内部模块，避免集成测试使用反射。 */
    @org.jetbrains.annotations.VisibleForTesting
    public com.jokerhub.paper.plugin.orzmc.assembly.BotModule botModule() {
        return botModule;
    }
}
