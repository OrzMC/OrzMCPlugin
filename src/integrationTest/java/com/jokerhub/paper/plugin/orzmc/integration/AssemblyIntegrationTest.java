package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.OrzServices;
import com.jokerhub.paper.plugin.orzmc.assembly.BotModule;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

@Tag("integration")
public class AssemblyIntegrationTest {

    private ServerMock server;
    private OrzMC plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testAssembleCreatesAllModules() {
        OrzServices services = plugin.services();
        Assertions.assertNotNull(services, "OrzServices should not be null after plugin load");
        Assertions.assertNotNull(services.botModule(), "BotModule should not be null");
    }

    @Test
    public void testSetupAllDuringLoadSucceeds() {
        // setupAll was called during plugin load (OrzMC.onEnable → OrzServices.setupAll)
        // Verify that the plugin is in a loaded state
        OrzServices services = plugin.services();
        Assertions.assertNotNull(services);
        // Verify the config service was initialized (which happens in setupAll path)
        Assertions.assertNotNull(services.botModule().notifier());
    }

    @Test
    public void testShutdownAllTeardownWithoutErrors() {
        OrzServices services = plugin.services();
        Assertions.assertDoesNotThrow(() -> services.shutdownAll());
    }

    @Test
    public void testBotModuleHasBackReferences() {
        BotModule botModule = plugin.services().botModule();
        // Verify command routing works via bot command dispatch
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() -> botModule.botInboundHandler().handleMessage("$d", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Bot command should produce a response");
    }

    @Test
    public void testBotModuleHasBlacklistServiceBackReference() {
        BotModule botModule = plugin.services().botModule();
        // $d is the BLACKLIST command. With no args, it lists patterns.
        // This exercises blacklistService.getPatterns() via the bot command path.
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() -> botModule.botInboundHandler().handleMessage("$d", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Blacklist bot command should produce a response");
        // Message should mention patterns or being empty (note: we receive the raw rendered template)
        String msg = envelope.message();
        Assertions.assertTrue(msg != null && !msg.isEmpty(), "Message should not be empty");
    }

    @Test
    public void testBotModuleAccessorReturnsNonNull() {
        BotModule botModule = plugin.services().botModule();
        Assertions.assertNotNull(botModule);
        Assertions.assertNotNull(botModule.botCommandService());
        Assertions.assertNotNull(botModule.botMessageService());
        Assertions.assertNotNull(botModule.notifier());
        Assertions.assertNotNull(botModule.botStatusService());
        Assertions.assertNotNull(botModule.botInboundHandler());
    }
}
