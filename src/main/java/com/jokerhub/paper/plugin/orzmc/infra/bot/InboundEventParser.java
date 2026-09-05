package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EasyBotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * EasyBot 入站事件解析器（从 OrzEasyBot 抽离）。
 *
 * <p>解析 WebSocket 入站 JSON：区分系统帧（auth_ok / auth_failed / lagged / ping）与业务事件帧
 * （{@code message.inbound}），做大小/速率/平台/会话白名单校验，提取 {@code platform}/{@code text}/
 * {@code sender.role}/{@code chat_id} 后经 {@link BotInboundDispatcher} 分派到业务层。</p>
 *
 * <p>与 WebSocket 的连接状态耦合点仅两处：认证失败触发 {@link WebSocketLifecycle#shutdown()}、ping 帧
 * 经 {@link WebSocketLifecycle#currentClient()} 回 pong。</p>
 */
final class InboundEventParser {

    private static final String HEALTH_KEY = "easybot";
    private static final int MAX_INBOUND_PAYLOAD_CHARS = 64 * 1024;
    private static final int MAX_INBOUND_TEXT_CHARS = 8 * 1024;
    private static final int MAX_INBOUND_TARGET_CHARS = 512;
    private static final int MAX_INBOUND_EVENTS_PER_SECOND = 100;

    private final ServerLogger logger;
    private final ConfigService configService;
    private final BotInboundHandler inboundHandler;
    private final MessageFormatter formatter;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private final HttpSender httpSender;
    private final WebSocketLifecycle wsLifecycle;
    private final AtomicLong inboundWindowStart = new AtomicLong();
    private final AtomicInteger inboundWindowCount = new AtomicInteger();

    InboundEventParser(
            ServerLogger logger,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry,
            HttpSender httpSender,
            WebSocketLifecycle wsLifecycle) {
        this.logger = logger;
        this.configService = configService;
        this.inboundHandler = inboundHandler;
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
        this.healthRegistry = healthRegistry;
        this.httpSender = httpSender;
        this.wsLifecycle = wsLifecycle;
    }

    /**
     * 处理来自 EasyBot WebSocket 的入站事件。
     *
     * <p>所有平台的消息都通过此单一方法处理。EasyBot 已屏蔽协议差异，
     * 统一为 {platform, text, sender.role, chat_id} 格式。
     *
     * <p>系统帧（auth_ok / auth_failed / lagged）在此直接处理，不会传递到业务层。
     */
    void process(String jsonString) {
        if (jsonString == null || jsonString.isEmpty() || jsonString.length() > MAX_INBOUND_PAYLOAD_CHARS) {
            if (jsonString != null && jsonString.length() > MAX_INBOUND_PAYLOAD_CHARS) {
                throttledLogger.warning("easybot-inbound-size", "EasyBot 入站消息超过大小限制，已丢弃");
            }
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(jsonString);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            String type = stringValue(root, "type");
            if (type == null) {
                return;
            }

            // ---- 系统帧处理 ----
            if ("auth_ok".equals(type)) {
                healthRegistry.setWsConnected(HEALTH_KEY, true);
                healthRegistry.setLastError(HEALTH_KEY, null);
                throttledLogger.info("easybot-ws-auth", "EasyBot WebSocket 认证成功");
                return;
            }
            if ("auth_failed".equals(type)) {
                healthRegistry.setWsConnected(HEALTH_KEY, false);
                String msg = stringValue(root, "message");
                if (msg == null) msg = "unknown";
                healthRegistry.setLastError(HEALTH_KEY, "WS auth failed: " + msg);
                throttledLogger.error("easybot-ws-auth", "EasyBot WebSocket 认证失败: " + msg);
                wsLifecycle.shutdown();
                return;
            }
            if ("lagged".equals(type)) {
                int dropped = root.has("dropped") && root.get("dropped").isJsonPrimitive()
                        ? root.get("dropped").getAsInt()
                        : 0;
                throttledLogger.warning("easybot-ws-lag", "EasyBot WS 事件丢失: " + dropped);
                return;
            }
            if ("ping".equals(type)) {
                WsClient current = wsLifecycle.currentClient();
                if (current != null) {
                    current.send("{\"type\":\"pong\"}");
                }
                return;
            }

            // ---- 只处理事件帧 ----
            if (!"event".equals(type)) {
                return;
            }
            if (!root.has("event")) {
                return;
            }
            String eventType = stringValue(root, "event");
            if (eventType == null) {
                return;
            }
            if (!"message.inbound".equals(eventType)) {
                return;
            }
            if (!allowInboundEvent()) {
                throttledLogger.warning("easybot-inbound-rate", "EasyBot 入站消息超过速率限制，已丢弃");
                return;
            }

            // ---- 解析消息数据 ----
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                return;
            }
            JsonObject data = root.getAsJsonObject("data");

            // platform: 标识来源平台，如 "qq", "discord", "telegram"
            if (!data.has("platform")) {
                return;
            }
            String platformValue = stringValue(data, "platform");
            if (platformValue == null) {
                return;
            }
            String platform = platformValue.trim().toLowerCase(Locale.ROOT);
            if (platform.isEmpty() || platform.length() > 64) {
                return;
            }
            EasyBotConfig cfg = loadConfig();

            // 跳过已禁用平台的消息
            if (!isPlatformEnabled(cfg, platform)) {
                return;
            }

            // text: 消息内容
            String textValue = stringValue(data, "text");
            String text = textValue == null ? "" : textValue.trim();
            if (text.isEmpty() || text.length() > MAX_INBOUND_TEXT_CHARS) {
                if (text.length() > MAX_INBOUND_TEXT_CHARS) {
                    throttledLogger.warning("easybot-inbound-text-size", "EasyBot 入站文本超过大小限制，已丢弃");
                }
                return;
            }

            // chat_id: 来源会话标识
            String chatIdValue = stringValue(data, "chat_id");
            String chatId = chatIdValue == null ? "" : chatIdValue;
            if (chatId.isEmpty() || chatId.length() > MAX_INBOUND_TARGET_CHARS) {
                return;
            }
            String replyTarget = normalizeTarget(platform, chatId);
            if (!isInboundTargetAllowed(cfg, platform, replyTarget)) {
                throttledLogger.warning(
                        "easybot-inbound-target",
                        "EasyBot 忽略未授权会话消息: platform=" + platform + ", target=" + replyTarget);
                return;
            }

            // sender.role: 发送者角色（EasyBot 已各平台标准化为群主/管理员）；sender.nickname: 群昵称（审核人身份用）
            // isAdmin 判定 fail-closed：仅网关返回 Owner/Admin 视为管理员，role 缺失/未知一律按非管理员处理
            // （2026-08-19 决策：网关 role 即权威，无需额外白名单兜底，判断不了即降级为非管理员）
            boolean isAdmin = false;
            String senderName = null;
            if (data.has("sender") && data.get("sender").isJsonObject()) {
                JsonObject sender = data.getAsJsonObject("sender");
                String role = stringValue(sender, "role");
                if (role != null) {
                    isAdmin = "Owner".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
                }
                senderName = stringValue(sender, "nickname");
                if (senderName == null || senderName.isBlank()) {
                    senderName = stringValue(sender, "user_id"); // 无昵称时用平台 ID 兜底
                }
            }

            // 关键：sink 捕获来源平台和会话，确保回复定向到正确的位置
            Consumer<MessageEnvelope> sink = env -> {
                if (env != null) {
                    MessageEnvelope.Format replyFormat =
                            env.format() == null ? MessageEnvelope.Format.DEFAULT : env.format();
                    for (String part : formatter.format(env.message(), replyFormat)) {
                        httpSender.sendMessage(cfg, replyTarget, part);
                    }
                }
            };

            BotInboundDispatcher.dispatch(inboundHandler, text, isAdmin, senderName, sink);
        } catch (Exception e) {
            healthRegistry.setLastError(HEALTH_KEY, e.toString());
            logger.logger().warning("EasyBot inbound parse error: " + e);
        }
    }

    private EasyBotConfig loadConfig() {
        return EasyBotConfig.from(configService.getConfig("easybot"));
    }

    /**
     * 检查指定平台是否已在配置中启用。
     * 未找到配置的平台（如未注册的测试平台）视为禁用。
     */
    private boolean isPlatformEnabled(EasyBotConfig cfg, String platform) {
        EasyBotConfig.PlatformEntry entry = cfg.platforms().get(platform);
        return entry != null && entry.enabled();
    }

    private boolean isInboundTargetAllowed(EasyBotConfig cfg, String platform, String target) {
        EasyBotConfig.PlatformEntry entry = cfg.platforms().get(platform);
        // 内联判定已抽为 ImMessageRouter 共享（EasyBot/Builtin 双 driver 同一语义，方案 §6）
        return entry != null && ImMessageRouter.isInboundAllowed(toConversation(entry), target);
    }

    private static ImConversation toConversation(EasyBotConfig.PlatformEntry entry) {
        return new ImConversation(entry.enabled(), entry.adminGroup(), entry.playerGroup(), entry.adminDm());
    }

    private static String normalizeTarget(String platform, String chatId) {
        chatId = chatId.trim();
        String prefix = platform + ":";
        return chatId.startsWith(prefix) ? chatId : prefix + chatId;
    }

    private boolean allowInboundEvent() {
        long now = System.currentTimeMillis();
        long windowStart = inboundWindowStart.get();
        if (windowStart == 0L || now - windowStart >= 1000L) {
            if (inboundWindowStart.compareAndSet(windowStart, now)) {
                inboundWindowCount.set(0);
            }
        }
        return inboundWindowCount.incrementAndGet() <= MAX_INBOUND_EVENTS_PER_SECOND;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && !value.isJsonNull() ? value.getAsString() : null;
    }
}
