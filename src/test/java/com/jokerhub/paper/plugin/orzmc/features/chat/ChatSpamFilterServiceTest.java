package com.jokerhub.paper.plugin.orzmc.features.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class ChatSpamFilterServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private static ChatSpamFilterService service(ChatConfig config) {
        return new ChatSpamFilterService(() -> config);
    }

    private static ChatSpamFilterService service(ChatConfig config, LongSupplier clock) {
        return new ChatSpamFilterService(() -> config, clock);
    }

    private static ChatConfig defaultConfig() {
        return new ChatConfig(true, 6, true, true, "请勿刷屏或发送广告");
    }

    // ---- 总开关 ----

    @Test
    void disabledConfig_allowsLinksAndFlood() {
        ChatSpamFilterService svc = service(new ChatConfig(false, 1, true, true, "请勿刷屏或发送广告"));
        assertFalse(svc.isSpam(PLAYER, "看看 http://example.com 吧"));
        assertFalse(svc.isSpam(PLAYER, "https://example.com"));
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
    }

    @Test
    void nullOrBlank_neverBlocked() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertFalse(svc.isSpam(PLAYER, null));
        assertFalse(svc.isSpam(PLAYER, "  "));
    }

    // ---- 链接检测 ----

    @Test
    void httpUrl_blocked() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertTrue(svc.isSpam(PLAYER, "加群看视频 http://t.cn/abc"));
        assertTrue(svc.isSpam(PLAYER, "官网 https://example.com/join"));
    }

    @Test
    void wwwDomain_blocked() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertTrue(svc.isSpam(PLAYER, "进服 www.example.com"));
    }

    @Test
    void linkDetectionDisabled_allowsLink() {
        ChatSpamFilterService svc = service(new ChatConfig(true, 6, false, true, "请勿刷屏或发送广告"));
        assertFalse(svc.isSpam(PLAYER, "看看 http://example.com"));
    }

    @Test
    void plainMessage_allowed() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertFalse(svc.isSpam(PLAYER, "今天天气真好"));
        assertFalse(svc.isSpam(PLAYER, "哪里有tnt"));
    }

    // ---- 重复检测 ----

    @Test
    void identicalMessage_blocked() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
        assertTrue(svc.isSpam(PLAYER, "收钻石"));
    }

    @Test
    void distinctMessages_allowed() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
        assertFalse(svc.isSpam(PLAYER, "出铁锭"));
    }

    @Test
    void repeatDetectionDisabled_allowsSameMessage() {
        ChatSpamFilterService svc = service(new ChatConfig(true, 6, true, false, "请勿刷屏或发送广告"));
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
    }

    // ---- 限流 ----

    @Test
    void rateLimited_afterMaxMessagesPerMinute() {
        ChatSpamFilterService svc = service(defaultConfig());
        // 上限 6：前 6 条放行，第 7 条被限流
        for (int i = 0; i < 6; i++) {
            assertFalse(svc.isSpam(PLAYER, "消息" + i), "第 " + (i + 1) + " 条应放行");
        }
        assertTrue(svc.isSpam(PLAYER, "第7条"));
    }

    @Test
    void windowSlides_afterOneMinute() {
        AtomicLong now = new AtomicLong(0L);
        ChatSpamFilterService svc = service(new ChatConfig(true, 2, false, false, "请勿刷屏或发送广告"), now::get);
        assertFalse(svc.isSpam(PLAYER, "a"));
        assertFalse(svc.isSpam(PLAYER, "b"));
        assertTrue(svc.isSpam(PLAYER, "c"));
        // 60s 后窗口滑动，恢复可用额度
        now.set(60_000L);
        assertFalse(svc.isSpam(PLAYER, "d"));
    }

    // ---- 状态清理 ----

    @Test
    void clear_resetsPlayerState() {
        ChatSpamFilterService svc = service(defaultConfig());
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
        assertTrue(svc.isSpam(PLAYER, "收钻石")); // 重复
        svc.clear(PLAYER);
        assertFalse(svc.isSpam(PLAYER, "收钻石"));
    }
}
