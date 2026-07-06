package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotReconnectionManagerTest {

    private ConfigService configService;
    private FileConfiguration botConfig;
    private HealthRegistry healthRegistry;
    private BotReconnectionManager manager;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        botConfig = mock(FileConfiguration.class);
        healthRegistry = spy(new HealthRegistry());
        manager = new BotReconnectionManager(configService, healthRegistry);

        when(configService.getConfig("bot")).thenReturn(botConfig);
    }

    @Test
    void tryReconnect_qqDisabled_doesNothing() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(false);

        manager.tryReconnectIfDisconnected(List.of(), () -> {});

        // Method returns early at enable_qq_bot=false check, so getString is never called
        verify(botConfig, never()).getString("qq_bot_ws_server");
        // No reconnect logic should execute: no health changes
        assertFalse(healthRegistry.getRaw("qq").wsConnected);
    }

    @Test
    void tryReconnect_wsServerNull_doesNothing() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn(null);

        manager.tryReconnectIfDisconnected(List.of(), () -> {});

        verify(botConfig).getString("qq_bot_ws_server");
        // No reconnect logic should execute
    }

    @Test
    void tryReconnect_wsServerEmpty_doesNothing() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("");

        manager.tryReconnectIfDisconnected(List.of(), () -> {});

        // No reconnect logic
    }

    @Test
    void tryReconnect_alreadyConnected_doesNothing() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        // Mark as connected
        healthRegistry.setApiReady("qq", true);
        healthRegistry.setWsConnected("qq", true);
        assertTrue(healthRegistry.getRaw("qq").wsConnected);

        manager.tryReconnectIfDisconnected(List.of(), () -> {});

        // No reconnect in flight — wsConnected was true so it returned early
    }

    @Test
    void tryReconnect_concurrentCall_resetsFlagAfterCompletion() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        // Mark as disconnected
        healthRegistry.setWsConnected("qq", false);

        Runnable onReconnect = mock(Runnable.class);
        // First call runs fully (reconnectInFlight set, then finally reset)
        manager.tryReconnectIfDisconnected(List.of(), onReconnect);
        // Second call runs again because finally block reset the flag
        manager.tryReconnectIfDisconnected(List.of(), onReconnect);

        // Both calls proceed since they're synchronous (no concurrent CAS)
        verify(onReconnect, times(2)).run();
    }

    @Test
    void tryReconnect_callsOnReconnectAndQqSetup() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        healthRegistry.setWsConnected("qq", false);

        Runnable onReconnect = mock(Runnable.class);
        OrzQQBot qqBot = mock(OrzQQBot.class);
        List<BotAdapter> adapters = new ArrayList<>();
        adapters.add(qqBot);

        manager.tryReconnectIfDisconnected(adapters, onReconnect);

        verify(onReconnect).run();
        verify(qqBot).setupWebSocketClient();
    }

    @Test
    void tryReconnect_onReconnectReconnects_noQqSetup() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        // onReconnect will set wsConnected = true
        Runnable onReconnect = () -> healthRegistry.setWsConnected("qq", true);

        healthRegistry.setWsConnected("qq", false);

        OrzQQBot qqBot = mock(OrzQQBot.class);
        List<BotAdapter> adapters = new ArrayList<>();
        adapters.add(qqBot);

        manager.tryReconnectIfDisconnected(adapters, onReconnect);

        // Should NOT call setupWebSocketClient because onReconnect already reconnected
        verify(qqBot, never()).setupWebSocketClient();
    }

    @Test
    void tryReconnect_exception_caughtAndLogged() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        healthRegistry.setWsConnected("qq", false);

        Runnable failingOnReconnect = () -> {
            throw new RuntimeException("Reconnect failed");
        };

        manager.tryReconnectIfDisconnected(List.of(), failingOnReconnect);

        // Error should be recorded
        assertNotNull(healthRegistry.getRaw("qq").lastError);
    }

    @Test
    void tryReconnect_findsQqBotInAdapterList() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        healthRegistry.setWsConnected("qq", false);

        List<BotAdapter> adapters = new ArrayList<>();
        adapters.add(mock(BotAdapter.class)); // non-QQ adapter
        OrzQQBot qqBot = mock(OrzQQBot.class);
        adapters.add(qqBot);

        manager.tryReconnectIfDisconnected(adapters, () -> {});

        verify(qqBot).setupWebSocketClient();
    }

    @Test
    void tryReconnect_noQqBotInList_doesNothing() {
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(true);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn("ws://localhost:12345");

        healthRegistry.setWsConnected("qq", false);

        List<BotAdapter> adapters = new ArrayList<>();
        adapters.add(mock(BotAdapter.class)); // No OrzQQBot in list

        manager.tryReconnectIfDisconnected(adapters, () -> {});

        // Should not throw, just return without calling setup
        verify(healthRegistry, atLeastOnce()).getRaw("qq");
    }
}
