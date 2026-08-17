package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.DefaultTypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.OrzLog4JCaptureAppender;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

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
    private final HealthRegistry healthRegistry;
    private final LogCaptureService logCaptureService;
    private OrzLog4JCaptureAppender logCaptureAppender;

    public PlatformModule(OrzMC plugin) {
        this.serverFacade = new ServerFacade(plugin);
        this.configService = new ConfigService(plugin);
        this.configs = new DefaultTypedConfigProvider(configService);
        this.textStyles = new OrzTextStyles(configService);
        this.throttledLogger = new ThrottledLogger(configService, plugin.getLogger());
        this.throttledNotifier = new ThrottledNotifier();
        this.healthRegistry = new HealthRegistry();
        this.logCaptureService = new LogCaptureService(LOG_CAPTURE_CAPACITY);
    }

    /** 日志环形缓冲容量（$e 命令输出窗口收集）。 */
    private static final int LOG_CAPTURE_CAPACITY = 500;

    @Override
    public void setup() {
        configService.setup();
        attachLogCaptureAppender();
    }

    @Override
    public void tearDown() {
        detachLogCaptureAppender();
        configService.tearDown();
    }

    /**
     * 注册 Log4J root Appender，把服务器日志喂给 {@link LogCaptureService}。
     * 环境异常（如测试容器无 Log4J）时降级为仅警告，不影响插件启动。
     */
    private void attachLogCaptureAppender() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            OrzLog4JCaptureAppender appender = new OrzLog4JCaptureAppender(logCaptureService);
            appender.start();
            context.getRootLogger().addAppender(appender);
            logCaptureAppender = appender;
        } catch (Exception e) {
            logCaptureAppender = null;
            serverFacade.logger().warning("Log4J 日志捕获 Appender 注册失败，$e 输出兜底不可用: " + e.getMessage());
        }
    }

    /** 注销 Log4J Appender（插件卸载时）。 */
    private void detachLogCaptureAppender() {
        if (logCaptureAppender == null) {
            return;
        }
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getRootLogger().removeAppender(logCaptureAppender);
            logCaptureAppender.stop();
        } catch (Exception e) {
            serverFacade.logger().warning("Log4J 日志捕获 Appender 注销失败: " + e.getMessage());
        } finally {
            logCaptureAppender = null;
        }
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

    public HealthRegistry healthRegistry() {
        return healthRegistry;
    }

    public LogCaptureService logCaptureService() {
        return logCaptureService;
    }
}
