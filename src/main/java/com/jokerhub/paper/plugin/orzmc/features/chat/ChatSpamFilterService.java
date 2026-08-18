package com.jokerhub.paper.plugin.orzmc.features.chat;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 聊天反垃圾判定核心（纯逻辑，安全加固 P2-1）。
 *
 * <p>在 {@code AsyncChatEvent} 事件线程（异步）上对每条玩家消息做三类检查：</p>
 * <ol>
 *   <li><b>链接检测</b>：消息含 {@code http(s)://} 或 {@code www.} 开头的链接即判为广告；</li>
 *   <li><b>重复检测</b>：与本人上一条已放行消息完全相同即判为刷屏；</li>
 *   <li><b>限流</b>：60s 滑动窗口内发言超过 {@code max_messages_per_minute} 即判为刷屏。</li>
 * </ol>
 *
 * <p>状态按 {@link UUID} 分桶（{@link ConcurrentHashMap}），事件线程可安全并发；
 * 命中规则即丢弃该条消息（不再进入全局广播）。配置通过 {@link Supplier} 注入，
 * 事件侧每次读取最新配置（与 {@code CommandGuardService} 的 Supplier 模式一致），
 * /config reload 生效。</p>
 */
public final class ChatSpamFilterService {

    /** 链接识别：http(s):// 或 www. 开头后接非空白字符。 */
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s]+");

    /** 限流滑动窗口长度（毫秒）。 */
    private static final long WINDOW_MS = 60_000L;

    private final Supplier<ChatConfig> configSupplier;
    private final LongSupplier clock;
    /** playerId → 窗口内各条消息的发言时刻。 */
    private final Map<UUID, Deque<Long>> messageTimes = new ConcurrentHashMap<>();
    /** playerId → 上一条已放行消息（原文，重复检测）。 */
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();

    public ChatSpamFilterService(Supplier<ChatConfig> configSupplier) {
        this(configSupplier, System::currentTimeMillis);
    }

    /** 测试用：注入可控时钟以验证窗口滑动。 */
    ChatSpamFilterService(Supplier<ChatConfig> configSupplier, LongSupplier clock) {
        this.configSupplier = configSupplier;
        this.clock = clock;
    }

    /** 判定一条玩家消息是否应被丢弃（true = 刷屏/广告）。 */
    public boolean isSpam(UUID playerId, String message) {
        ChatConfig cfg = configSupplier.get();
        if (!cfg.enabled()) {
            return false;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        if (cfg.detectLinks() && LINK_PATTERN.matcher(message).find()) {
            return true;
        }
        if (cfg.detectRepeat() && message.equals(lastMessages.get(playerId))) {
            return true;
        }
        if (rateLimited(playerId, cfg.maxMessagesPerMinute())) {
            return true;
        }
        lastMessages.put(playerId, message);
        return false;
    }

    /** 玩家退出时清理其状态，避免无界增长。 */
    public void clear(UUID playerId) {
        messageTimes.remove(playerId);
        lastMessages.remove(playerId);
    }

    /** 60s 滑动窗口限流：窗口内条数达到上限即拒绝，否则记录本条发言时刻。 */
    private boolean rateLimited(UUID playerId, int maxPerMinute) {
        long now = clock.getAsLong();
        Deque<Long> times = messageTimes.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= maxPerMinute) {
                return true;
            }
            times.addLast(now);
        }
        return false;
    }
}
