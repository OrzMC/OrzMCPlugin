package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.BuiltinPlatform;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImProxyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TelegramPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Telegram 平台适配器（builtin，批次 5a）：长轮询 getUpdates 循环 + 下行 sendMessage + 入站处理。
 *
 * <p>与 QQ/飞书（WS 上行网关）不同，TG 为<b>免公网入站的长轮询</b>（官方 Bot API，R8）——本类持有一个
 * 单线程 executor（{@code im-telegram-poll}）驱动轮询循环：每次 getUpdates（timeout 秒长挂）→ 逐条推进
 * offset 并把文本用户消息交 {@link TelegramInboundProcessor}（门槛/调度/R12）；失败退避后重试（R8）。
 * 启动自检 getMe（401=配置错误 → 健康降级 + 停止轮询）。</p>
 *
 * <p>代理（D13）：HTTP 经 {@code ImProxyConfig} 解析的 {@link java.net.Proxy} 透传（TG 域名国内不可达时
 * 必须配；直连则 NO_PROXY）。凭据（{@link TelegramPlatformConfig}）构造时消费一次，token 为长期静态
 * （无刷新语义，401=配置错误告警停用）。健康 key {@code builtin.telegram}。</p>
 */
public final class TelegramBuiltinAdapter implements BuiltinPlatform {

    public static final String HEALTH_KEY = "builtin.telegram";

    /** getUpdates 长轮询挂起秒数（官方上限：short polling 默认 0，long polling 建议 ≤50）。 */
    private static final int POLL_TIMEOUT_SECS = 30;
    /** 轮询失败退避间隔（网络抖动/HTTP 5xx 后等待再试）。 */
    private static final long RETRY_DELAY_MS = 5_000;

    private final Logger log;
    private final HealthRegistry health;
    private final TelegramApiClient api;
    private final TelegramInboundProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean fatal = new AtomicBoolean();
    private volatile ScheduledExecutorService poller;
    /** 下轮 getUpdates offset（R8：严格推进 update_id+1；启动首拉丢弃积压见 pollOnce）。 */
    private volatile long nextOffset;

