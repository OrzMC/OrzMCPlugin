package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MainConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.NotifyPolicy.Notifications;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Portals;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Styles;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
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
        Assertions.assertNotNull(WhitelistConfig.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(WhitelistKickMessage.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(MaintenanceConfig.from(cfg.getConfigurationSection("maintenance")));
        Assertions.assertNotNull(TntConfig.from(cfg.getConfigurationSection("tnt")));
        Assertions.assertNotNull(IpWhitelist.from(cfg.getConfigurationSection("geoip")));
        Assertions.assertNotNull(CommandPolicies.from(cfg.getConfigurationSection("command_policies")));
        // Legacy MainConfig reads flat keys — verify it at least doesn't crash
        Assertions.assertNotNull(MainConfig.from(cfg));
    }

    @Test
    public void testBotConfigResource() throws Exception {
        YamlConfiguration cfg = load("bot.yml");
        Assertions.assertNotNull(BotConfig.from(cfg));
    }

    @Test
    public void testTemplatesResource() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        Assertions.assertNotNull(TemplateOptions.from(cfg));
        Assertions.assertNotNull(Templates.from(cfg));
        Assertions.assertNotNull(Notifications.from(cfg.getConfigurationSection("notifications")));
        Assertions.assertNotNull(Styles.from(cfg.getConfigurationSection("styles")));
    }

    @Test
    public void testPortalsResource() throws Exception {
        YamlConfiguration cfg = load("portals.yml");
        Assertions.assertNotNull(Portals.from(cfg));
    }
}
