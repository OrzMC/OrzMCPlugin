package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
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

    // 注：MockBukkit 的 dispatchCommand 不会派发 Brigadier 命令（走 vanilla "Unknown command" 兜底），
    // 游戏侧 /blacklist 守卫与移除反馈由单元测试（PlayerNameRuleTest / BotCommandServiceTest /
    // AccessRuleServiceTest）覆盖；集成测试经 bot $d 走真实处理器链。

    @Test
    public void testBlacklistBotMatchTypeKeyword_rejectsNotIp() {
        // P2：$d 简写首词是匹配类型 → 拒绝并回用法提示，绝不把 "prefix bot_" 当 IP 静默误加
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() ->
                plugin.services().botModule().botInboundHandler().handleMessage("$d prefix bot_", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Match-type guard should produce response");
        Assertions.assertTrue(
                envelope.message().contains("玩家名规则请使用"), "Expected player-rule usage hint, got: " + envelope.message());
    }

    @Test
    public void testBlacklistBotRemoveMissing_reportsNotFound() {
        // P3：移除不存在的 IP → 回「未找到」而非假报「已移除」
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() ->
                plugin.services().botModule().botInboundHandler().handleMessage("$d -192.168.1.1", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Remove-missing should produce response");
        Assertions.assertTrue(
                envelope.message().contains("未在黑名单中找到"), "Expected not-found feedback, got: " + envelope.message());
    }

    @Test
    public void testBlacklistCommandNonAdminPlayer() {
        // MockBukkit does not check Brigadier requires(), so admin-only commands dispatch
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist list"));
    }

    @Test
    public void testAccessRuleServiceDirectly() {
        // Access AccessRuleService through its public API
        ConfigService configService = new ConfigService(plugin);
        AccessRuleService accessRuleService = new AccessRuleService(configService);
        Assertions.assertNotNull(accessRuleService);
        Assertions.assertTrue(accessRuleService.getIpPatterns().isEmpty(), "Blacklist should start empty");

        // Test add
        accessRuleService.addIpPattern("10.0.0.1");
        Assertions.assertEquals(1, accessRuleService.getIpPatterns().size());
        Assertions.assertTrue(accessRuleService.isIpBlocked("10.0.0.1"));

        // Test remove
        accessRuleService.removeIpPattern("10.0.0.1");
        Assertions.assertTrue(accessRuleService.getIpPatterns().isEmpty());

        // Test IP matching
        accessRuleService.addIpPattern("192.168.1.*");
        Assertions.assertTrue(accessRuleService.isIpBlocked("192.168.1.100"));
        Assertions.assertFalse(accessRuleService.isIpBlocked("192.168.2.100"));
    }

    @Test
    public void testPlayerNameRuleViaBotCommand() {
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        plugin.services().botModule().botInboundHandler().handleMessage("$d player prefix bot_", true, got::set);
        server.getScheduler().performOneTick();
        Assertions.assertNotNull(got.get(), "Player rule add should produce response");
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