    public TelegramBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            TelegramPlatformConfig cfg) {
        this(serverLogger, scheduler, inbound, formatter, conversation, health, cfg, null);
    }

    /**
     * @param discovery 未绑定会话发现候选（可为 null；D11：候选进 status 提示）
     */
    public TelegramBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            TelegramPlatformConfig cfg,
            ImDiscoveryCandidates discovery) {
        if (serverLogger == null || scheduler == null || inbound == null || health == null) {
            throw new IllegalArgumentException("必需依赖不能为 null");
        }
        if (cfg == null || !cfg.usable()) {
            throw new IllegalArgumentException("telegram 平台需 enabled 且 token 齐备（usable）");
        }
        this.log = serverLogger.logger();
        this.health = health;
        java.net.Proxy proxy = resolveProxy(cfg);
        this.api = new TelegramApiClient(cfg.token(), TelegramApiClient.DEFAULT_API_BASE, proxy, log);
        this.processor = new TelegramInboundProcessor(
                log,
                scheduler,
                conversation,
                inbound,
                formatter,
                new TelegramRoleResolver(log, api),
                this::sendReply,
                discovery);
    }

    /** 生效代理解析（cfg.proxy 已合并全局段；null/DIRECT → 直连）。 */
    private static java.net.Proxy resolveProxy(TelegramPlatformConfig cfg) {
        ImProxyConfig proxy = cfg.proxy();
        return proxy == null ? java.net.Proxy.NO_PROXY : proxy.toProxy();
    }

    @Override
    public String platform() {
        return "telegram";
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return; // 幂等
        }
        fatal.set(false);
        health.setEnabled(HEALTH_KEY, true);
        health.setApiReady(HEALTH_KEY, false);
        startPoller();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return; // 幂等
        }
        ScheduledExecutorService p = poller;
        if (p != null) {
            p.shutdownNow();
            try {
                p.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            poller = null;
        }
        health.setEnabled(HEALTH_KEY, false);
        health.setApiReady(HEALTH_KEY, false);
        health.setLastError(HEALTH_KEY, null);
    }

    @Override
    public void reconnectIfNeeded() {
        if (!running.get() || fatal.get()) {
            start();
            return;
        }
        if (poller == null || poller.isShutdown()) {
            startPoller(); // 轮询线程意外终止 → 重启
        }
    }

    /** 启动轮询线程：先 getMe 自检（401=配置错误 → 健康降级停用），通过后进入循环。 */
    private void startPoller() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(threadFactory());
        poller = executor;
        executor.execute(() -> {
            String botName = api.getMe();
            if (botName == null) {
                fatal.set(true);
                health.setApiReady(HEALTH_KEY, false);
                health.setLastError(HEALTH_KEY, "telegram getMe 自检失败（token 无效或网络不可达），已停用轮询");
                log.warning("[telegram] getMe 自检失败（token 无效或网络不可达），已停用轮询（检查 im.yml platforms.telegram.token 与代理配置）");
                running.set(false);
                return;
            }
            log.info("[telegram] 启动成功（bot @" + botName + "），开始长轮询");
            health.setApiReady(HEALTH_KEY, true);
            health.setLastError(HEALTH_KEY, null);
            pollLoop();
        });
    }

    private void pollLoop() {
        while (running.get() && !fatal.get()) {
            long startedPoll = System.currentTimeMillis();
            TelegramApiClient.GetUpdatesResult result = api.getUpdates(nextOffset, POLL_TIMEOUT_SECS);
            if (!running.get()) {
                return; // stop() 期间
            }
            if (result.error() != null && result.error().httpStatus() == 401) {
                // token 配置错误（长期凭据无刷新）：停用轮询，健康告警
                fatal.set(true);
                health.setApiReady(HEALTH_KEY, false);
                health.setLastError(HEALTH_KEY, "telegram token 无效（401），已停用轮询");
                log.warning("[telegram] token 无效（401，BotFather 检查 token 或重新生成），已停用轮询");
                running.set(false);
                return;
            }
            if (!result.ok()) {
                health.setApiReady(HEALTH_KEY, false);
                log.warning("[telegram] getUpdates 失败，退避重试: " + (result.error() == null ? "unknown" : result.error()));
                sleepQuietly(RETRY_DELAY_MS);
                continue;
            }
            health.setApiReady(HEALTH_KEY, true);
            health.setLastError(HEALTH_KEY, null);
            if (result.updates().isEmpty()) {
                // 长轮询超时无新事件：保持 offset（getUpdates 返回空 result 时不推进）
                continue;
            }
            for (var rawUpdate : result.updates()) {
                if (!running.get()) {
                    return;
                }
                TelegramInboundParser.TelegramUpdate parsed = TelegramInboundParser.parse(rawUpdate.toString());
                if (parsed.updateId() >= 0) {
                    // R8：严格推进 offset=update_id+1（无论消息是否被处理——防重启重拉积压）
                    nextOffset = parsed.updateId() + 1;
                }
                TelegramInboundMessage message = parsed.message();
                if (message != null) {
                    processor.onMessage(message);
                }
            }
            // 长轮询事件间隔（心跳节奏）；本轮耗时短于轮询超时（事件到达）时不额外等待
            long elapsed = System.currentTimeMillis() - startedPoll;
            if (elapsed < 100 && running.get()) {
                sleepQuietly(50); // 防空转：事件洪峰后稍歇
            }
        }
    }

    @Override
    public void send(String target, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Long chatId = parseChatId(target);
        if (chatId == null) {
            if (target != null && target.startsWith("telegram:")) {
                log.warning("[telegram] 无法识别的 target: " + target);
            }
            return;
        }
        fire(chatId, text, false);
    }

    /** 被动回复（chat_id 直发——TG 语义，无被动回复通道）。 */
    private void sendReply(long chatId, String text) {
        fire(chatId, text, true);
    }

    /** 尽力一次（D7：失败/异常 → 健康告警 + 日志，不重试）。 */
    private void fire(long chatId, String text, boolean reply) {
        try {
            boolean ok = api.sendMessage(chatId, text);
            if (!ok) {
                health.setLastError(HEALTH_KEY, "telegram 发送失败 chat_id=" + chatId);
                log.warning("[telegram] 发送失败 chat_id=" + chatId + (reply ? "（回复）" : ""));
            }
        } catch (RuntimeException e) {
            health.setLastError(HEALTH_KEY, "telegram 发送异常 chat_id=" + chatId + " " + e);
            log.warning("[telegram] 发送异常 chat_id=" + chatId + (reply ? "（回复）" : "") + " " + e);
        }
    }

    /** target {@code telegram:<chatType>:<chatId>} → chatId（long）；非 telegram 前缀/格式错误 → null。 */
    private static Long parseChatId(String target) {
        if (target == null || !target.startsWith("telegram:")) {
            return null;
        }
        String rest = target.substring("telegram:".length());
        int sep = rest.indexOf(':');
        if (sep <= 0 || sep == rest.length() - 1) {
            return null;
        }
        String chatType = rest.substring(0, sep);
        // 仅群/单聊两类（防任意值）：出站均以 chat_id 投递
        if (!TelegramInboundMessage.CHAT_TYPE_GROUP.equals(chatType)
                && !TelegramInboundMessage.CHAT_TYPE_USER.equals(chatType)) {
            return null;
        }
        try {
            return Long.parseLong(rest.substring(sep + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory threadFactory() {
        return r -> {
            Thread t = new Thread(r, "im-telegram-poll");
            t.setDaemon(true);
            return t;
        };
    }
}
