package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证配置解析与健康检查健壮性。
 *
 * <p>新式 config.yml 全分段可解析；分段缺失时 {@code TypedConfig.from(null)} 返回安全默认；
 * 健康检查对空配置不崩溃、对完整新配置无致命问题。
 */
public class ConfigRobustnessTest {

    private FileConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** 空 config.yml：无任何分段（等价于配置缺失的健壮性场景）。 */
    private FileConfiguration emptyConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        // 故意不设任何键
        return cfg;
    }

    /** 新式 config.yml：包含所有分段 */
    private FileConfiguration newStyleConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("whitelist.force_whitelist", true);
        cfg.set("whitelist.cleanup_inactive_days", 90);
        cfg.set("whitelist.pagination_delay_ticks", 5);
        cfg.set("whitelist.kick_message.title", "测试踢出消息");
        cfg.set("whitelist.kick_message.qq_group_id", "123456");
        List<Map<String, String>> kickUps = new ArrayList<>();
        Map<String, String> kickItem = new HashMap<>();
        kickItem.put("name", "测试链接");
        kickItem.put("platform", "https://example.com");
        kickUps.add(kickItem);
        cfg.set("whitelist.kick_message.ups", kickUps);
        cfg.set("maintenance.optimize_enabled", false);
        cfg.set("maintenance.optimize_tick_time_threshold", 300);
        cfg.set("maintenance.backup_retention_count", 5);
        cfg.set("tnt.enable", false);
        cfg.set("tnt.enable_respawn_anchor", false);
        cfg.set("tnt.place_cooldown", 5);
        cfg.set("tnt.notify_throttle_ms", 1000L);
        cfg.set("geoip.allow_country_code", List.of());
        cfg.set("command_policies.tpbow.cooldown_secs", 3);
        cfg.set("command_policies.tpbow.admin_only", false);
        cfg.set("command_policies.menu.cooldown_secs", 0);
        cfg.set("command_policies.menu.admin_only", false);
        cfg.set("command_policies.portal.cooldown_secs", 5);
        cfg.set("command_policies.portal.admin_only", true);
        return cfg;
    }

    // ---------------------------------------------------------------
    // 场景 A：新式 config.yml → 应有分段，不依赖旧文件
    // ---------------------------------------------------------------

    @Test
    public void testNewStyleConfigExtractsSections() {
        FileConfiguration config = newStyleConfig();

        Assertions.assertNotNull(config.getConfigurationSection("whitelist"), "新式 config 应有 whitelist 分段");
        Assertions.assertNotNull(config.getConfigurationSection("maintenance"), "新式 config 应有 maintenance 分段");
        Assertions.assertNotNull(config.getConfigurationSection("tnt"), "新式 config 应有 tnt 分段");
        Assertions.assertNotNull(config.getConfigurationSection("geoip"), "新式 config 应有 geoip 分段");
        Assertions.assertNotNull(
                config.getConfigurationSection("command_policies"), "新式 config 应有 command_policies 分段");
    }

    @Test
    public void testNewStyleConfigTypedConfigsParse() {
        FileConfiguration config = newStyleConfig();

        WhitelistConfig wl = WhitelistConfig.from(config.getConfigurationSection("whitelist"));
        Assertions.assertTrue(wl.forceWhitelist());
        Assertions.assertEquals(90, wl.cleanupInactiveDays());

        MaintenanceConfig mt = MaintenanceConfig.from(config.getConfigurationSection("maintenance"));
        Assertions.assertFalse(mt.optimizeEnabled());

        TntConfig tnt = TntConfig.from(config.getConfigurationSection("tnt"));
        Assertions.assertFalse(tnt.enable());
    }

    // ---------------------------------------------------------------
    // 场景 B：TypedConfig.from(null) → 安全默认值（分段缺失时健壮性）
    // ---------------------------------------------------------------

    @Test
    public void testNullSectionIsHandledGracefully() {
        // TypedConfigs.from() 在 section 为 null 时应返回安全的默认值
        WhitelistConfig wl = WhitelistConfig.from(null);
        Assertions.assertTrue(wl.forceWhitelist(), "null 时应有默认值");
        Assertions.assertEquals(90, wl.cleanupInactiveDays());

        MaintenanceConfig mt = MaintenanceConfig.from(null);
        Assertions.assertFalse(mt.optimizeEnabled());

        TntConfig tnt = TntConfig.from(null);
        Assertions.assertFalse(tnt.enable());

        IpWhitelist ip = IpWhitelist.from(null);
        Assertions.assertTrue(ip.allowCountryCode().isEmpty());

        CommandPolicies cp = CommandPolicies.from(null);
        Assertions.assertTrue(cp.policies().isEmpty());
    }

    // ---------------------------------------------------------------
    // 场景 C：健康检查在空配置上不应崩溃
    // ---------------------------------------------------------------

    @Test
    public void testHealthCheckWithEmptyConfigDoesNotCrash() throws Exception {
        // 模拟配置缺失：config.yml 无任何分段，但其他文件存在
        // 健康检查应优雅处理，不抛出异常
        Map<String, FileConfiguration> cfgs = new HashMap<>();
        cfgs.put("config", emptyConfig());
        cfgs.put("easybot", load("easybot.yml"));
        cfgs.put("guide_book", load("guide_book.yml"));
        cfgs.put("templates", load("templates.yml"));
        cfgs.put("portals", load("portals.yml"));

        Assertions.assertDoesNotThrow(() -> ConfigHealthCheck.validateAll(cfgs::get), "旧式配置也不应抛出异常");
    }

    // ---------------------------------------------------------------
    // 场景 E：健康检查在新式完整配置上应通过
    // ---------------------------------------------------------------

    @Test
    public void testHealthCheckWithNewStyleConfigPasses() throws Exception {
        Map<String, FileConfiguration> cfgs = new HashMap<>();
        cfgs.put("config", newStyleConfig());
        cfgs.put("easybot", load("easybot.yml"));
        cfgs.put("guide_book", load("guide_book.yml"));
        cfgs.put("templates", load("templates.yml"));
        cfgs.put("portals", load("portals.yml"));

        List<String> issues = ConfigHealthCheck.validateAll(cfgs::get);
        List<String> fatal = new ArrayList<>();
        for (String s : issues) {
            if (s.startsWith("缺失:")
                    || s.startsWith("非法:")
                    || s.startsWith("类型错误:")
                    || s.startsWith("通知事件缺少模板:")
                    || s.startsWith("模板变量未知:")) {
                fatal.add(s);
            }
        }
        Assertions.assertTrue(fatal.isEmpty(), "新式完整配置应无致命问题，发现: " + String.join("\n", fatal));
    }
}
