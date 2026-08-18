package com.jokerhub.paper.plugin.orzmc.features.chat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatSpamFilterEventServiceTest {

    private ChatSpamFilterService filter;
    private TypedConfigProvider configs;
    private OrzTextStyles styles;
    private ChatSpamFilterEventService service;

    @BeforeEach
    void setUp() {
        ChatConfig config = new ChatConfig(true, 6, true, true, "请勿刷屏或发送广告");
        filter = new ChatSpamFilterService(() -> config);
        configs = mock(TypedConfigProvider.class);
        when(configs.chat()).thenReturn(config);
        styles = mock(OrzTextStyles.class);
        when(styles.warn(anyString())).thenReturn(Component.text("请勿刷屏或发送广告"));
        service = new ChatSpamFilterEventService(filter, configs, styles);
    }

    @Test
    void spamMessage_cancelledAndWarned() {
        Player player = player();
        AsyncChatEvent event = asyncChatEvent(player, "http://example.com");
        service.onAsyncChat(event);
        verify(event).setCancelled(true);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void normalMessage_notCancelled() {
        Player player = player();
        AsyncChatEvent event = asyncChatEvent(player, "今天天气真好");
        service.onAsyncChat(event);
        verify(event, never()).setCancelled(true);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void playerQuit_clearsState() {
        Player player = player();
        // 触发一次重复命中，确保状态存在
        service.onAsyncChat(asyncChatEvent(player, "收钻石"));
        AsyncChatEvent second = asyncChatEvent(player, "收钻石");
        service.onAsyncChat(second);
        verify(second).setCancelled(true);

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        service.onPlayerQuit(quit);

        // 退出后状态被清空，同样的消息不再判定为重复
        AsyncChatEvent third = asyncChatEvent(player, "收钻石");
        service.onAsyncChat(third);
        verify(third, never()).setCancelled(true);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static AsyncChatEvent asyncChatEvent(Player player, String text) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(text));
        return event;
    }
}
