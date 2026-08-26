package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LoginAccessControlServiceTest extends ServiceTestBase {

    @Mock
    private WorldMaintenanceService maintenanceService;

    @Mock
    private AccessRuleService accessRuleService;

    @Mock
    private GeoIpAccessService geoIpAccessService;

    @Mock
    private PlayerEventService playerEventService;

    @Mock
    private Notifier notifier;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private ServerFacade server;

    @Mock
    private Logger logger;

    @Mock
    private ThrottledNotifier blockNotifier;

    @Mock
    private AsyncPlayerPreLoginEvent event;

    @Mock
    private PlayerProfile profile;

    private LoginAccessControlService service;

    @BeforeEach
    void setUp() throws Exception {
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(event.getAddress()).thenReturn(InetAddress.getByName("1.2.3.4"));
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("player1");
        when(maintenanceService.isRunning()).thenReturn(false);
        when(accessRuleService.matchedIpPattern(anyString())).thenReturn(null);
        when(accessRuleService.matchedPlayerNameRule(anyString())).thenReturn(null);
        when(configs.renderTemplate(anyString(), anyMap(), anyString()))
                .thenReturn(MessageEnvelope.publicMessage("ip_blacklist_block"));
        when(server.logger()).thenReturn(logger);
        when(styles.warn(anyString())).thenReturn(Component.text("warn"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));
        when(blockNotifier.shouldRun(anyString(), anyLong())).thenReturn(true);

        service = new LoginAccessControlService(
                maintenanceService,
                accessRuleService,
                geoIpAccessService,
                playerEventService,
                notifier,
                configs,
                styles,
                server,
                blockNotifier);
    }

    @Test
    void handlePreLogin_maintenance_disallowsAndSkipsChecks() {
        when(maintenanceService.isRunning()).thenReturn(true);

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verifyNoInteractions(accessRuleService, geoIpAccessService, playerEventService);
    }

    @Test
    void handlePreLogin_alreadyDisallowed_returnsEarly() {
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);

        service.handlePreLogin(event);

        verifyNoInteractions(accessRuleService, geoIpAccessService, playerEventService);
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handlePreLogin_blacklisted_disallowsNotifiesAndLogs() {
        when(accessRuleService.matchedIpPattern("1.2.3.4")).thenReturn("1.2.3.4");

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("ip_blacklist_block"), any(MessageEnvelope.class));
        verify(configs).renderTemplate(eq("ip_blacklist_block"), anyMap(), anyString());
        verify(logger).warning(anyString());
        verifyNoInteractions(geoIpAccessService, playerEventService);
    }

    @Test
    void handlePreLogin_blacklistedV6_notifiesWithPattern() throws Exception {
        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        when(event.getAddress()).thenReturn(v6);
        when(accessRuleService.matchedIpPattern(anyString())).thenReturn("2001:db8::/32");

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("ip_blacklist_block"), any(MessageEnvelope.class));
        verify(configs).renderTemplate(eq("ip_blacklist_block"), anyMap(), anyString());
    }

    @Test
    void handlePreLogin_playerNameBlocked_disallowsNotifiesAndSkipsGeoIp() {
        when(accessRuleService.matchedPlayerNameRule("player1"))
                .thenReturn(PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "bot_"));

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("player_name_block"), any(MessageEnvelope.class));
        verify(configs).renderTemplate(eq("player_name_block"), anyMap(), anyString());
        verify(logger).warning(anyString());
        verifyNoInteractions(geoIpAccessService, playerEventService);
    }

    @Test
    void handlePreLogin_blacklistNotificationThrottled_skipsDmButLogs() {
        // 限频器抑制 → 私信跳过，但控制台日志每次保留（防重连刷屏打爆 QQ 频控，日志不失明）
        when(accessRuleService.matchedIpPattern("1.2.3.4")).thenReturn("1.2.3.4");
        when(blockNotifier.shouldRun("ip_blacklist_block", 5000)).thenReturn(false);

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier, never()).event(eq("ip_blacklist_block"), any(MessageEnvelope.class));
        verify(logger).warning(anyString());
    }

    @Test
    void handlePreLogin_playerNameBlockThrottled_skipsDmButLogs() {
        when(accessRuleService.matchedPlayerNameRule("player1"))
                .thenReturn(PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "bot_"));
        when(blockNotifier.shouldRun("player_name_block", 5000)).thenReturn(false);

        service.handlePreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier, never()).event(eq("player_name_block"), any(MessageEnvelope.class));
        verify(logger).warning(anyString());
    }

    @Test
    void handlePreLogin_nullPlayerName_usesFallbackInNotification() {
        when(profile.getName()).thenReturn(null);
        when(accessRuleService.matchedIpPattern("1.2.3.4")).thenReturn("1.2.3.4");

        service.handlePreLogin(event);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderTemplate(eq("ip_blacklist_block"), captor.capture(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals("未知玩家", captor.getValue().get("player"));
    }

    @Test
    void handlePreLogin_nullPlayerName_skipsNameRuleMatching() throws Exception {
        // P2-2：名称未上报时跳过玩家名规则匹配——即使存在过宽规则（本应命中任意名）也不误封
        when(profile.getName()).thenReturn(null);
        when(accessRuleService.matchedPlayerNameRule(anyString()))
                .thenReturn(PlayerNameRule.of(PlayerNameRule.MatchType.CONTAINS, "a"));
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}")));

        service.handlePreLogin(event);

        verify(accessRuleService, never()).matchedPlayerNameRule(anyString());
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        // 名称规则跳过后仍正常走 GeoIP（展示名用占位）
        verify(playerEventService)
                .handleGeoIpPreLogin(
                        eq(event), eq("未知玩家"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }

    @Test
    void handlePreLogin_emptyPlayerName_skipsNameRuleMatching() throws Exception {
        // P3-4：profile 上报空串（离线模式变体）同样跳过名称规则匹配，与 null 等价
        when(profile.getName()).thenReturn("");
        when(accessRuleService.matchedPlayerNameRule(anyString()))
                .thenReturn(PlayerNameRule.of(PlayerNameRule.MatchType.CONTAINS, "a"));
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}")));

        service.handlePreLogin(event);

        verify(accessRuleService, never()).matchedPlayerNameRule(anyString());
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(playerEventService)
                .handleGeoIpPreLogin(
                        eq(event), eq("未知玩家"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }

    @Test
    void handlePreLogin_emptyIp_skipsGeoIp() {
        InetAddress emptyAddr = mock(InetAddress.class);
        when(emptyAddr.getHostAddress()).thenReturn("");
        when(event.getAddress()).thenReturn(emptyAddr);

        service.handlePreLogin(event);

        verify(accessRuleService).matchedIpPattern("");
        verifyNoInteractions(geoIpAccessService, playerEventService);
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handlePreLogin_nullAddress_skipsGeoIpButNameRuleStillRuns() {
        when(event.getAddress()).thenReturn(null);
        when(accessRuleService.matchedPlayerNameRule("player1"))
                .thenReturn(PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "bot_"));

        service.handlePreLogin(event);

        verify(accessRuleService).matchedIpPattern("");
        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("player_name_block"), any(MessageEnvelope.class));
        verifyNoInteractions(geoIpAccessService, playerEventService);
    }

    @Test
    void handlePreLogin_geoIpBlocked_forwardsDecision() {
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}")));

        service.handlePreLogin(event);

        verify(playerEventService)
                .handleGeoIpPreLogin(
                        eq(event), eq("player1"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }

    @Test
    void handlePreLogin_geoIpAllowed_forwardsDecision() {
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}")));

        service.handlePreLogin(event);

        verify(playerEventService)
                .handleGeoIpPreLogin(
                        eq(event), eq("player1"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }
}
