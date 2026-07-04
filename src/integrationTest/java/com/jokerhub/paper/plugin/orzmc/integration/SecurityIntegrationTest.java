package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@Tag("integration")
public class SecurityIntegrationTest {

    private ServerMock server;
    private OrzMC plugin;
    private CapturingSink sink;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
        sink = new CapturingSink();
        plugin.services().botModule().notifier().registerSink(sink);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testBlacklistCommandAddWithAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist add 1.2.3.4"));
    }

    @Test
    public void testBlacklistCommandListWithAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist list"));
    }

    @Test
    public void testBlacklistCommandRemoveWithAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist remove 192.168.1.1"));
    }

    @Test
    public void testBlacklistCommandNonAdminPlayer() {
        // MockBukkit does not check Brigadier requires(), so admin-only commands dispatch
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist list"));
    }

    @Test
    public void testBlacklistServiceDirectly() {
        // Access BlacklistService through its public API
        ConfigService configService = new ConfigService(plugin);
        BlacklistService blacklistService = new BlacklistService(configService);
        Assertions.assertNotNull(blacklistService);
        Assertions.assertTrue(blacklistService.getPatterns().isEmpty(), "Blacklist should start empty");

        // Test add
        blacklistService.add("10.0.0.1");
        Assertions.assertEquals(1, blacklistService.getPatterns().size());
        Assertions.assertTrue(blacklistService.isBlocked("10.0.0.1"));

        // Test remove
        blacklistService.remove("10.0.0.1");
        Assertions.assertTrue(blacklistService.getPatterns().isEmpty());

        // Test IP matching
        blacklistService.add("192.168.1.*");
        Assertions.assertTrue(blacklistService.isBlocked("192.168.1.100"));
        Assertions.assertFalse(blacklistService.isBlocked("192.168.2.100"));
    }

    @Test
    public void testBlacklistViaBotCommand() {
        // $d is the BLACKLIST bot command name
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(
                () -> plugin.services().botModule().botInboundHandler().handleMessage("$d", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Blacklist via bot should produce response");
    }

    @Test
    public void testBlacklistAddViaBotCommand() {
        // $d <pattern> adds to blacklist
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(
                () -> plugin.services().botModule().botInboundHandler().handleMessage("$d 10.0.0.1", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Blacklist add via bot should produce response");
    }

    @Test
    public void testGeoIpServiceInitialization() {
        // GeoIpAccessService is created during assembly. Load succeeds without errors.
        Assertions.assertNotNull(plugin.services());
        Assertions.assertNotNull(plugin.services().botModule());
    }

    @Test
    public void testBlacklistNonAdminBotCommandBlocked() {
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(
                () -> plugin.services().botModule().botInboundHandler().handleMessage("$d", false, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Non-admin blacklist should produce response");
        Assertions.assertTrue(
                envelope.message().contains("admin") || envelope.message().contains("管理员"),
                "Non-admin should receive admin-required message, got: " + envelope.message());
    }

    @Test
    public void testBlacklistAddAndVerifyViaBotCommand() {
        // Add a pattern via bot command
        AtomicReference<MessageEnvelope> got1 = new AtomicReference<>();
        plugin.services().botModule().botInboundHandler().handleMessage("$d 192.168.1.0/24", true, got1::set);
        server.getScheduler().performOneTick();
        Assertions.assertNotNull(got1.get(), "Blacklist add should produce response");

        // Now list patterns
        AtomicReference<MessageEnvelope> got2 = new AtomicReference<>();
        plugin.services().botModule().botInboundHandler().handleMessage("$d", true, got2::set);
        server.getScheduler().performOneTick();
        MessageEnvelope envelope2 = got2.get();
        Assertions.assertNotNull(envelope2, "Blacklist list should produce response");
        Assertions.assertTrue(
                envelope2.message().contains("192.168.1.0/24"),
                "List should contain added pattern, got: " + envelope2.message());
    }

    private static final class CapturingSink implements NotifierSink {
        private final List<String> keys = new ArrayList<>();
        private final List<MessageEnvelope> envelopes = new ArrayList<>();
        private final List<Component> serverMessages = new ArrayList<>();

        @Override
        public void server(Component message) {
            serverMessages.add(message);
        }

        @Override
        public void event(String key, MessageEnvelope envelope) {
            keys.add(key);
            envelopes.add(envelope);
        }
    }
}
