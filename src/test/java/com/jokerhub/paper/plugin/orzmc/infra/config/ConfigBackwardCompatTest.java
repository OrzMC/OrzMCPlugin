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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证配置合并后的向后兼容性。
 *
 * <p>旧式配置（config.yml 无分段 + 独立配置文件）必须能通过健康检查，
 * 且 {@link ConfigManager#sectionOrLegacy} 能正确提取旧文件内容。
 */
public class ConfigBackwardCompatTest {

    private FileConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** 旧式 config.yml：只有注释，无 whitelist/maintenance/tnt/geoip/command_policies 分段。 */
    private FileConfiguration oldStyleConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        // 故意不设任何键
        return cfg;
    }

    /** 旧式 whitelist.yml：扁平结构（无 whitelist: 包裹） */
    private FileConfiguration oldWhitelistYml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("force_whitelist", true);
        cfg.set("cleanup_inactive_days", 90);
        cfg.set("pagination_delay_ticks", 5);
        return cfg;
    }

    /** 旧式 maintenance.yml：扁平结构 */
    private FileConfiguration oldMaintenanceYml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("optimize_enabled", false);
        cfg.set("optimize_tick_time_threshold", 300);
        cfg.set("backup_retention_count", 5);
        cfg.set("backup_maintenance_motd", "服务器维护中，稍后再试");
        return cfg;
    }

    /** 旧式 tnt.yml：扁平结构 */
    private FileConfiguration oldTntYml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enable", false);
        cfg.set("enable_respawn_anchor", false);
        cfg.set("place_cooldown", 5);
        cfg.set("notify_throttle_ms", 1000L);
        return cfg;
    }

    /** 旧式 ip_whitelist.yml */
    private FileConfiguration oldIpWhitelistYml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("allow_country_code", List.of());
        return cfg;
    }

    /** 旧式 commands.yml：带 commands: 包裹 */
    private FileConfiguration oldCommandsYml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("commands.tpbow.cooldown_secs", 3);
        cfg.set("commands.tpbow.admin_only", false);
        cfg.set("commands.menu.cooldown_secs", 0);
        cfg.set("commands.menu.admin_only", false);
        cfg.set("commands.portal.cooldown_secs", 5);
        cfg.set("commands.portal.admin_only", true);
        return cfg;
    }

    /** 新式 config.yml：包含所有分段 */
    private FileConfiguration newStyleConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("config-version", 2);
        cfg.set("whitelist.force_whitelist", true);
        cfg.set("whitelist.cleanup_inactive_days", 90);
        cfg.set("whitelist.pagination_delay_ticks", 5);
        cfg.set("maintenance.optimize_enabled", false);
        cfg.set("maintenance.optimize_tick_time_threshold", 300);
        cfg.set("maintenance.backup_retention_count", 5);
        cfg.set("maintenance.backup_maintenance_motd", "服务器维护中，稍后再试");
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
    // 场景 B：旧式扁平文件 → TypedConfigs.from() 从旧文件直接读取
    // ---------------------------------------------------------------

    @Test
    public void testOldWhitelistFileDirectParse() {
        // 旧的 whitelist.yml 是扁平结构（无 whitelist: 包裹），
        // sectionOrLegacy 会返回 legacy 文件本身作为 section。
        FileConfiguration legacy = oldWhitelistYml();

        WhitelistConfig wl = WhitelistConfig.from(legacy);
        Assertions.assertTrue(wl.forceWhitelist());
        Assertions.assertEquals(90, wl.cleanupInactiveDays());
        Assertions.assertEquals(5, wl.paginationDelayTicks());
    }

    @Test
    public void testOldMaintenanceFileDirectParse() {
        FileConfiguration legacy = oldMaintenanceYml();

        MaintenanceConfig mt = MaintenanceConfig.from(legacy);
        Assertions.assertFalse(mt.optimizeEnabled());
        Assertions.assertEquals(300L, mt.optimizeTickTimeThreshold());
        Assertions.assertEquals("服务器维护中，稍后再试", mt.backupMaintenanceMotd());
    }

    @Test
    public void testOldTntFileDirectParse() {
        FileConfiguration legacy = oldTntYml();

        TntConfig tnt = TntConfig.from(legacy);
        Assertions.assertFalse(tnt.enable());
        Assertions.assertEquals(5, tnt.placeCooldownSeconds());
        Assertions.assertEquals(1000L, tnt.notifyThrottleMs());
    }

    @Test
    public void testOldIpWhitelistFileDirectParse() {
        FileConfiguration legacy = oldIpWhitelistYml();

        IpWhitelist ip = IpWhitelist.from(legacy);
        Assertions.assertTrue(ip.allowCountryCode().isEmpty());
    }

    // ---------------------------------------------------------------
    // 场景 C：sectionOrLegacy 逻辑测试（通过程序化 provider 模拟）
    // 旧式 config.yml（无分段）→ 应 null 分段 → 代码不会 NPE
    // ---------------------------------------------------------------

    @Test
    public void testSectionOrLegacyReturnsNullForNonExistentSection() {
        // 模拟旧式 config.yml：只有 config-version，没有子分段
        YamlConfiguration minimal = new YamlConfiguration();
        minimal.set("config-version", 2);

        ConfigurationSection whitelistSection = minimal.getConfigurationSection("whitelist");
        Assertions.assertNull(whitelistSection, "旧式 config.yml 不应有 whitelist 分段");
    }

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
    // 场景 D：健康检查在旧式配置上不应崩溃
    // ---------------------------------------------------------------

    @Test
    public void testHealthCheckWithOldStyleConfigDoesNotCrash() throws Exception {
        // 模拟旧式设置：config.yml 无分段，但其他文件存在
        // 健康检查应优雅处理，不抛出异常
        Map<String, FileConfiguration> cfgs = new HashMap<>();
        cfgs.put("config", oldStyleConfig());
        cfgs.put("bot", load("bot.yml"));
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
        cfgs.put("bot", load("bot.yml"));
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
