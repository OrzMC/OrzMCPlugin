package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BotCommandListFeedbackServiceTest {

    private ServerFacade serverFacade;
    private TypedConfigProvider configs;
    private Server server;
    private BotCommandListFeedbackService service;

    private MockedStatic<PlayerDisplayNames> displayNamesMock;
    private MockedStatic<TemplateRenderer> templateMock;

    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        server = mock(Server.class);
        configs = mock(TypedConfigProvider.class);
        when(serverFacade.server()).thenReturn(server);

        displayNamesMock = mockStatic(PlayerDisplayNames.class);
        templateMock = mockStatic(TemplateRenderer.class);

        service = new BotCommandListFeedbackService(serverFacade, configs);
    }

    @AfterEach
    void tearDown() {
        displayNamesMock.close();
        templateMock.close();
    }

    @Test
    void onlineVars_returnsCorrectMapping() {
        var online = new BotCommandListFeedbackService.OnlineList("Alice\nBob", "fallback", "header", "2", "20");
        Map<String, String> vars = service.onlineVars(online);

        assertEquals("2", vars.get("online_count"));
        assertEquals("20", vars.get("max_count"));
        assertEquals("Alice\nBob", vars.get("online_list"));
    }

    @Test
    void buildOnlineList_withPlayers_formatsNames() {
        Player alice = mock(Player.class);
        Player bob = mock(Player.class);
        ArrayList<Player> players = new ArrayList<>();
        players.add(alice);
        players.add(bob);

        displayNamesMock.when(() -> PlayerDisplayNames.format(alice)).thenReturn("§aAlice");
        displayNamesMock.when(() -> PlayerDisplayNames.format(bob)).thenReturn("§bBob");
        when(configs.resolveTemplate(eq("command_players"), anyString())).thenAnswer(i -> i.getArgument(1));
        templateMock.when(() -> TemplateRenderer.render(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        BotCommandListFeedbackService.OnlineList result = service.buildOnlineList(players, 20);

        assertEquals("§aAlice\n§bBob", result.list());
        assertTrue(result.fallback().contains("§aAlice"));
        assertTrue(result.fallback().contains("2/20"));
        assertEquals("2", result.onlineCount());
        assertEquals("20", result.maxCount());
    }

    @Test
    void buildOnlineList_emptyPlayers_returnsEmptyList() {
        ArrayList<Player> players = new ArrayList<>();
        when(configs.resolveTemplate(eq("command_players"), anyString())).thenAnswer(i -> i.getArgument(1));
        templateMock.when(() -> TemplateRenderer.render(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        BotCommandListFeedbackService.OnlineList result = service.buildOnlineList(players, 10);

        assertEquals("", result.list());
        assertTrue(result.fallback().contains("0/10"));
        assertEquals("0", result.onlineCount());
        assertEquals("10", result.maxCount());
    }

    @Test
    void buildWhitelistHeader_returnsHeaderWithCount() {
        when(configs.resolveTemplate(eq("command_whitelist_header"), anyString()))
                .thenAnswer(i -> i.getArgument(1));
        templateMock.when(() -> TemplateRenderer.render(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        BotCommandListFeedbackService.WhitelistHeader result = service.buildWhitelistHeader(42);

        assertTrue(result.header().contains("42"));
    }

    @Test
    void whitelistHeaderVars_containsCount() {
        Map<String, String> vars = service.whitelistHeaderVars(7);
        assertEquals("7", vars.get("count"));
    }

    @Test
    void buildCleanupNotice_includesRemovedNames() {
        Set<String> removed = Set.of("Alice", "Bob");
        when(configs.resolveTemplate(eq("command_whitelist_cleanup"), anyString()))
                .thenAnswer(i -> i.getArgument(1));
        templateMock.when(() -> TemplateRenderer.render(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        BotCommandListFeedbackService.CleanupNotice result = service.buildCleanupNotice(removed);

        assertTrue(result.fallback().contains("Alice"));
        assertTrue(result.fallback().contains("Bob"));
    }

    @Test
    void buildWhitelistPage_containsHeaderAndPage() {
        when(configs.resolveTemplate(eq("command_whitelist_page"), anyString())).thenAnswer(i -> i.getArgument(1));
        templateMock.when(() -> TemplateRenderer.render(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        BotCommandListFeedbackService.WhitelistPage result =
                service.buildWhitelistPage("白名单列表", 2, 3, "player1\nplayer2");

        assertTrue(result.fallback().contains("白名单列表"));
        assertTrue(result.fallback().contains("2/3"));
        assertTrue(result.fallback().contains("player1"));
    }

    @Test
    void currentOnlinePlayers_returnsPlayerList() {
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        HashSet<Player> onlinePlayers = new HashSet<>();
        onlinePlayers.add(p1);
        onlinePlayers.add(p2);
        doReturn(onlinePlayers).when(server).getOnlinePlayers();

        ArrayList<Player> result = service.currentOnlinePlayers();

        assertEquals(2, result.size());
    }
}
