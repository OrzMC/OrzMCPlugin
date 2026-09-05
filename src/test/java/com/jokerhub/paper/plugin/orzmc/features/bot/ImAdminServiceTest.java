package com.jokerhub.paper.plugin.orzmc.features.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImBindings;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.BuiltinImDriver;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthAccessor;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ImAdminService 单测（/config im 逻辑层）：权限拒绝（D10）、bind 写 im_bindings.yml 往返 + 候选清除（D11）、
 * status/setup 输出、test 引导与参数校验。
 */
class ImAdminServiceTest {

    private ConfigService configService;
    private YamlConfiguration bindingsStore; // 模拟 im_bindings.yml 文件态
    private YamlConfiguration imStore;
    private List<String> sent;
    private BuiltinImDriver builtin;
    private ImAdminService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        bindingsStore = new YamlConfiguration();
        imStore = new YamlConfiguration();
        imStore.set("backend", "builtin");
        imStore.set("platforms.qq.enabled", true);
        imStore.set("platforms.qq.app_id", "app-1");
        imStore.set("platforms.qq.client_secret", "secret-1");
        when(configService.getConfig("im")).thenReturn(imStore);
        when(configService.getConfig("im_bindings")).thenReturn(bindingsStore);
        // updateConfig = 内存原子变更（模拟 ConfigManager.updateConfig 语义）
        doAnswer(inv -> {
                    Consumer<org.bukkit.configuration.file.FileConfiguration> updater = inv.getArgument(1);
                    updater.accept(bindingsStore);
                    return true;
                })
                .when(configService)
                .updateConfig(eq("im_bindings"), any());

        // 注：不调用 builtin.setup() → 不启动真实网络；D11 候选存于驱动实例内
        builtin = new BuiltinImDriver(
                silentLogger(),
                mock(ServerScheduler.class),
                configService,
                mock(BotInboundHandler.class),
                (message, format) -> List.of(message),
                new HealthRegistry());
        builtin.candidates().record("qq:group:G-STRANGER");
        service = new ImAdminService(
                new OrzTextStyles(stylesConfig()), configService, new HealthAccessor(new HealthRegistry()), builtin);
    }

    private static ConfigService stylesConfig() {
        ConfigService cfg = mock(ConfigService.class);
        when(cfg.getConfig("config")).thenReturn(new YamlConfiguration());
        return cfg;
    }

    private static String text(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private void sentTo(CommandSender sender) {
        doAnswer(inv -> {
                    sent.add(text(inv.getArgument(0)));
                    return null;
                })
                .when(sender)
                .sendMessage(any(Component.class));
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void bind_writesBindingAndClearsCandidate() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.bind(console, "qq", "group", "G-1", "admin_group");
        service.bind(console, "qq", "user", "U-1", "admin_dm");

        ConfigurationSection qq = bindingsStore.getConfigurationSection("sessions.qq");
        assertEquals("group:G-1", qq.getString("admin_group"));
        assertEquals("user:U-1", qq.getString("admin_dm"));
        ImBindings bindings = ImBindings.from(bindingsStore);
        assertEquals("qq:group:G-1", bindings.conversation("qq").adminGroup(), "读回会话补平台前缀");
        assertTrue(bindings.conversation("qq").enabled());
        assertTrue(sent.get(0).contains("已写入并持久化"), sent.get(0));
    }

    @Test
    void bind_clearsDiscoveredCandidateForBoundSession() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);
        assertTrue(!builtin.candidates().isEmpty());

        service.bind(console, "qq", "group", "G-STRANGER", "admin_group");

        assertTrue(builtin.candidates().isEmpty(), "绑定成功后候选应清除（D11）");
    }

    @Test
    void bind_invalidArgs_rejectedWithoutWrite() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.bind(console, "qq", "channel", "G-1", "admin_group");
        assertTrue(sent.get(sent.size() - 1).contains("chat_type 仅支持"));

        service.bind(console, "qq", "group", "G-1", "owner");
        assertTrue(sent.get(sent.size() - 1).contains("role 仅支持"));

        service.bind(console, "", "group", "G-1", "admin_group");
        assertTrue(sent.get(sent.size() - 1).contains("platform 不能为空"));

        assertNull(bindingsStore.getConfigurationSection("sessions"), "非法参数一律不写入");
    }

    @Test
    void permissionDenied_nonAdminRejectedWithoutWrite() {
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission("orzmc.admin")).thenReturn(false);
        sent = new ArrayList<>();
        sentTo(player);

        service.bind(player, "qq", "group", "G-1", "admin_group");

        assertEquals(1, sent.size(), "非管理只应收到一条拒绝提示");
        assertTrue(sent.get(0).contains("仅控制台/游戏内 op"), sent.get(0));
        assertNull(bindingsStore.getConfigurationSection("sessions"), "拒绝后不应写入");
    }

    @Test
    void status_reportsBackendBindingsAndCandidates() {
        bindingsStore.set("sessions.qq.admin_group", "group:G-1");
        bindingsStore.set("sessions.qq.admin_dm", "user:U-1");
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.status(console);

        String text = String.join("\n", sent);
        assertTrue(text.contains("backend: builtin"), text);
        assertTrue(text.contains("QQ 平台: 启用"), text);
        assertTrue(text.contains("qq:group:G-1"), text);
        assertTrue(text.contains("qq:user:U-1"), text);
        assertTrue(text.contains("qq:group:G-STRANGER"), "候选应出现在 status（D11）");
        assertTrue(text.contains("/config im bind qq group G-STRANGER admin_group"), "候选区应给出可复制执行的完整 bind 命令（UX）");
        assertTrue(text.contains("/config im bind qq group G-STRANGER player_group"), "群候选应含玩家群建议命令");
    }

    @Test
    void status_backendEasybot_showsEasybotBranch() {
        imStore.set("backend", "easybot");
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.status(console);

        String text = String.join("\n", sent);
        assertTrue(text.contains("backend: easybot"), text);
        assertTrue(text.contains("EasyBot 网关"), text);
    }

    @Test
    void setup_printsChecklist() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.setup(console);

        String text = String.join("\n", sent);
        assertTrue(text.contains("backend: builtin"), text);
        assertTrue(text.contains("凭据齐备"), text);
        assertTrue(text.contains("/config im bind"), text);
    }

    @Test
    void test_whenDriverHasNoPlatform_guidanceError() {
        // 驱动存在但未 setup（无已启动平台）→ sendTo false → 错误引导
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        sent = new ArrayList<>();
        sentTo(console);

        service.test(console, "qq", "group", "G-1", "你好");

        assertTrue(sent.get(sent.size() - 1).contains("无可用平台投递"), sent.get(sent.size() - 1));
    }

    @Test
    void sessionValue_andBindError_shapeChecks() {
        assertNull(ImAdminService.bindError("qq", "group", "G-1", "admin_group"));
        assertNull(ImAdminService.bindError("qq", "user", "U-1", "admin_dm"));
        assertEquals("group:G-1", ImAdminService.sessionValue("group", "G-1"));
        assertEquals("group:G-1", ImAdminService.sessionValue("group", "group:G-1"), "已带前缀原样使用");
        assertTrue(ImAdminService.bindError("qq", "user", "U-1", "owner") != null);
    }

    private static ServerLogger silentLogger() {
        Logger raw = Logger.getLogger("im-admin-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }
}
