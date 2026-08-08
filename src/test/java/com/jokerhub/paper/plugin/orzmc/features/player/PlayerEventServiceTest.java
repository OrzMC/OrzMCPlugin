package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PlayerEventServiceTest extends ServiceTestBase {

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private Notifier notifier;

    @Mock
    private ThrottledNotifier throttledNotifier;

    @Mock
    private AsyncPlayerPreLoginEvent loginEvent;

    @Mock
    private Logger logger;

    private PlayerEventService service;

    @BeforeEach
    void setUp() {
        // 告警限频默认放行，聚焦验证告警本身；限频行为由 ThrottledNotifier 单测覆盖
        when(throttledNotifier.shouldRun(anyString(), anyLong())).thenReturn(true);
        service =
                new PlayerEventService(server, configs, styles, notifier, throttledNotifier, new OnlineListFormatter());
    }

    @Test
    void handleGeoIpDecision_allowed_doesNothing() {
        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}"));

        verifyNoInteractions(notifier, configs, styles);
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpDecision_blocked_sendsNotification() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}"));

        verify(notifier).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handleGeoIpDecision_allowedButLookupFailed_sendsPrivateAlert() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(true, "", List.of("CN"), "", true));

        verify(logger).warning(contains("已放行"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpException_logsWarning() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));

        service.handleGeoIpException(new RuntimeException("lookup failed"));

        verify(logger).warning(contains("lookup failed"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }

    @Test
    void handleGeoIpLookupFailure_throttled_suppressesDmButKeepsLog() {
        when(server.logger()).thenReturn(logger);
        when(throttledNotifier.shouldRun(anyString(), anyLong())).thenReturn(false);

        service.handleGeoIpLookupFailure("player1", "1.2.3.4");

        verify(logger).warning(contains("已放行"));
        verifyNoInteractions(notifier, configs, styles);
    }

    @Test
    void handleGeoIpPreLogin_blocked_disallows() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));
        CompletableFuture<GeoIpAccessService.Decision> future =
                CompletableFuture.completedFuture(new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", future, 1000);

        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("geoip_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleGeoIpPreLogin_allowed_doesNothing() {
        CompletableFuture<GeoIpAccessService.Decision> future =
                CompletableFuture.completedFuture(new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", future, 1000);

        verifyNoInteractions(loginEvent, notifier, configs, styles);
    }

    @Test
    void handleGeoIpPreLogin_timeout_allowsAndWarns() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));
        CompletableFuture<GeoIpAccessService.Decision> never = new CompletableFuture<>();

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", never, 50);

        verify(logger).warning(contains("超时"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpPreLogin_lookupError_allowsAndWarns() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));
        CompletableFuture<GeoIpAccessService.Decision> failed =
                CompletableFuture.failedFuture(new RuntimeException("boom"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", failed, 1000);

        verify(logger).warning(contains("boom"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpTimeout_logsWarningAndNotifies() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));

        service.handleGeoIpTimeout("player1", "1.2.3.4", 3000);

        verify(logger).warning(contains("超时"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }

    @Test
    void handleGeoIpDecision_blocked_prettyPrintsAddressInfo() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));
        String compact = "{\"ip\":\"1.2.3.4\",\"country_code\":\"US\"}";

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "US", List.of("CN"), compact));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderEvent(eq("geoip_block"), captor.capture());
        String addressInfo = captor.getValue().get("address_info");
        assertNotNull(addressInfo, "address_info should be present");
        assertTrue(addressInfo.contains("\n  \"ip\""), "JSON should be pretty-printed, got: " + addressInfo);
    }

    // ---- 上下线广播：在线列表含权限组（2026-08-07 修复：缺组名）----

    @Test
    void notifyPlayerState_join_withRankService_includesGroupInList() {
        com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService =
                mock(com.jokerhub.paper.plugin.orzmc.features.rank.RankService.class);
        com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter formatter =
                new com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter();
        formatter.setRankService(rankService);
        service = new PlayerEventService(server, configs, styles, notifier, throttledNotifier, formatter);

        org.bukkit.entity.Player p1 = mock(org.bukkit.entity.Player.class);
        org.bukkit.entity.Player p2 = mock(org.bukkit.entity.Player.class);
        com.destroystokyo.paper.profile.PlayerProfile profile1 =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        com.destroystokyo.paper.profile.PlayerProfile profile2 =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile1.getName()).thenReturn("Alice");
        when(profile2.getName()).thenReturn("Bob");
        when(p1.getPlayerProfile()).thenReturn(profile1);
        when(p2.getPlayerProfile()).thenReturn(profile2);
        when(p1.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(org.bukkit.GameMode.CREATIVE);

        org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
        when(server.server()).thenReturn(bukkitServer);
        java.util.Collection<? extends org.bukkit.entity.Player> online = java.util.List.of(p1, p2);
        doReturn(online).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(20);

        // p1 是 admin 组、p2 是 builder 组（LP 真实组 → 显示名）
        java.util.UUID id1 = java.util.UUID.randomUUID();
        java.util.UUID id2 = java.util.UUID.randomUUID();
        when(p1.getUniqueId()).thenReturn(id1);
        when(p2.getUniqueId()).thenReturn(id2);
        when(rankService.currentGroup(id1)).thenReturn("admin");
        when(rankService.currentGroup(id2)).thenReturn("builder");

        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        when(p1.getLocation()).thenReturn(loc);
        when(loc.getWorld()).thenReturn(null);
        when(server.logger()).thenReturn(logger);
        when(configs.templateOptions())
                .thenReturn(mock(com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions.class));
        when(configs.renderEvent(eq("player_join"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));
        when(throttledNotifier.shouldRunDefault(anyString())).thenReturn(true);

        service.notifyPlayerState(p1, PlayerEventService.PlayerState.JOIN);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderEvent(eq("player_join"), captor.capture());
        String onlineList = captor.getValue().get("online_list");
        assertNotNull(onlineList, "online_list should be present");
        assertTrue(onlineList.contains("Alice"), "list should contain Alice, got: " + onlineList);
        assertTrue(onlineList.contains("管理员"), "Alice 应显示权限组 管理员, got: " + onlineList);
        assertTrue(onlineList.contains("Bob"), "list should contain Bob, got: " + onlineList);
        assertTrue(onlineList.contains("建造者"), "Bob 应显示权限组 建造者, got: " + onlineList);
    }

    @Test
    void notifyPlayerState_join_withoutRankService_omitsGroup() {
        org.bukkit.entity.Player p1 = mock(org.bukkit.entity.Player.class);
        com.destroystokyo.paper.profile.PlayerProfile profile1 =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile1.getName()).thenReturn("Alice");
        when(p1.getPlayerProfile()).thenReturn(profile1);
        when(p1.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);

        org.bukkit.Server bukkitServer = mock(org.bukkit.Server.class);
        when(server.server()).thenReturn(bukkitServer);
        java.util.Collection<? extends org.bukkit.entity.Player> online = java.util.List.of(p1);
        doReturn(online).when(bukkitServer).getOnlinePlayers();
        when(bukkitServer.getMaxPlayers()).thenReturn(20);

        org.bukkit.Location loc = mock(org.bukkit.Location.class);
        when(p1.getLocation()).thenReturn(loc);
        when(loc.getWorld()).thenReturn(null);
        when(server.logger()).thenReturn(logger);
        when(configs.templateOptions())
                .thenReturn(mock(com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions.class));
        when(configs.renderEvent(eq("player_join"), anyMap())).thenReturn(MessageEnvelope.publicMessage("ok"));
        when(throttledNotifier.shouldRunDefault(anyString())).thenReturn(true);

        service.notifyPlayerState(p1, PlayerEventService.PlayerState.JOIN);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderEvent(eq("player_join"), captor.capture());
        String onlineList = captor.getValue().get("online_list");
        assertNotNull(onlineList);
        // 无 rankService：只显示 玩家名+游戏模式，不含任何权限组词
        assertTrue(onlineList.contains("Alice 生存模式"), "got: " + onlineList);
        assertFalse(onlineList.contains("管理员"), "不应含权限组, got: " + onlineList);
    }
}
