package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EntityTeleportConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ExploitHardeningConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Portals;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Styles;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
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
    public void testEntityTeleportConfigResource() throws Exception {
        YamlConfiguration cfg = load("config.yml");
        // Test each section
        Assertions.assertNotNull(WhitelistConfig.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(WhitelistKickMessage.from(cfg.getConfigurationSection("whitelist")));
        Assertions.assertNotNull(MaintenanceConfig.from(cfg.getConfigurationSection("maintenance")));
        Assertions.assertNotNull(TntConfig.from(cfg.getConfigurationSection("tnt")));
        Assertions.assertNotNull(IpWhitelist.from(cfg.getConfigurationSection("geoip")));
        Assertions.assertNotNull(CommandPolicies.from(cfg.getConfigurationSection("command_policies")));
        // 有内置默认的集合键：资源里必须显式列出默认清单，且与代码常量一致（集合键约定 β，governance §3.7）
        Assertions.assertEquals(
                SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS,
                SecurityGuardConfig.from(cfg.getConfigurationSection("guard")).blockedCommands());
        Assertions.assertEquals(
                TntConfig.DEFAULT_EXEMPT_ENTITIES,
                TntConfig.from(cfg.getConfigurationSection("tnt")).exemptEntities());
        Assertions.assertEquals(
                ExploitHardeningConfig.DEFAULT_ENTITY_COUNT_EXEMPT_TYPES,
                ExploitHardeningConfig.from(cfg.getConfigurationSection("exploit_hardening"))
                        .entityCountExemptTypes());
        // EntityTeleportConfig 读根级扁平键：默认白名单也必须显式列出且与常量一致
        Assertions.assertEquals(
                EntityTeleportConfig.DEFAULT_ENTITY_TELEPORT_WHITELIST,
                EntityTeleportConfig.from(cfg).whitelist());
        // EntityTeleportConfig reads config.yml 根级扁平键 — verify it at least doesn't crash
        Assertions.assertNotNull(EntityTeleportConfig.from(cfg));
    }

    @Test
    public void testBotConfigResource() throws Exception {
        YamlConfiguration cfg = load("config.yml");
        BotConfig bot = BotConfig.from(cfg.getConfigurationSection("bot"));
        Assertions.assertNotNull(bot);
        Assertions.assertEquals("$", bot.cmdPromptChar());
    }

    @Test
    public void testTemplatesResource() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        Assertions.assertNotNull(TemplateOptions.from(cfg));
        Assertions.assertNotNull(Styles.from(cfg.getConfigurationSection("styles")));
    }

    @Test
    public void testPortalsResource() throws Exception {
        YamlConfiguration cfg = load("portals.yml");
        Assertions.assertNotNull(Portals.from(cfg));
    }
}
