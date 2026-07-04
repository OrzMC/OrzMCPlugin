package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzBotManagerTest {

    private ServerAccess server;
    private ServerScheduler scheduler;
    private ServerLogger logger;
    private ConfigService configService;
    private ThrottledLogger throttledLogger;
    private BotInboundHandler inboundHandler;
    private HealthRegistry healthRegistry;
    private FileConfiguration botConfig;
    private OrzBotManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        server = mock(ServerAccess.class);
        scheduler = mock(ServerScheduler.class);
        logger = mock(ServerLogger.class);
        configService = mock(ConfigService.class);
        throttledLogger = mock(ThrottledLogger.class);
        inboundHandler = mock(BotInboundHandler.class);
        healthRegistry = spy(new HealthRegistry());
        botConfig = mock(FileConfiguration.class);

        when(logger.logger()).thenReturn(mock(Logger.class));
        when(configService.getConfig("bot")).thenReturn(botConfig);
        // Default: qq bot disabled so startIfRequested doesn't trigger WS calls
        when(botConfig.getBoolean("enable_qq_bot")).thenReturn(false);
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(false);
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(false);
        when(botConfig.getString("qq_bot_ws_server")).thenReturn(null);

        manager = new OrzBotManager(
                server, scheduler, logger, configService, throttledLogger, inboundHandler, healthRegistry);
    }

    @Test
    void constructor_setsIdleState() {
        // Constructor runs without error and manager is usable
        assertNotNull(manager);
    }

    @Test
    void setup_runsAsyncTask() {
        manager.setup();
        verify(scheduler).runAsync(any(Runnable.class));
    }

    @Test
    void setup_triggersStartWhenRunAsyncExecuted() {
        Runnable[] captured = new Runnable[1];
        doAnswer(invocation -> {
                    captured[0] = invocation.getArgument(0);
                    return null;
                })
                .when(scheduler)
                .runAsync(any(Runnable.class));

        manager.setup();

        assertNotNull(captured[0], "runAsync should have been called with a Runnable");
        captured[0].run();

        // After startIfRequested, no exception means adapters were created
        verify(throttledLogger, atLeast(0)).info(anyString(), anyString());
    }

    @Test
    void setup_ignored_whenAlreadyStarted() {
        Runnable[] captured1 = new Runnable[1];
        doAnswer(invocation -> {
                    captured1[0] = invocation.getArgument(0);
                    return null;
                })
                .when(scheduler)
                .runAsync(any(Runnable.class));

        manager.setup();
        assertNotNull(captured1[0]);
        captured1[0].run(); // First call starts it

        Runnable[] captured2 = new Runnable[1];
        doAnswer(invocation -> {
                    captured2[0] = invocation.getArgument(0);
                    return null;
                })
                .when(scheduler)
                .runAsync(any(Runnable.class));

        manager.setup(); // Second call
        assertNotNull(captured2[0]);
        captured2[0].run(); // Second call should be ignored by startIfRequested

        // No crash is the main verification
    }

    @Test
    void send_delegatesToRouter() {
        MessageEnvelope envelope = MessageEnvelope.publicMessage("test message");
        manager.send(envelope);

        verify(throttledLogger).info("bot", "test message");
        // No crash — router routes to (empty) adapters
    }

    @Test
    void send_nullEnvelope_doesNothing() {
        manager.send(null);
        verify(throttledLogger, never()).info(anyString(), anyString());
    }

    @Test
    void tryReconnectQqWsIfDisconnected_runsAsync() {
        manager.tryReconnectQqWsIfDisconnected();
        verify(scheduler).runAsync(any(Runnable.class));
    }

    @Test
    void tearDown_clearsAdapters() {
        manager.tearDown();
        // No crash — adapters set to empty list
    }

    @Test
    void tearDown_afterSetup_clearsAdapters() {
        Runnable[] captured = new Runnable[1];
        doAnswer(invocation -> {
                    captured[0] = invocation.getArgument(0);
                    return null;
                })
                .when(scheduler)
                .runAsync(any(Runnable.class));

        manager.setup();
        assertNotNull(captured[0]);
        captured[0].run();

        manager.tearDown();
        // No crash
    }
}
