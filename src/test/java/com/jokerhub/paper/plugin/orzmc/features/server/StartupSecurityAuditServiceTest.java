package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class StartupSecurityAuditServiceTest {

    @TempDir
    Path tmpDir;

    private ServerFacade server;
    private TypedConfigProvider configs;
    private Notifier notifier;
    private Server bukkit;
    private PluginManager pluginManager;
    private MessageEnvelope rendered;
    private StartupSecurityAuditService service;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        configs = mock(TypedConfigProvider.class);
        notifier = mock(Notifier.class);
        bukkit = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(server.server()).thenReturn(bukkit);
        when(server.logger()).thenReturn(mock(java.util.logging.Logger.class));
        when(bukkit.getPluginManager()).thenReturn(pluginManager);
        rendered = MessageEnvelope.publicMessage("");
        when(configs.renderTemplate(anyString(), anyMap(), anyString())).thenReturn(rendered);
        service = new StartupSecurityAuditService(
                server, configs, notifier, tmpDir.resolve("server.properties").toFile());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> runAndCaptureVars() {
        service.run(mock(ServerLoadEvent.class));
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderTemplate(eq(TemplateKeys.SECURITY_AUDIT), varsCaptor.capture(), anyString());
        verify(notifier).event(eq(TemplateKeys.SECURITY_AUDIT), eq(rendered));
        return varsCaptor.getValue();
    }

    @Test
    void run_secureDefaults_collected() {
        when(bukkit.getOnlineMode()).thenReturn(true);
        when(bukkit.hasWhitelist()).thenReturn(true);
        when(bukkit.isWhitelistEnforced()).thenReturn(true);
        when(bukkit.getOperators()).thenReturn(Set.of());

        Map<String, String> vars = runAndCaptureVars();

        assertEquals("正版验证开启", vars.get("online_mode"));
        assertEquals("禁用", vars.get("command_block"));
        assertEquals("未启用", vars.get("rcon"));
        assertEquals("开启（强制）", vars.get("whitelist"));
        assertEquals("0 个", vars.get("ops"));
        assertEquals("均未安装（高危）", vars.get("plugins"));
    }

    @Test
    void run_serverProperties_rconAndCommandBlockReflected() throws IOException {
        Files.writeString(
                tmpDir.resolve("server.properties"),
                "enable-command-block=true\nenable-rcon=true\nrcon.port=25575\n",
                StandardCharsets.UTF_8);

        Map<String, String> vars = runAndCaptureVars();

        assertEquals("启用（高危）", vars.get("command_block"));
        assertEquals("启用（端口: 25575）", vars.get("rcon"));
    }

    @Test
    void run_opsAndPlugins_listed() {
        OfflinePlayer steve = offlinePlayer("steve");
        OfflinePlayer alex = offlinePlayer("alex");
        when(bukkit.getOperators()).thenReturn(Set.of(steve, alex));
        when(pluginManager.getPlugin("LuckPerms")).thenReturn(mock(Plugin.class));

        Map<String, String> vars = runAndCaptureVars();

        assertEquals("2 个: alex, steve", vars.get("ops"));
        assertEquals("LuckPerms", vars.get("plugins"));
    }

    @Test
    void run_offlineModeAndWhitelistOff_reported() {
        when(bukkit.getOnlineMode()).thenReturn(false);
        when(bukkit.hasWhitelist()).thenReturn(false);

        Map<String, String> vars = runAndCaptureVars();

        assertEquals("离线（高危）", vars.get("online_mode"));
        assertEquals("关闭", vars.get("whitelist"));
    }

    @Test
    void run_missingServerProperties_logsWarningOnce() {
        Map<String, String> vars = runAndCaptureVars();

        verify(server).logger();
        assertEquals("禁用", vars.get("command_block"));
    }

    private static OfflinePlayer offlinePlayer(String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
