package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
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
        service = new ServerFeedbackService(server, configs, styles);
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
    }

    @Test
    void buildMaintenanceMotd_containsMaintenanceWarn() {
        MaintenanceConfig maint = new MaintenanceConfig(true, 300L, 5, "维护中请稍后");
        BotConfig bot = new BotConfig("$", null, null);
        when(configs.maintenance()).thenReturn(maint);
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        Component result = service.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("维护中"));
        assertTrue(plain.contains("维护中请稍后"));
    }

    @Test
    void buildMaintenanceMotd_withDiscord() {
        MaintenanceConfig maint = new MaintenanceConfig(true, 300L, 5, "维护公告");
        BotConfig bot = new BotConfig("$", "https://discord.gg/test", null);
        when(configs.maintenance()).thenReturn(maint);
        when(configs.bot()).thenReturn(bot);
        when(styles.warn(anyString())).thenReturn(Component.text("⚠ 维护中"));
        when(styles.info(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        Component result = service.buildMaintenanceMotd();
        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("维护中"), "should contain warn text: " + plain);
        assertTrue(plain.contains("discord.gg/test"), "should contain discord link: " + plain);
    }
}
