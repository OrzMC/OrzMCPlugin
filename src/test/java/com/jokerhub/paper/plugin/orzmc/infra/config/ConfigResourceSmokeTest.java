package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigResourceSmokeTest {
    private YamlConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testMainConfigResource() throws Exception {
        YamlConfiguration cfg = load("config.yml");
        Assertions.assertNotNull(TypedConfigs.MainConfig.from(cfg));
    }

    @Test
    public void testBotConfigResource() throws Exception {
        YamlConfiguration cfg = load("bot.yml");
        Assertions.assertNotNull(TypedConfigs.BotConfig.from(cfg));
    }

    @Test
    public void testMaintenanceConfigResource() throws Exception {
        YamlConfiguration cfg = load("maintenance.yml");
        Assertions.assertNotNull(TypedConfigs.MaintenanceConfig.from(cfg));
    }

    @Test
    public void testWhitelistConfigResource() throws Exception {
        YamlConfiguration cfg = load("whitelist.yml");
        Assertions.assertNotNull(TypedConfigs.WhitelistConfig.from(cfg));
        Assertions.assertNotNull(TypedConfigs.WhitelistKickMessage.from(cfg));
    }

    @Test
    public void testTemplatesResource() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        Assertions.assertNotNull(TypedConfigs.TemplateOptions.from(cfg));
        Assertions.assertNotNull(TypedConfigs.Templates.from(cfg));
    }

    @Test
    public void testTntResource() throws Exception {
        YamlConfiguration cfg = load("tnt.yml");
        Assertions.assertNotNull(TypedConfigs.TntConfig.from(cfg));
    }

    @Test
    public void testIpWhitelistResource() throws Exception {
        YamlConfiguration cfg = load("ip_whitelist.yml");
        Assertions.assertNotNull(TypedConfigs.IpWhitelist.from(cfg));
    }

    @Test
    public void testNotificationsResource() throws Exception {
        YamlConfiguration cfg = load("notifications.yml");
        Assertions.assertNotNull(TypedConfigs.Notifications.from(cfg));
    }

    @Test
    public void testStylesResource() throws Exception {
        YamlConfiguration cfg = load("styles.yml");
        Assertions.assertNotNull(TypedConfigs.Styles.from(cfg));
    }

    @Test
    public void testPortalsResource() throws Exception {
        YamlConfiguration cfg = load("portals.yml");
        Assertions.assertNotNull(TypedConfigs.Portals.from(cfg));
    }

    @Test
    public void testCommandsResource() throws Exception {
        YamlConfiguration cfg = load("commands.yml");
        Assertions.assertNotNull(TypedConfigs.CommandPolicies.from(cfg));
    }
}
