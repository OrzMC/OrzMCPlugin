package com.jokerhub.paper.plugin.orzmc.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.rank.PlayerRankDisplayService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** OrzRankDisplayEvent 测试：聊天渲染器保留 displayName 昵称并强制 rank 色；取消/禁用时跳过。 */
class OrzRankDisplayEventTest {

    private OrzMC plugin;
    private ServerFacade serverFacade;
    private RankService rankService;
    private PlayerRankDisplayService service;

    @BeforeEach
    void setUp() {
        plugin = mock(OrzMC.class);
        serverFacade = mock(ServerFacade.class);
        rankService = mock(RankService.class);
        service = new PlayerRankDisplayService(serverFacade, rankService, () -> RankColorsConfig.from(null));
    }

    private Player mockPlayer(String name, boolean op) {
        Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOp()).thenReturn(op);
        return p;
    }

    @Test
    void onAsyncChat_preservesDisplayNameNickAndColors() {
        Player sender = mockPlayer("Steve", false);
        when(rankService.currentGroup(sender.getUniqueId())).thenReturn("member");
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getPlayer()).thenReturn(sender);

        new OrzRankDisplayEvent(plugin, service).onAsyncChat(event);

        ArgumentCaptor<ChatRenderer> cap = ArgumentCaptor.forClass(ChatRenderer.class);
        verify(event).renderer(cap.capture());
        Component rendered = cap.getValue()
                .render(mock(Player.class), Component.text("CoolGuy"), Component.text("hi"), mock(Audience.class));

        // displayName 昵称保留 + rank 色强制 + 消息透传（整体组件相等断言，避免触碰已废弃 args()）
        assertEquals(
                Component.translatable(
                        "chat.type.text", Component.text("CoolGuy").color(NamedTextColor.AQUA), Component.text("hi")),
                rendered);
    }

    @Test
    void onAsyncChat_cancelled_skipsRenderer() {
        Player sender = mockPlayer("Steve", false);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.isCancelled()).thenReturn(true);

        new OrzRankDisplayEvent(plugin, service).onAsyncChat(event);

        verify(event, never()).renderer(any());
    }

    @Test
    void onAsyncChat_featureDisabled_skipsRenderer() {
        Player sender = mockPlayer("Steve", false);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getPlayer()).thenReturn(sender);
        PlayerRankDisplayService disabled = new PlayerRankDisplayService(
                serverFacade,
                rankService,
                () -> new RankColorsConfig(false, true, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS));

        new OrzRankDisplayEvent(plugin, disabled).onAsyncChat(event);

        verify(event, never()).renderer(any());
    }
}
