package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceReason;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ServerFeedbackServiceTest extends ServiceTestBase {

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private ServerLoadEvent event;

    private ServerFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new ServerFeedbackService(server, configs, styles, new MaintenanceModeService());
    }

    @Test
    void buildServerLoadMessage_startup_containsStartup() {
        Server bukkitServer = mock(Server.class);
        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getOnlineMode()).thenReturn(true);
        when(bukkitServer.getMinecraftVersion()).thenReturn("1.21.4");
        when(configs.bot()).thenReturn(new BotConfig("$", null, null));
        when(event.getType()).thenReturn(ServerLoadEvent.LoadType.STARTUP);

        String msg = service.buildServerLoadMessage(event);
        assertTrue(msg.contains("Minecraft 1.21.4"));
        assertTrue(msg.contains("正版服"));
        assertTrue(msg.contains("启动完成"));
        assertTrue(msg.contains("$h"));
        // 分割线统一 33 连字符（群消息统一样式防回归）
        assertTrue(msg.contains("\n---------------------------------\n"), "启动消息分割线应为 33 连字符: " + msg);
    }

    @Test
    void buildServerLoadMessage_reload_containsReload() {
        Server bukkitServer = mock(Server.class);
        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getOnlineMode()).thenReturn(false);
        when(bukkitServer.getMinecraftVersion()).thenReturn("1.21");
        when(configs.bot()).thenReturn(new BotConfig("!", null, null));
        when(event.getType()).thenReturn(ServerLoadEvent.LoadType.RELOAD);

        String msg = service.buildServerLoadMessage(event);
        assertTrue(msg.contains("离线服"));
        assertTrue(msg.contains("重启完成"));
        assertTrue(msg.contains("!h"));
        assertTrue(msg.contains("\n---------------------------------\n"), "重启消息分割线应为 33 连字符: " + msg);
    }

    @Test
    void buildMaintenanceMotd_containsMaintenanceWarn() {
        BotConfig bot = new BotConfig("$", null, null);
        when(configs.templates()).thenReturn(maintenanceTemplates("维护中请稍后", null, null, null));
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        MaintenanceModeService mode = new MaintenanceModeService();
        mode.enter(MaintenanceReason.BACKUP);
        ServerFeedbackService svc = new ServerFeedbackService(server, configs, styles, mode);

        Component result = svc.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("维护中"));
        assertTrue(plain.contains("维护中请稍后"));
    }

    @Test
    void buildMaintenanceMotd_withDiscord() {
        BotConfig bot = new BotConfig("$", "https://discord.gg/test", null);
        when(configs.templates()).thenReturn(maintenanceTemplates("维护公告", null, null, null));
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        MaintenanceModeService mode = new MaintenanceModeService();
        mode.enter(MaintenanceReason.BACKUP);
        ServerFeedbackService svc = new ServerFeedbackService(server, configs, styles, mode);

        Component result = svc.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("维护中"), "should contain warn text: " + plain);
        assertTrue(plain.contains("discord.gg/test"), "should contain discord link: " + plain);
    }

    @Test
    void buildMaintenanceMotd_withoutPlaceholders_appendsProgressLine() {
        // 纯场景文案（不含 {stage}/{percent}/{eta}）+ 有进度 → 统一渲染入口追加进度行（progress_line 模板默认格式）
        BotConfig bot = new BotConfig("$", null, null);
        when(configs.templates()).thenReturn(maintenanceTemplates("备份中", null, null, null));
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        MaintenanceModeService mode = new MaintenanceModeService();
        mode.enter(MaintenanceReason.BACKUP);
        mode.updateProgress("区块", 45, 35);
        ServerFeedbackService svc = new ServerFeedbackService(server, configs, styles, mode);

        Component result = svc.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertEquals("⚠ 维护中\n备份中\n进度：区块 45% 预计剩余 35秒", plain);
    }

    @Test
    void buildMaintenanceMotd_manual_noProgress_omitsProgressLine() {
        BotConfig bot = new BotConfig("$", null, null);
        when(configs.templates()).thenReturn(maintenanceTemplates(null, null, "手动维护中", null));
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        MaintenanceModeService mode = new MaintenanceModeService();
        mode.enter(MaintenanceReason.MANUAL);
        ServerFeedbackService svc = new ServerFeedbackService(server, configs, styles, mode);

        Component result = svc.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("手动维护中"), "manual MOTD 文案: " + plain);
        assertFalse(plain.contains("进度"), "手动维护无进度时不应渲染进度行: " + plain);
    }

    @Test
    void buildMaintenanceMotd_withPlaceholders_noSeparateProgressLine() {
        // 场景模板含 {stage}/{percent}/{eta} → 占位符替换进场景文案，不再追加独立进度行（防重复）
        BotConfig bot = new BotConfig("$", null, null);
        when(configs.templates()).thenReturn(maintenanceTemplates("备份 {stage} {percent}% {eta}秒", null, null, null));
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        MaintenanceModeService mode = new MaintenanceModeService();
        mode.enter(MaintenanceReason.BACKUP);
        mode.updateProgress("区块", 45, 35);
        ServerFeedbackService svc = new ServerFeedbackService(server, configs, styles, mode);

        Component result = svc.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        // 精确断言：只有场景文案一行（占位符已被替换），无第二行「进度：区块 45% 预计剩余 35秒」重复
        assertEquals("⚠ 维护中\n备份 区块 45% 35秒", plain);
    }
}
