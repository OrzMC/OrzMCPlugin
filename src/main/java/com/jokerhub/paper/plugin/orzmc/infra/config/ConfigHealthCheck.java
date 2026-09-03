package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplatePlaceholderValidator;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigHealthCheck {
    private ConfigHealthCheck() {}

    public static List<String> validateAll(AdvancedConfigManager mgr) {
        return validateAll(mgr::getConfig);
    }

    public static List<String> validateAll(Function<String, FileConfiguration> provider) {
        List<String> issues = new ArrayList<>();
        validateConfig(provider.apply("config"), provider, issues);
        validateEasyBot(provider.apply("easybot"), issues);
        validateTemplates(provider.apply("templates"), issues);
        validatePortals(provider.apply("portals"), issues);
        validateAccessRules(provider.apply("access_rules"), issues);
        return issues;
    }

    private static void validateConfig(
            FileConfiguration cfg, Function<String, FileConfiguration> provider, List<String> issues) {
        if (cfg == null) {
            issues.add("config.yml 未加载");
            return;
        }
        validateWhitelistSection(cfg.getConfigurationSection("whitelist"), issues);
        validateMaintenanceSection(cfg.getConfigurationSection("maintenance"), issues);
        validateTntSection(cfg.getConfigurationSection("tnt"), issues);
        validatePlayerNotifySection(cfg.getConfigurationSection("player_notify"), issues);
        validateGeoIpSection(cfg.getConfigurationSection("geoip"), issues);
        validateCommandPoliciesSection(cfg.getConfigurationSection("command_policies"), issues);
        validateGuardSection(cfg.getConfigurationSection("guard"), issues);
        validateChatSection(cfg.getConfigurationSection("chat"), issues);
        validateLoginRateLimitSection(cfg.getConfigurationSection("login_rate_limit"), issues);
        validateExploitHardeningSection(cfg.getConfigurationSection("exploit_hardening"), issues);
        validateRankColorsSection(cfg.getConfigurationSection("rank_colors"), issues);
    }

    private static void validateRankColorsSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("建议: config.yml 缺失 rank_colors 配置段，将使用默认配置（Tab 着色默认关闭）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: rank_colors.enabled 需为布尔值");
        Object nt = section.get("nametag_enabled");
        if (nt != null && !(nt instanceof Boolean)) issues.add("类型错误: rank_colors.nametag_enabled 需为布尔值");
        Object tab = section.get("tab_enabled");
        if (tab != null && !(tab instanceof Boolean)) issues.add("类型错误: rank_colors.tab_enabled 需为布尔值");
        String opColor = section.getString("op_color", "");
        if (!opColor.isBlank() && !isValidRankColor(opColor)) {
            issues.add("非法: rank_colors.op_color '" + opColor + "' 不是合法命名色或 #RRGGBB");
        }
        ConfigurationSection colorsSection = section.getConfigurationSection("colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                String raw = colorsSection.getString(key, "");
                if (!raw.isBlank() && !isValidRankColor(raw)) {
                    issues.add("非法: rank_colors.colors." + key + " '" + raw + "' 不是合法命名色或 #RRGGBB");
                }
            }
        }
    }

    /** 与 {@code RankColorsConfig.parseColor} 同一接受范围：命名色或 CSS hex（含 #RRGGBB）。 */
    private static boolean isValidRankColor(String raw) {
        String trimmed = raw.trim();
        return NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT)) != null
                || TextColor.fromCSSHexString(trimmed) != null;
    }

    private static void validateWhitelistSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 whitelist 配置段");
            return;
        }
        Object fw = section.get("force_whitelist");
        if (!(fw instanceof Boolean)) issues.add("类型错误: whitelist.force_whitelist 需为布尔值");
        int days = section.getInt("cleanup_inactive_days", 90);
        if (days <= 0) issues.add("非法: whitelist.cleanup_inactive_days 必须为正数");
        int ticks = section.getInt("pagination_delay_ticks", 5);
        if (ticks < 0) issues.add("非法: whitelist.pagination_delay_ticks 不得为负数");
        ConfigurationSection kickSection = section.getConfigurationSection("kick_message");
        if (kickSection == null) {
            issues.add("缺失: whitelist.kick_message 未配置");
        } else {
            String title = kickSection.getString("title", "");
            if (title.isEmpty()) issues.add("缺失: whitelist.kick_message.title 不可为空");
            List<?> ups = kickSection.getList("ups");
            if (ups == null || ups.isEmpty()) issues.add("缺失: whitelist.kick_message.ups 至少需要一项");
        }
    }

    private static void validateMaintenanceSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 maintenance 配置段");
            return;
        }
        Object en = section.get("optimize_enabled");
        if (!(en instanceof Boolean)) issues.add("类型错误: maintenance.optimize_enabled 需为布尔值");
        int thr = section.getInt("optimize_tick_time_threshold", 300);
        if (thr <= 0) issues.add("非法: maintenance.optimize_tick_time_threshold 必须为正数");
        int retain = section.getInt("backup_retention_count", 5);
        if (retain < 0) issues.add("非法: maintenance.backup_retention_count 不得为负数");
        // 维护场景文案/进度行已迁 templates.yml（maintenance_motd_*，Templates record 有默认兜底），
        // config.yml maintenance 段不再含 motd 键，故此处不再校验（2026-09-02 PR4）
    }

    private static void validateTntSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 tnt 配置段");
            return;
        }
        Object enable = section.get("enable");
        if (enable != null && !(enable instanceof Boolean)) issues.add("类型错误: tnt.enable 需为布尔值");
        Object enableAnchor = section.get("enable_respawn_anchor");
        if (enableAnchor != null && !(enableAnchor instanceof Boolean))
            issues.add("类型错误: tnt.enable_respawn_anchor 需为布尔值");
        int cd = section.getInt("place_cooldown", 0);
        if (cd < 0) issues.add("非法: tnt.place_cooldown 不得为负数");
        long agg = section.getLong("notify_aggregate_ms", 3000L);
        if (agg <= 0) issues.add("非法: tnt.notify_aggregate_ms 必须为正数（≤0 会回退默认 3000ms，静默关闭防刷屏）");
        Object wl = section.get("whitelist");
        if (wl != null && !(wl instanceof List<?>)) issues.add("类型错误: tnt.whitelist 需为列表");
        Object exempt = section.get("exempt_entities");
        if (exempt != null && !(exempt instanceof List<?>)) issues.add("类型错误: tnt.exempt_entities 需为列表");
    }

    private static void validatePlayerNotifySection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，仅升级安装（config.yml 存在故未复制新默认值）会缺此段，
            // 属提示而非缺陷，避免升级后每次启动的持久告警
            issues.add("建议: config.yml 缺失 player_notify 配置段，将使用默认配置（窗口 1000ms，三类通知启用）");
            return;
        }
        for (String key : new String[] {"enabled_join", "enabled_quit", "enabled_kick"}) {
            Object en = section.get(key);
            if (en != null && !(en instanceof Boolean)) issues.add("类型错误: player_notify." + key + " 需为布尔值");
        }
        long window = section.getLong("window_ms", 1000L);
        if (window <= 0) issues.add("非法: player_notify.window_ms 必须为正数（≤0 会回退默认 1000ms，静默关闭防刷屏）");
        int maxList = section.getInt("max_list_items", 6);
        if (maxList < 1) issues.add("非法: player_notify.max_list_items 不得小于 1");
    }

    private static void validateGeoIpSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 geoip 配置段");
            return;
        }
        Object raw = section.get("allow_country_code");
        if (raw == null) {
            issues.add("建议: geoip.allow_country_code 未配置，默认允许所有地区");
        } else if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    issues.add("非法: geoip.allow_country_code 不允许空项");
                } else {
                    String code = String.valueOf(o);
                    if (!code.matches("^[A-Z]{2}$")) {
                        issues.add("非法: geoip.allow_country_code '" + code + "' 必须为大写两位国家码");
                    }
                }
            }
        } else {
            issues.add("类型错误: geoip.allow_country_code 需为列表");
        }
    }

    private static void validateAccessRules(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("access_rules.yml 未加载");
            return;
        }
        Object ip = cfg.get("ip_blacklist");
        if (ip != null && !(ip instanceof List<?>)) {
            issues.add("类型错误: access_rules.ip_blacklist 需为列表");
        }
        Object raw = cfg.get("player_name_rules");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> list)) {
            issues.add("类型错误: access_rules.player_name_rules 需为列表");
            return;
        }
        List<String> validTypes = List.of("exact", "prefix", "suffix", "contains", "glob", "regex");
        for (Object item : list) {
            String type = null;
            String value = null;
            if (item instanceof java.util.Map<?, ?> map) {
                Object rawType = map.get("type");
                Object rawValue = map.get("value");
                type = rawType == null ? null : String.valueOf(rawType);
                value = rawValue == null ? null : String.valueOf(rawValue);
            } else if (item instanceof ConfigurationSection section) {
                type = section.getString("type");
                value = section.getString("value");
            } else if (item instanceof String text) {
                int colon = text.indexOf(':');
                if (colon > 0) {
                    type = text.substring(0, colon);
                    value = text.substring(colon + 1);
                }
            }
            if (type == null || value == null || value.isBlank()) {
                issues.add("非法: access_rules.player_name_rules 条目缺少 type/value");
                continue;
            }
            // trim 后校验：运行时 MatchType.from() 也是 trim 后解析，口径一致避免「运行时生效、
            // 校验误报非法」；纯空白值（isBlank）运行时会被丢弃，此处同样视为缺值
            String normalizedType = type.trim().toLowerCase(Locale.ROOT);
            if (!validTypes.contains(normalizedType)) {
                issues.add("非法: access_rules.player_name_rules.type '" + type + "' 不在支持范围");
                continue;
            }
            if ("regex".equals(normalizedType)) {
                try {
                    Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    issues.add("非法: access_rules.player_name_rules 正则无法编译: " + value);
                }
            }
        }
    }

    private static void validateCommandPoliciesSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 command_policies 配置段");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                issues.add("非法: command_policies." + key + " 需为对象");
                continue;
            }
            Object cd = s.get("cooldown_secs");
            if (cd != null) {
                try {
                    int val = Integer.parseInt(String.valueOf(cd));
                    if (val < 0) issues.add("非法: command_policies." + key + ".cooldown_secs 不得为负数");
                } catch (Exception e) {
                    issues.add("类型错误: command_policies." + key + ".cooldown_secs 需为数字");
                }
            }
            Object ao = s.get("admin_only");
            if (ao != null && !(ao instanceof Boolean))
                issues.add("类型错误: command_policies." + key + ".admin_only 需为布尔值");
        }
    }

    private static void validateGuardSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("建议: config.yml 缺失 guard 配置段，将使用默认配置（危险命令拦截开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: guard.enabled 需为布尔值");
        Object na = section.get("notify_admins");
        if (na != null && !(na instanceof Boolean)) issues.add("类型错误: guard.notify_admins 需为布尔值");
        Object ae = section.get("audit_enabled");
        if (ae != null && !(ae instanceof Boolean)) issues.add("类型错误: guard.audit_enabled 需为布尔值");
        Object bl = section.get("blocked_commands");
        if (bl != null && !(bl instanceof List<?>)) issues.add("类型错误: guard.blocked_commands 需为列表");
    }

    private static void validateChatSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，升级安装（config.yml 存在故未复制新默认值）会缺此段，
            // 属提示而非缺陷，避免升级后每次启动的持久告警
            issues.add("建议: config.yml 缺失 chat 配置段，将使用默认配置（聊天反垃圾开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: chat.enabled 需为布尔值");
        int max = section.getInt("max_messages_per_minute", 20);
        if (max < 1) issues.add("非法: chat.max_messages_per_minute 不得小于 1");
        Object dl = section.get("detect_links");
        if (dl != null && !(dl instanceof Boolean)) issues.add("类型错误: chat.detect_links 需为布尔值");
        Object dr = section.get("detect_repeat");
        if (dr != null && !(dr instanceof Boolean)) issues.add("类型错误: chat.detect_repeat 需为布尔值");
        String msg = section.getString("message", "");
        if (msg.isBlank()) issues.add("缺失: chat.message 不可为空");
    }

    private static void validateLoginRateLimitSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，升级安装（config.yml 存在故未复制新默认值）会缺此段
            issues.add("建议: config.yml 缺失 login_rate_limit 配置段，将使用默认配置（进服限流开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: login_rate_limit.enabled 需为布尔值");
        int freq = section.getInt("max_login_attempts_per_minute", 20);
        if (freq < 1) issues.add("非法: login_rate_limit.max_login_attempts_per_minute 不得小于 1");
        int conc = section.getInt("max_concurrent_per_ip", 5);
        if (conc < 1) issues.add("非法: login_rate_limit.max_concurrent_per_ip 不得小于 1");
        Object na = section.get("notify_admins");
        if (na != null && !(na instanceof Boolean)) issues.add("类型错误: login_rate_limit.notify_admins 需为布尔值");
        String msg = section.getString("message", "");
        if (msg.isBlank()) issues.add("缺失: login_rate_limit.message 不可为空");
    }

    private static void validateExploitHardeningSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，升级安装（config.yml 存在故未复制新默认值）会缺此段
            issues.add("建议: config.yml 缺失 exploit_hardening 配置段，将使用默认配置（漏洞加固开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: exploit_hardening.enabled 需为布尔值");
        for (String key : List.of("book_enabled", "item_enabled", "entity_enabled")) {
            Object flag = section.get(key);
            if (flag != null && !(flag instanceof Boolean)) {
                issues.add("类型错误: exploit_hardening." + key + " 需为布尔值");
            }
        }
        Object na = section.get("notify_admins");
        if (na != null && !(na instanceof Boolean)) issues.add("类型错误: exploit_hardening.notify_admins 需为布尔值");
        int pages = section.getInt("book_max_pages", 100);
        if (pages < 1) issues.add("非法: exploit_hardening.book_max_pages 不得小于 1");
        int attrs = section.getInt("item_max_attribute_modifiers", 6);
        if (attrs < 1) issues.add("非法: exploit_hardening.item_max_attribute_modifiers 不得小于 1");
        int entities = section.getInt("entity_max_per_chunk", 128);
        if (entities < 1) issues.add("非法: exploit_hardening.entity_max_per_chunk 不得小于 1");
        String msg = section.getString("message", "");
        if (msg.isBlank()) issues.add("缺失: exploit_hardening.message 不可为空");
    }

    /** 校验 EasyBot 网关 scheme：非本机地址用明文 http/ws 时提示改用加密协议。 */
    private static void validateScheme(String url, String path, List<String> issues) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("ws"))) {
                return; // 非明文协议（https/wss）或无法判定，跳过
            }
            String host = uri.getHost();
            if (host == null
                    || "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || host.startsWith("127.")) {
                return; // 本地网关明文可接受
            }
            issues.add("建议: " + path + " 使用明文 " + scheme + " 且非本机地址，apiKey 将明文传输，建议改用加密协议");
        } catch (IllegalArgumentException e) {
            // 非法 URL 由其它校验负责，此处跳过
        }
    }

    private static void validateEasyBot(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("easybot.yml 未加载");
            return;
        }
        Object prompt = cfg.get("cmd_prompt_char");
        if (prompt != null && !(prompt instanceof String)) {
            issues.add("类型错误: easybot.cmd_prompt_char 需为字符串");
        } else if (prompt != null && String.valueOf(prompt).isBlank()) {
            issues.add("非法: easybot.cmd_prompt_char 不可为空");
        }

        // apiKey 传输安全：远程网关（非本机）用明文 http/ws 时 apiKey 会明文传输
        validateScheme(cfg.getString("api_server", ""), "easybot.api_server", issues);
        validateScheme(cfg.getString("ws_server", ""), "easybot.ws_server", issues);

        // 检测是否有至少一个平台启用了 enabled: true
        boolean anyPlatformEnabled = false;
        ConfigurationSection platformsSec = cfg.getConfigurationSection("platforms");
        if (platformsSec != null) {
            for (String key : platformsSec.getKeys(false)) {
                ConfigurationSection sec = platformsSec.getConfigurationSection(key);
                if (sec == null) {
                    issues.add("非法: easybot.platforms." + key + " 需为对象");
                    continue;
                }
                Object enabledField = sec.get("enabled");
                if (enabledField != null && !(enabledField instanceof Boolean)) {
                    issues.add("类型错误: easybot.platforms." + key + ".enabled 需为布尔值");
                }
                if (enabledField instanceof Boolean && (Boolean) enabledField) {
                    anyPlatformEnabled = true;
                    String platform = key.trim().toLowerCase(Locale.ROOT);
                    String adminGroup = sec.getString("admin_group", "").trim();
                    String playerGroup = sec.getString("player_group", "").trim();
                    String adminDm = sec.getString("admin_dm", "").trim();
                    if (adminGroup.isEmpty() && playerGroup.isEmpty()) {
                        issues.add("建议: easybot.platforms." + key + " 至少配置 admin_group 或 player_group");
                    }
                    if (adminDm.isEmpty()) {
                        issues.add("建议: easybot.platforms." + key + ".admin_dm 未配置，PRIVATE 告警将无法发送");
                    }
                    validateAdminGroup(adminGroup, platform, "easybot.platforms." + key, issues);
                }
                String platform = key.trim().toLowerCase(Locale.ROOT);
                validateTarget(sec, "player_group", platform, "easybot.platforms." + key, issues);
                validateTarget(sec, "admin_dm", platform, "easybot.platforms." + key, issues);
            }
        }

        String apiServer = cfg.getString("api_server", "");
        String wsServer = cfg.getString("ws_server", "");
        if (anyPlatformEnabled) {
            if (apiServer.isEmpty()) {
                issues.add("缺失: easybot.api_server 有平台启用时必须配置");
            }
            if (wsServer.isEmpty()) {
                issues.add("缺失: easybot.ws_server 有平台启用时必须配置");
            }
            String apiKey = cfg.getString("api_key", "").trim();
            if (apiKey.isBlank()) {
                issues.add("缺失: easybot.api_key 有平台启用时必须配置");
            }
        }
        validateUri(apiServer, "http", "https", "easybot.api_server", issues);
        validateUri(wsServer, "ws", "wss", "easybot.ws_server", issues);
        // Validate HTTP timeouts
        int httpConn = cfg.getInt("http_connect_timeout_seconds", 3);
        int httpReq = cfg.getInt("http_request_timeout_seconds", 3);
        int httpRetries = cfg.getInt("http_max_retries", 3);
        if (httpConn <= 0) issues.add("非法: easybot.http_connect_timeout_seconds 必须为正数");
        if (httpReq <= 0) issues.add("非法: easybot.http_request_timeout_seconds 必须为正数");
        if (httpRetries < 0) issues.add("非法: easybot.http_max_retries 不得为负数");
        int wsRetries = cfg.getInt("ws_max_retries", 10);
        long wsBaseRetry = cfg.getLong("ws_base_retry_ms", 5000);
        long wsMaxDelay = cfg.getLong("ws_max_delay_ms", 60000);
        int wsJitter = cfg.getInt("ws_jitter_percent", 10);
        long wsStableReset = cfg.getLong("ws_stable_reset_ms", 20000);
        long wsLogThrottle = cfg.getLong("ws_message_log_throttle_ms", 60000);
        long logThrottle = cfg.getLong("log_throttle_ms", 5000);
        if (wsRetries < 0) issues.add("非法: easybot.ws_max_retries 不得为负数");
        if (wsBaseRetry <= 0) issues.add("非法: easybot.ws_base_retry_ms 必须为正数");
        if (wsMaxDelay < wsBaseRetry) issues.add("非法: easybot.ws_max_delay_ms 不得小于 ws_base_retry_ms");
        if (wsJitter < 0 || wsJitter > 100) issues.add("非法: easybot.ws_jitter_percent 范围 0-100");
        if (wsStableReset <= 0) issues.add("非法: easybot.ws_stable_reset_ms 必须为正数");
        if (wsLogThrottle <= 0) issues.add("非法: easybot.ws_message_log_throttle_ms 必须为正数");
        if (logThrottle <= 0) issues.add("非法: easybot.log_throttle_ms 必须为正数");
    }

    private static void validateTarget(
            ConfigurationSection section, String field, String platform, String path, List<String> issues) {
        String target = section.getString(field, "").trim();
        validateTargetValue(target, field, platform, path, issues);
    }

    private static void validateTargetValue(
            String target, String field, String platform, String path, List<String> issues) {
        if (target.isEmpty()) return;
        if (!target.startsWith(platform + ":") || target.length() == platform.length() + 1) {
            issues.add("格式: " + path + "." + field + " 需为 '" + platform + ":chatId' 格式");
        }
    }

    private static void validateAdminGroup(String target, String platform, String path, List<String> issues) {
        if (target.isEmpty()) return;
        if (!target.contains(":")) {
            issues.add("格式: " + path + ".admin_group 需为 'platform:chatId' 格式");
        } else if (!target.startsWith(platform + ":")) {
            issues.add("格式: " + path + ".admin_group 平台前缀应为 '" + platform + ":'");
        } else if (target.length() == platform.length() + 1) {
            issues.add("格式: " + path + ".admin_group 需为 '" + platform + ":chatId' 格式");
        }
    }

    private static void validateUri(String value, String scheme1, String scheme2, String path, List<String> issues) {
        if (value == null || value.isEmpty()) return;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !(scheme1.equalsIgnoreCase(scheme) || scheme2.equalsIgnoreCase(scheme))) {
                issues.add("格式: " + path + " 需为有效的 " + scheme1 + "/" + scheme2 + " URL");
            }
        } catch (IllegalArgumentException e) {
            issues.add("格式: " + path + " 需为有效的 " + scheme1 + "/" + scheme2 + " URL");
        }
    }

    private static void validatePortals(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("portals.yml 未加载");
            return;
        }
        Object raw = cfg.get("portals");
        if (raw instanceof ConfigurationSection sec) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) {
                    issues.add("非法: portals." + k + " 节点为空");
                    continue;
                }
                String target = SafeKeys.decodeTargetKey(k);
                for (String center : s.getKeys(false)) {
                    String[] parts = center.split(":");
                    if (parts.length != 4) {
                        issues.add("非法: portals." + target + " 下键需为 world:cx:cy:cz");
                        continue;
                    }
                    try {
                        Integer.parseInt(parts[1]);
                        Integer.parseInt(parts[2]);
                        Integer.parseInt(parts[3]);
                    } catch (Exception e) {
                        issues.add("非法: portals 坐标需为整数");
                    }
                    String axis = s.getString(center, "X");
                    if (!(axis.equalsIgnoreCase("X") || axis.equalsIgnoreCase("Z"))) {
                        issues.add("非法: portals 轴向取值 X/Z");
                    }
                }
                if (target.contains(":")) {
                    String[] hp = target.split(":");
                    if (hp.length == 2) {
                        try {
                            int port = Integer.parseInt(hp[1]);
                            if (port <= 0 || port > 65535) {
                                issues.add("非法: portals 端口范围 1-65535");
                            }
                        } catch (Exception e) {
                            issues.add("类型错误: portals 端口需为数字");
                        }
                    }
                }
            }
        }
    }

    private static void validateTemplates(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("templates.yml 未加载");
            return;
        }
        issues.addAll(TemplatePlaceholderValidator.validate(cfg));

        // Validate styles section
        ConfigurationSection stylesSection = cfg.getConfigurationSection("styles");
        if (stylesSection == null) {
            issues.add("templates.yml 缺失 styles 配置段");
        } else {
            validateStylesSection(stylesSection, issues);
        }

        // Validate templates section
        String base = "templates";
        if (!cfg.contains(base + ".player_join")) issues.add("缺失: templates.player_join");
        double scale = cfg.getDouble(base + ".coord.scale", 1.0);
        if (scale <= 0) issues.add("非法: templates.coord.scale 必须为正数");
        int precision = cfg.getInt(base + ".coord.precision", 2);
        if (precision < 0) issues.add("非法: templates.coord.precision 不得为负数");
        String unit = cfg.getString(base + ".coord.unit_label", "block");
        if (unit.isEmpty()) issues.add("缺失: templates.coord.unit_label");
        String rate = cfg.getString(base + ".progress_units.rate", "per_sec");
        if (!(rate.equalsIgnoreCase("per_sec") || rate.equalsIgnoreCase("per_min"))) {
            issues.add("非法: templates.progress_units.rate 取值 per_sec/per_min");
        }
        String eta = cfg.getString(base + ".progress_units.eta", "ms");
        if (!(eta.equalsIgnoreCase("ms") || eta.equalsIgnoreCase("sec") || eta.equalsIgnoreCase("min"))) {
            issues.add("非法: templates.progress_units.eta 取值 ms/sec/min");
        }
        if (!cfg.contains("templates.world_alias.world")) issues.add("建议: templates.world_alias.world 缺失");
        if (!cfg.contains("templates.world_alias.world_nether"))
            issues.add("建议: templates.world_alias.world_nether 缺失");
        if (!cfg.contains("templates.world_alias.world_the_end"))
            issues.add("建议: templates.world_alias.world_the_end 缺失");
        // 权限组中文名由 RankService.groupDisplayName 统一提供（唯一事实源），
        // 模板系统的 role_alias/role_groups 配置已删除，不再校验
        String[] requiredTemplates = TemplateKeys.ALL;
        for (String key : requiredTemplates) {
            if (!cfg.contains("templates." + key)) {
                issues.add("缺失: templates." + key);
            }
        }
        Object rawFmt = cfg.get("templates.format");
        if (rawFmt instanceof ConfigurationSection sec) {
            for (String key : sec.getKeys(false)) {
                String raw = sec.getString(key, "DEFAULT");
                if (raw.isEmpty()) {
                    issues.add("非法: templates.format." + key + " 不可为空");
                    continue;
                }
                String v = raw.toUpperCase();
                if (!("DEFAULT".equals(v) || "PLAIN".equals(v) || "CODE_BLOCK".equals(v))) {
                    issues.add("非法: templates.format." + key + " 值无效: " + raw);
                }
                if (!cfg.contains("templates." + key)) {
                    issues.add("建议: templates.format." + key + " 未找到对应模板");
                }
            }
        }
    }

    private static void validateStylesSection(ConfigurationSection section, List<String> issues) {
        ConfigurationSection colorsSection = section.getConfigurationSection("colors");
        if (colorsSection == null) {
            issues.add("templates.yml styles 缺失 colors 配置段");
            return;
        }
        String[] keys = {
            "success", "info", "warn", "error", "coord", "player", "unknown", "tnt_alert", "explosion_alert"
        };
        for (String k : keys) {
            Object v = colorsSection.get(k);
            if (v == null) {
                issues.add("缺失: styles.colors." + k);
            } else {
                String s = String.valueOf(v);
                if (!s.matches("^#[0-9A-Fa-f]{6}$")) {
                    issues.add("非法: styles.colors." + k + " 必须为 #RRGGBB");
                }
            }
        }
    }
}
