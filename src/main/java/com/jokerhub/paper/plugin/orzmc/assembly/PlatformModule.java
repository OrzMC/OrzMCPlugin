package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImWorkerPool;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.DefaultTypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.I18nConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.OrzLog4JCaptureAppender;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
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
    /** 多语言服务（P0）：内置语料 bundled ⊕ 数据目录覆盖层 custom；语言决议 + 文案读取。 */
    private final I18nService i18nService;

    private final ThrottledLogger throttledLogger;
    private final ThrottledNotifier throttledNotifier;
    private final HealthRegistry healthRegistry;
    private final LogCaptureService logCaptureService;
    private OrzLog4JCaptureAppender logCaptureAppender;
    /** 危险命令判定核心（安全加固）：BotModule（$e）与 FeatureModule（事件）共享同一实例。 */
    private final CommandGuardService commandGuardService;
    /** 命令审计日志（安全加固 P0-4）：audit/command_audit.log，全入口共享同一实例。 */
    private final CommandAuditService commandAuditService;

    public PlatformModule(OrzMC plugin) {
        this.serverFacade = new ServerFacade(plugin);
        this.configService = new ConfigService(plugin);
        this.configs = new DefaultTypedConfigProvider(configService);
        this.textStyles = new OrzTextStyles(configService);
        this.i18nService = new I18nService(
                plugin.getClass().getClassLoader(),
                configService.dataFolder().toPath(),
                () -> I18nConfig.from(configService.getConfig("config").getConfigurationSection("i18n")),
                plugin.getLogger());
        this.throttledLogger = new ThrottledLogger(configService, plugin.getLogger());
        this.throttledNotifier = new ThrottledNotifier();
        this.healthRegistry = new HealthRegistry();
        this.logCaptureService = new LogCaptureService(LOG_CAPTURE_CAPACITY);
        // 危险命令 guard + 审计：零 Bukkit 依赖的纯服务，由平台模块统一持有（配置热重载经 Supplier 生效）
        this.commandGuardService = new CommandGuardService(() -> configs.securityGuard(), i18nService);
        this.commandAuditService = new CommandAuditService(
                () -> configs.securityGuard().auditEnabled(),
                configService.dataFolder().toPath().resolve("audit"),
                CommandAuditService.DEFAULT_MAX_BYTES,
                plugin.getLogger());
    }

    /** 日志环形缓冲容量（$e 命令输出窗口收集）。 */
    private static final int LOG_CAPTURE_CAPACITY = 500;

    @Override
    public void setup() {
        configService.setup();
        com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nServiceHolder.init(i18nService);
        reportI18nHealth();
        attachLogCaptureAppender();
    }

    /** 内置语言包一致性检查（启动告警；运行时单测由 I18nCatalogConsistencyTest 守护）。 */
    private void reportI18nHealth() {
        List<String> issues = i18nService.health();
        if (!issues.isEmpty()) {
            serverFacade.logger().warning("i18n 语言包健康检查发现问题:");
            for (String issue : issues) {
                serverFacade.logger().warning(" - " + issue);
            }
        }
    }

    @Override
    public void tearDown() {
        detachLogCaptureAppender();
        commandAuditService.shutdown();
        AsyncHttp.shutdown();
        ImWorkerPool.shutdown();
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

    public I18nService i18nService() {
        return i18nService;
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

    public CommandGuardService commandGuardService() {
        return commandGuardService;
    }

    public CommandAuditService commandAuditService() {
        return commandAuditService;
    }
}
