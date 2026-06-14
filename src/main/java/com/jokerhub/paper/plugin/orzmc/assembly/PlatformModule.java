package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.DefaultTypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;

/**
 * 平台基础设施模块。
 *
 * <p>零依赖的基础模块，提供核心基础设施能力：
 * 服务器门面、配置服务、类型化配置、文本样式、限流日志与通知。</p>
 */
public final class PlatformModule implements ServiceModule {

    private final ServerFacade serverFacade;
    private final ConfigService configService;
    private final DefaultTypedConfigProvider configs;
    private final OrzTextStyles textStyles;
    private final ThrottledLogger throttledLogger;
    private final ThrottledNotifier throttledNotifier;

    public PlatformModule(OrzMC plugin) {
        this.serverFacade = new ServerFacade(plugin);
        this.configService = new ConfigService(plugin);
        this.configs = new DefaultTypedConfigProvider(configService);
        this.textStyles = new OrzTextStyles(configService);
        this.throttledLogger = new ThrottledLogger(configService, plugin.getLogger());
        this.throttledNotifier = new ThrottledNotifier(configService);
    }

    @Override
    public void setup() {
        configService.setup();
    }

    @Override
    public void tearDown() {
        configService.tearDown();
    }

    // --- Getters ---

    public ServerFacade serverFacade() {
        return serverFacade;
    }

    public ServerAccess serverAccess() {
        return serverFacade;
    }

    public ServerLogger serverLogger() {
        return serverFacade;
    }

    public ServerScheduler serverScheduler() {
        return serverFacade;
    }

    public ConfigService configService() {
        return configService;
    }

    public TypedConfigProvider configs() {
        return configs;
    }

    public OrzTextStyles textStyles() {
        return textStyles;
    }

    public ThrottledLogger throttledLogger() {
        return throttledLogger;
    }

    public ThrottledNotifier throttledNotifier() {
        return throttledNotifier;
    }
}
