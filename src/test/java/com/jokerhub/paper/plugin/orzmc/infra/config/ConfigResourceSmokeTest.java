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
        // Test each section
        Assertions.assertNotNull(TypedConfigs.WhitelistConfig.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(TypedConfigs.WhitelistKickMessage.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(TypedConfigs.MaintenanceConfig.from(cfg.getConfigurationSection("maintenance")));
        Assertions.assertNotNull(TypedConfigs.TntConfig.from(cfg.getConfigurationSection("tnt")));
        Assertions.assertNotNull(TypedConfigs.IpWhitelist.from(cfg.getConfigurationSection("geoip")));
        Assertions.assertNotNull(TypedConfigs.CommandPolicies.from(cfg.getConfigurationSection("command_policies")));
        // Legacy MainConfig reads flat keys — verify it at least doesn't crash
        Assertions.assertNotNull(TypedConfigs.MainConfig.from(cfg));
    }

    @Test
    public void testBotConfigResource() throws Exception {
        YamlConfiguration cfg = load("bot.yml");
        Assertions.assertNotNull(TypedConfigs.BotConfig.from(cfg));
    }

    @Test
    public void testTemplatesResource() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        Assertions.assertNotNull(TypedConfigs.TemplateOptions.from(cfg));
        Assertions.assertNotNull(TypedConfigs.Templates.from(cfg));
        Assertions.assertNotNull(TypedConfigs.Notifications.from(cfg.getConfigurationSection("notifications")));
        Assertions.assertNotNull(TypedConfigs.Styles.from(cfg.getConfigurationSection("styles")));
    }

    @Test
    public void testPortalsResource() throws Exception {
        YamlConfiguration cfg = load("portals.yml");
        Assertions.assertNotNull(TypedConfigs.Portals.from(cfg));
    }
}
