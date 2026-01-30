package com.jokerhub.paper.plugin.orzmc.infra.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypedConfigs {
    private TypedConfigs() {}

    public record MainConfig(
            boolean forceWhitelist,
            int whitelistCleanupInactiveDays,
            int whitelistPaginationDelayTicks,
            String cmdPromptChar,
            boolean optimizeEnabled,
            boolean optimizeOnShutdown,
            long optimizeTickTimeThreshold,
            int backupRetentionCount,
            String backupMaintenanceMotd,
            List<String> allowCountryCode,
            Map<String, CommandPolicy> commandPolicies) {

        public static MainConfig from(FileConfiguration cfg) {
            boolean forceWhitelist = cfg.getBoolean("force_whitelist", true);
            int whitelistCleanupInactiveDays = cfg.getInt("whitelist_cleanup_inactive_days", 90);
            int whitelistPaginationDelayTicks = cfg.getInt("whitelist_pagination_delay_ticks", 5);
            String cmdPromptChar = cfg.getString("cmd_prompt_char", "$");
            boolean optimizeEnabled = cfg.getBoolean("optimize_enabled", false);
            boolean optimizeOnShutdown = cfg.getBoolean("optimize_on_shutdown", false);
            long optimizeTickTimeThreshold = cfg.getLong("optimize_tick_time_threshold", 300L);
            int backupRetentionCount = cfg.getInt("backup_retention_count", 5);
            String backupMaintenanceMotd = cfg.getString("backup_maintenance_motd", "服务器维护中，稍后再试");
            List<String> allowCodes = new ArrayList<>();
            Object raw = cfg.get("allow_country_code");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) allowCodes.add(String.valueOf(o));
                }
            }
            Map<String, CommandPolicy> policies = new HashMap<>();
            Object rawCmds = cfg.get("commands");
            if (rawCmds instanceof ConfigurationSection section) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(key);
                    if (s != null) {
                        int cooldown = s.getInt("cooldown_secs", 0);
                        boolean adminOnly = s.getBoolean("admin_only", false);
                        policies.put(key, new CommandPolicy(cooldown, adminOnly));
                    }
                }
            }
            return new MainConfig(
                    forceWhitelist,
                    whitelistCleanupInactiveDays,
                    whitelistPaginationDelayTicks,
                    cmdPromptChar,
                    optimizeEnabled,
                    optimizeOnShutdown,
                    optimizeTickTimeThreshold,
                    backupRetentionCount,
                    backupMaintenanceMotd,
                    allowCodes,
                    policies);
        }
    }

    public record TntConfig(
            boolean enable,
            boolean enableRespawnAnchor,
            int placeCooldownSeconds,
            long notifyThrottleMs,
            List<Map<String, Object>> whitelistRegions,
            List<String> exemptEntities) {

        @SuppressWarnings("unchecked")
        public static TntConfig from(FileConfiguration cfg) {
            boolean enable = cfg.getBoolean("enable", false);
            boolean enableRespawnAnchor = cfg.getBoolean("enable_respawn_anchor", false);
            int placeCooldownSeconds = cfg.getInt("place_cooldown", 5);
            long notifyThrottleMs = cfg.getLong("notify_throttle_ms", 1000L);
            List<Map<String, Object>> whitelistRegions = new ArrayList<>();
            Object rawRegions = cfg.get("whitelist");
            if (rawRegions instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        whitelistRegions.add((Map<String, Object>) m);
                    }
                }
            }
            List<String> exemptEntities = new ArrayList<>();
            Object rawExempt = cfg.get("exempt_entities");
            if (rawExempt instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) exemptEntities.add(String.valueOf(o));
                }
            }
            return new TntConfig(
                    enable,
                    enableRespawnAnchor,
                    placeCooldownSeconds,
                    notifyThrottleMs,
                    whitelistRegions,
                    exemptEntities);
        }
    }

    public record CommandPolicy(int cooldownSeconds, boolean adminOnly) {}

    public record CommandPolicies(Map<String, CommandPolicy> policies) {

        public static CommandPolicies from(FileConfiguration cfg) {
            Map<String, CommandPolicy> policies = new HashMap<>();
            Object rawCmds = cfg.get("commands");
            if (rawCmds instanceof ConfigurationSection section) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(key);
                    if (s != null) {
                        int cooldown = s.getInt("cooldown_secs", 0);
                        boolean adminOnly = s.getBoolean("admin_only", false);
                        policies.put(key, new CommandPolicy(cooldown, adminOnly));
                    }
                }
            }
            return new CommandPolicies(policies);
        }
    }

    public record Styles(Map<String, String> colors) {

        public static Styles from(FileConfiguration cfg) {
            Map<String, String> colors = new HashMap<>();
            String base = "styles.colors";
            Map<String, String> defaults = Map.of(
                    "success", "#00FF00",
                    "info", "#55AAFF",
                    "warn", "#FFAA00",
                    "error", "#FF5555",
                    "coord", "#55FF55",
                    "player", "#FF5555",
                    "unknown", "#AAAAAA",
                    "tnt_alert", "#FF5555",
                    "explosion_alert", "#FFAA00");
            defaults.forEach((k, v) -> colors.put(k, cfg.getString(base + "." + k, v)));
            return new Styles(colors);
        }
    }

    public record Portals(Map<String, PortalEntry> entries) {
        public record PortalEntry(String target, String axis) {}

        public static Portals from(FileConfiguration cfg) {
            Map<String, PortalEntry> entries = new HashMap<>();
            Object raw = cfg.get("portals");
            if (raw instanceof ConfigurationSection sec) {
                for (String targetKey : sec.getKeys(false)) {
                    ConfigurationSection centers = sec.getConfigurationSection(targetKey);
                    if (centers == null) continue;
                    String target = SafeKeys.decodeTargetKey(targetKey);
                    for (String center : centers.getKeys(false)) {
                        String axis = centers.getString(center, "X");
                        entries.put(center, new PortalEntry(target, axis));
                    }
                }
            }
            return new Portals(entries);
        }
    }

    public record IpWhitelist(List<String> allowCountryCode) {

        public static IpWhitelist from(FileConfiguration cfg) {
            List<String> list = new ArrayList<>();
            Object raw = cfg.get("allow_country_code");
            if (raw instanceof List<?> l) {
                for (Object o : l) {
                    if (o != null) list.add(String.valueOf(o));
                }
            }
            return new IpWhitelist(list);
        }
    }

    public record NotifyPolicy(boolean privateEnabled, boolean privateAdminOnly, boolean publicEnabled) {}

    public record Notifications(Map<String, NotifyPolicy> policies) {

        public static Notifications from(FileConfiguration cfg) {
            Map<String, NotifyPolicy> policies = new HashMap<>();
            Object raw = cfg.get("notifications");
            if (raw instanceof ConfigurationSection section) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(key);
                    if (s != null) {
                        boolean privateEnabled = s.getBoolean("private.enabled", false);
                        boolean privateAdminOnly = s.getBoolean("private.admin_only", true);
                        boolean publicEnabled = s.getBoolean("public.enabled", true);
                        policies.put(key, new NotifyPolicy(privateEnabled, privateAdminOnly, publicEnabled));
                    }
                }
            }
            return new Notifications(policies);
        }
    }

    public record Templates(
            String playerJoin,
            String playerQuit,
            String playerKick,
            String exceptionAlert,
            String geoipBlock,
            String tntAlert,
            String maintenanceBackupStage,
            String maintenanceBackupDone,
            String maintenanceBackupError,
            String maintenanceOptimizeStage,
            String maintenanceOptimizeDone,
            String maintenanceOptimizeError,
            String serverMaintenanceHint) {

        public static Templates from(FileConfiguration cfg) {
            String base = "templates";
            String join = cfg.getString(
                    base + ".player_join",
                    "{name} 上线\n世界:{world_alias} 坐标:{x_unit},{y_unit},{z_unit}({coord_unit})\n角色:{role_alias}\n------当前在线({online_count}/{max_count})------\n{online_list}");
            String quit = cfg.getString(
                    base + ".player_quit",
                    "{name} 下线\n世界:{world_alias} 坐标:{x_unit},{y_unit},{z_unit}({coord_unit})\n角色:{role_alias}\n------当前在线({online_count}/{max_count})------\n{online_list}");
            String kick = cfg.getString(
                    base + ".player_kick",
                    "{name} 被踢\n世界:{world_alias} 坐标:{x_unit},{y_unit},{z_unit}({coord_unit})\n角色:{role_alias}\n------当前在线({online_count}/{max_count})------\n{online_list}");
            String exceptionAlert = cfg.getString(base + ".exception_alert", "异常: {message}\n摘要: {stack_summary}");
            String geoipBlock = cfg.getString(
                    base + ".geoip_block", "{name}({ip}) 地区:{country_code} 不在允许列表({allow_list})\n{address_info}");
            String tntAlert = cfg.getString(
                    base + ".tnt_alert",
                    "{msg}\n世界:{world_alias} 坐标:{x_unit},{y_unit},{z_unit}({coord_unit})\n触发:{actor} 方块:{block_type}");
            String mbStage = cfg.getString(
                    base + ".maintenance_backup_stage",
                    "地图{label} 阶段:{stage}({stage_name}/{stage_i18n}) 进度:{percent}% {current}/{total} 速率:{rate_per}{rate_unit} 预计剩余:{eta_value}{eta_unit}");
            String mbDone = cfg.getString(base + ".maintenance_backup_done", "地图{label} 完成 用时:{duration_ms}ms");
            String mbErr = cfg.getString(base + ".maintenance_backup_error", "地图{label} 失败 用时:{duration_ms}ms");
            String moStage = cfg.getString(
                    base + ".maintenance_optimize_stage",
                    "地图{label} 阶段:{stage}({stage_name}/{stage_i18n}) 进度:{percent}% {current}/{total} 速率:{rate_per}{rate_unit} 预计剩余:{eta_value}{eta_unit}");
            String moDone = cfg.getString(base + ".maintenance_optimize_done", "地图{label} 完成 用时:{duration_ms}ms");
            String moErr = cfg.getString(base + ".maintenance_optimize_error", "地图{label} 失败 用时:{duration_ms}ms");
            String maintHint = cfg.getString(base + ".server_maintenance_hint", "服务器当前无玩家，可进行服务器维护");
            return new Templates(
                    join,
                    quit,
                    kick,
                    exceptionAlert,
                    geoipBlock,
                    tntAlert,
                    mbStage,
                    mbDone,
                    mbErr,
                    moStage,
                    moDone,
                    moErr,
                    maintHint);
        }
    }

    public record TemplateOptions(
            Map<String, String> stageCnMap,
            String rateUnit,
            String etaUnit,
            Map<String, String> worldAlias,
            Map<String, Map<String, String>> worldAliasLocalized,
            double coordScale,
            int coordPrecision,
            String coordUnitLabel,
            Map<String, String> roleAlias,
            String locale,
            Map<String, Map<String, String>> roleAliasLocalized,
            Map<String, String> roleGroupAliases,
            Map<String, Map<String, String>> stageAliasLocalized) {

        public TemplateOptions(
                Map<String, String> stageCnMap,
                String rateUnit,
                String etaUnit,
                Map<String, String> worldAlias,
                Map<String, Map<String, String>> worldAliasLocalized,
                double coordScale,
                String coordUnitLabel,
                Map<String, String> roleAlias,
                String locale,
                Map<String, Map<String, String>> roleAliasLocalized,
                Map<String, String> roleGroupAliases,
                Map<String, Map<String, String>> stageAliasLocalized) {
            this(
                    stageCnMap,
                    rateUnit,
                    etaUnit,
                    worldAlias,
                    worldAliasLocalized,
                    coordScale,
                    2,
                    coordUnitLabel,
                    roleAlias,
                    locale,
                    roleAliasLocalized,
                    roleGroupAliases,
                    stageAliasLocalized);
        }

        public static TemplateOptions from(FileConfiguration cfg) {
            Map<String, String> m = new HashMap<>();
            Object raw = cfg.get("templates.stage_cn");
            if (raw instanceof ConfigurationSection sec) {
                for (String k : sec.getKeys(false)) {
                    String v = sec.getString(k);
                    if (v != null) m.put(k, v);
                }
            }
            String rate = cfg.getString("templates.progress_units.rate", "per_sec");
            String eta = cfg.getString("templates.progress_units.eta", "ms");
            Map<String, String> worldAlias = new HashMap<>();
            Object wa = cfg.get("templates.world_alias");
            if (wa instanceof ConfigurationSection sec2) {
                for (String k : sec2.getKeys(false)) {
                    String v = sec2.getString(k);
                    if (v != null) worldAlias.put(k, v);
                }
            }
            Map<String, Map<String, String>> worldAliasLocalized = new HashMap<>();
            Object wal = cfg.get("templates.i18n.world_alias");
            if (wal instanceof ConfigurationSection secw) {
                for (String lang : secw.getKeys(false)) {
                    ConfigurationSection langSec = secw.getConfigurationSection(lang);
                    if (langSec != null) {
                        Map<String, String> map = new HashMap<>();
                        for (String k : langSec.getKeys(false)) {
                            String v = langSec.getString(k);
                            if (v != null) map.put(k, v);
                        }
                        worldAliasLocalized.put(lang, map);
                    }
                }
            }
            worldAlias.putIfAbsent("world", "主世界");
            worldAlias.putIfAbsent("world_nether", "下界");
            worldAlias.putIfAbsent("world_the_end", "末地");
            Map<String, String> roleAlias = new HashMap<>();
            Object ra = cfg.get("templates.role_alias");
            if (ra instanceof ConfigurationSection sec3) {
                for (String k : sec3.getKeys(false)) {
                    String v = sec3.getString(k);
                    if (v != null) roleAlias.put(k, v);
                }
            }
            roleAlias.putIfAbsent("admin", "管理员");
            roleAlias.putIfAbsent("member", "玩家");
            String locale = cfg.getString("templates.locale", "zh-CN");
            Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
            Object ral = cfg.get("templates.i18n.role_alias");
            if (ral instanceof ConfigurationSection sec4) {
                for (String lang : sec4.getKeys(false)) {
                    ConfigurationSection langSec = sec4.getConfigurationSection(lang);
                    if (langSec != null) {
                        Map<String, String> map = new HashMap<>();
                        for (String k : langSec.getKeys(false)) {
                            String v = langSec.getString(k);
                            if (v != null) map.put(k, v);
                        }
                        roleAliasLocalized.put(lang, map);
                    }
                }
            }
            Map<String, Map<String, String>> stageAliasLocalized = new HashMap<>();
            Object sal = cfg.get("templates.i18n.stage_alias");
            if (sal instanceof ConfigurationSection secs) {
                for (String lang : secs.getKeys(false)) {
                    ConfigurationSection langSec = secs.getConfigurationSection(lang);
                    if (langSec != null) {
                        Map<String, String> map = new HashMap<>();
                        for (String k : langSec.getKeys(false)) {
                            String v = langSec.getString(k);
                            if (v != null) map.put(k, v);
                        }
                        stageAliasLocalized.put(lang, map);
                    }
                }
            }
            Map<String, String> roleGroups = new HashMap<>();
            Object rg = cfg.get("templates.role_groups");
            if (rg instanceof ConfigurationSection sec5) {
                for (String k : sec5.getKeys(false)) {
                    String v = sec5.getString(k);
                    if (v != null) roleGroups.put(k, v);
                }
            }
            roleGroups.putIfAbsent("orzmc.admin", "管理员");
            roleGroups.putIfAbsent("default", "玩家");
            double coordScale = cfg.getDouble("templates.coord.scale", 1.0);
            int coordPrecision = cfg.getInt("templates.coord.precision", 2);
            String coordUnitLabel = cfg.getString("templates.coord.unit_label", "block");
            return new TemplateOptions(
                    m,
                    rate,
                    eta,
                    worldAlias,
                    worldAliasLocalized,
                    coordScale,
                    coordPrecision,
                    coordUnitLabel,
                    roleAlias,
                    locale,
                    roleAliasLocalized,
                    roleGroups,
                    stageAliasLocalized);
        }
    }
}
