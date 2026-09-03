package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
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
    private PlayerEventAggregator aggregator;

    @Mock
    private Logger logger;

    private PlayerEventService service;

    @BeforeEach
    void setUp() {
        // 告警限频默认放行，聚焦验证告警本身；限频行为由 ThrottledNotifier 单测覆盖
        when(throttledNotifier.shouldRun(anyString(), anyLong())).thenReturn(true);
        service = new PlayerEventService(server, configs, styles, notifier, throttledNotifier, aggregator);
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
    void handleGeoIpDecision_blocked_throttledSuppressesGroupMsg_keepsDisallow() {
        // geoip_block 群消息限频：GeoIP 故障期 fail-close 拒绝随客户端自动重连高频触发，
        // 不限频会重复打爆玩家群（对照 whitelist_block 曾 48 次打爆 QQ 频控 40034100）。
        // 限频只抑制群消息；event.disallow 不受限频，拦截始终执行。
        when(throttledNotifier.shouldRun(eq("geoip_block_group_notify"), anyLong()))
                .thenReturn(false);
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}"));

        verify(notifier, never()).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handleGeoIpDecision_allowedButLookupFailed_sendsPrivateAlert() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(true, "", List.of("CN"), "", true));

        verify(logger).warning(contains("已放行（fail-open）"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpDecision_deniedButLookupFailed_failClose_blocksAndAlerts() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));
        when(configs.renderEvent(eq("geoip_unverifiable"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("retry"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "", List.of("CN"), "", true));

        verify(logger).warning(contains("已拒绝（fail-close）"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verify(notifier).event(eq("geoip_unverifiable"), any(MessageEnvelope.class));
        verify(notifier, never()).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
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

        service.handleGeoIpLookupFailure("player1", "1.2.3.4", "已放行（fail-open）");

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
    void handleGeoIpPreLogin_timeout_defaultFailClose_deniesAndAlerts() {
        when(server.logger()).thenReturn(logger);
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));
        when(configs.renderEvent(eq("geoip_unverifiable"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("retry"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));
        CompletableFuture<GeoIpAccessService.Decision> never = new CompletableFuture<>();

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", never, 50);

        verify(logger).warning(contains("超时"));
        verify(logger).warning(contains("已拒绝"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verify(notifier).event(eq("geoip_unverifiable"), any(MessageEnvelope.class));
        verify(notifier, never()).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void denyGeoIpUnverifiable_rendersRetryHintTemplate_withNameAndIpOnly() {
        when(server.logger()).thenReturn(logger);
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));
        when(configs.renderEvent(eq("geoip_unverifiable"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("retry"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));
        CompletableFuture<GeoIpAccessService.Decision> never = new CompletableFuture<>();

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", never, 50);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderEvent(eq("geoip_unverifiable"), captor.capture());
        assertEquals(java.util.Set.of("name", "ip"), captor.getValue().keySet());
        assertEquals("player1", captor.getValue().get("name"));
        assertEquals("1.2.3.4", captor.getValue().get("ip"));
        verify(notifier).event(eq("geoip_unverifiable"), any(MessageEnvelope.class));
        verify(notifier, never()).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handleGeoIpPreLogin_timeout_failOpen_allowsAndAlerts() {
        when(server.logger()).thenReturn(logger);
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));
        CompletableFuture<GeoIpAccessService.Decision> never = new CompletableFuture<>();

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", never, 50);

        verify(logger).warning(contains("超时"));
        verify(logger).warning(contains("已放行"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpPreLogin_lookupError_defaultFailClose_deniesAndAlerts() {
        when(server.logger()).thenReturn(logger);
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));
        when(configs.renderEvent(eq("geoip_unverifiable"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("retry"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));
        CompletableFuture<GeoIpAccessService.Decision> failed =
                CompletableFuture.failedFuture(new RuntimeException("boom"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", failed, 1000);

        verify(logger).warning(contains("boom"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verify(notifier).event(eq("geoip_unverifiable"), any(MessageEnvelope.class));
        verify(notifier, never()).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handleGeoIpPreLogin_lookupError_failOpen_allowsAndAlerts() {
        when(server.logger()).thenReturn(logger);
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN"), true));
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
        when(configs.ipWhitelist()).thenReturn(new IpWhitelist(List.of("CN")));
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));

        service.handleGeoIpTimeout("player1", "1.2.3.4", 3000);

        verify(logger).warning(contains("超时"));
        verify(logger).warning(contains("已拒绝"));
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

    // ---- 上下线广播：委托聚合器（渲染与聚合逻辑由 PlayerEventAggregatorTest 覆盖）----

    @Test
    void notifyPlayerState_delegatesToAggregator() {
        Player player = mock(Player.class);

        service.notifyPlayerState(player, PlayerEventService.PlayerState.JOIN);
        service.notifyPlayerState(player, PlayerEventService.PlayerState.QUIT);
        service.notifyPlayerState(player, PlayerEventService.PlayerState.KICK);

        verify(aggregator).enqueue(player, PlayerEventService.PlayerState.JOIN);
        verify(aggregator).enqueue(player, PlayerEventService.PlayerState.QUIT);
        verify(aggregator).enqueue(player, PlayerEventService.PlayerState.KICK);
    }

    @Test
    void flushPending_delegatesToAggregator() {
        service.flushPending();

        verify(aggregator).flushPending();
    }
}
