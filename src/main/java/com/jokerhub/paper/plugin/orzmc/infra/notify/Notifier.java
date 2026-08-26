package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

public final class Notifier {
    private static final Logger LOGGER = Logger.getLogger("OrzMC.Notifier");
    private final ServerAccess server;
    private final BotMessageService botMessageService;
    private NotifierSink sink;

    public Notifier(ServerAccess server, BotMessageService botMessageService) {
        this.server = server;
        this.botMessageService = botMessageService;
        this.sink = new DefaultSink();
    }

    public void server(Component message) {
        sink.server(message);
    }

    public void event(String key, MessageEnvelope envelope) {
        sink.event(key, envelope);
    }

    public void registerSink(NotifierSink s) {
        sink = s == null ? sink : s;
    }

    private final class DefaultSink implements NotifierSink {
        @Override
        public void server(Component message) {
            server.server().sendMessage(message);
        }

        @Override
        public void event(String key, MessageEnvelope envelope) {
            routeEvent(key, envelope);
        }
    }

    public void routeEvent(String key, MessageEnvelope envelope) {
        if (key == null || envelope == null) {
            return;
        }
        MessageEnvelope.TargetType target =
                switch (key) {
                    case TemplateKeys.EXCEPTION_ALERT,
                            TemplateKeys.MAINTENANCE_BACKUP_ERROR,
                            TemplateKeys.MAINTENANCE_OPTIMIZE_ERROR,
                            TemplateKeys.COMMAND_GUARD_BLOCKED,
                            TemplateKeys.SECURITY_AUDIT,
                            TemplateKeys.LOGIN_RATE_LIMIT_ALERT,
                            TemplateKeys.EXPLOIT_BLOCKED,
                            TemplateKeys.IP_BLACKLIST_BLOCK,
                            TemplateKeys.PLAYER_NAME_BLOCK -> MessageEnvelope.TargetType.PRIVATE;
                    default -> MessageEnvelope.TargetType.PUBLIC;
                };
        botMessageService.send(envelope.withTargetType(target));
        // 群消息统一日志：所有通知类型（白名单拦截/IP黑名单/上下线/审核/异常等）渲染后的
        // 消息都记入服务器日志——E2E 断言 + 排查投递问题的一手证据（避免依赖 EasyBot API）。
        // ⏎ 转义换行：JUL→log4j 桥接只输出消息首行，转义后单行完整可读、E2E 好断言。
        // 发送本身已节流（whitelist_block 等高频类型有 ThrottledNotifier），日志跟随节流不会刷屏。
        LOGGER.info("[群消息:" + key + "] " + envelope.message().replace("\n", " ⏎ "));
    }
}
