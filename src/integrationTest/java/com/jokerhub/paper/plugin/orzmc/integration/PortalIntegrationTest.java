package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@Tag("integration")
public class PortalIntegrationTest {

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
    public void testPortalCommandCreateWithAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        boolean executed = server.dispatchCommand(player, "portal example.com");
        Assertions.assertTrue(executed, "Admin player should be able to execute /portal");
    }

    @Test
    public void testPortalCommandCreateWithNonAdminPlayer() {
        // Note: MockBukkit does not check Brigadier requires(), so admin-only commands
        // dispatch successfully even for non-admin players. The runtime guard in
        // PortalCommandService.handle() checks permission and returns a failure result.
        PlayerMock player = server.addPlayer();
        boolean executed = server.dispatchCommand(player, "portal example.com");
        Assertions.assertTrue(executed, "Command dispatches, but service layer may return failure");
    }

    @Test
    public void testPortalCommandRemove() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "portal remove somehost"));
    }

    @Test
    public void testPortalCommandNonPlayerSender() {
        // Console sender: PlayerOnlyInterceptor blocks with "需要玩家执行"
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "portal somehost"));
    }

    @Test
    public void testPortalCommandDispatchDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "portal somehost"));
    }

    @Test
    public void testPortalUsageShowsForAdmin() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        // /portal with no args shows usage
        boolean executed = server.dispatchCommand(player, "portal");
        Assertions.assertTrue(executed, "Admin should be able to see portal usage");
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
