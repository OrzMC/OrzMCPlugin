package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.BuiltinImDriver;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.DiscordPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.FeishuPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImGatewayConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.QqPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TelegramPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;

public final class BotMessageServiceProvider {
    private BotMessageServiceProvider() {}

    public static BotMessageService create(
            ServerLogger logger,
            ServerScheduler scheduler,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler,
            HealthRegistry healthRegistry) {
        // 按 im.yml 选择通道（方案 D1/D2/D3）：easybot=现状兜底；builtin=内置直连（平台可配时启用）
        ImGatewayConfig im = ImGatewayConfig.from(configService.getConfig("im"));
        if (im.isBuiltin()) {
            if (anyPlatformUsable(configService)) {
                logger.logger().info("IM backend=builtin：启用内置直连（可用平台：" + usablePlatformNames(configService) + "）。");
                return new BuiltinImDriver(
                        logger, scheduler, configService, inboundHandler, new PlainMessageFormatter(), healthRegistry);
            }
            logger.logger()
                    .warning("IM backend=builtin 已选择，但无任何可用平台（QQ/飞书/Telegram/Discord 需 enabled 且凭据齐备）"
                            + "——已停用群功能（D3，可改回 easybot）。");
            return new UnavailableBotMessageService(logger, healthRegistry);
        }
        return new OrzEasyBot(
                logger, configService, inboundHandler, new PlainMessageFormatter(), throttledLogger, healthRegistry);
    }

    /** 可用平台名（逗号分隔，供启用日志展示）。 */
    private static String usablePlatformNames(ConfigService configService) {
        StringBuilder names = new StringBuilder();
        if (qqPlatform(configService).usable()) {
            names.append("qq");
        }
        if (feishuPlatform(configService).usable()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append("feishu");
        }
        if (telegramPlatform(configService).usable()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append("telegram");
        }
        if (discordPlatform(configService).usable()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append("discord");
        }
        return names.length() == 0 ? "-" : names.toString();
    }

    /** backend=builtin 时是否有任一可用平台（builtin 内部逐平台 reconcile；全部不可用才返回 Unavailable）。 */
    private static boolean anyPlatformUsable(ConfigService configService) {
        return qqPlatform(configService).usable()
                || feishuPlatform(configService).usable()
                || telegramPlatform(configService).usable()
                || discordPlatform(configService).usable();
    }

    private static QqPlatformConfig qqPlatform(ConfigService configService) {
        if (configService.getConfig("im") == null) {
            return QqPlatformConfig.DISABLED;
        }
        return QqPlatformConfig.from(configService.getConfig("im").getConfigurationSection("platforms.qq"));
    }

    private static FeishuPlatformConfig feishuPlatform(ConfigService configService) {
        if (configService.getConfig("im") == null) {
            return FeishuPlatformConfig.DISABLED;
        }
        return FeishuPlatformConfig.from(configService.getConfig("im").getConfigurationSection("platforms.feishu"));
    }

    private static TelegramPlatformConfig telegramPlatform(ConfigService configService) {
        if (configService.getConfig("im") == null) {
            return TelegramPlatformConfig.DISABLED;
        }
        org.bukkit.configuration.ConfigurationSection im = configService.getConfig("im");
        return TelegramPlatformConfig.from(
                im.getConfigurationSection("platforms.telegram"), im.getConfigurationSection("proxy"));
    }

    private static DiscordPlatformConfig discordPlatform(ConfigService configService) {
        if (configService.getConfig("im") == null) {
            return DiscordPlatformConfig.DISABLED;
        }
        org.bukkit.configuration.ConfigurationSection im = configService.getConfig("im");
        return DiscordPlatformConfig.from(
                im.getConfigurationSection("platforms.discord"), im.getConfigurationSection("proxy"));
    }
}
