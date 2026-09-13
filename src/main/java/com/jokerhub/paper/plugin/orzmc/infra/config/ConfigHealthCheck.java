package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ExploitHardeningConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PlayerNotifyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookConfigParser;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplatePlaceholderValidator;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigHealthCheck {
    private ConfigHealthCheck() {}

    public static List<String> validateAll(AdvancedConfigManager mgr) {
        return validateAll(mgr::getConfig);
    }

    public static List<String> validateAll(Function<String, FileConfiguration> provider) {
        List<String> issues = new ArrayList<>();
        FileConfiguration config = provider.apply("config");
        validateConfig(config, provider, issues);
        validateEasyBot(provider.apply("easybot"), issues);
        validateIm(provider.apply("im"), issues);
        validateBotSection(config, provider.apply("easybot"), issues);
        validateTemplates(provider.apply("templates"), issues);
        validatePortals(provider.apply("portals"), issues);
        validateAccessRules(provider.apply("access_rules"), issues);
        validateGuideBook(provider.apply("guide_book"), issues);
        return issues;
    }

    private static void validateConfig(
            FileConfiguration cfg, Function<String, FileConfiguration> provider, List<String> issues) {
        if (cfg == null) {
            issues.add("config.yml 未加载");
            return;
        }
        WhitelistConfig.validate(cfg.getConfigurationSection("whitelist"), issues);
        WhitelistKickMessage.validate(cfg.getConfigurationSection("whitelist"), issues);
        MaintenanceConfig.validate(cfg.getConfigurationSection("maintenance"), issues);
        TntConfig.validate(cfg.getConfigurationSection("tnt"), issues);
        PlayerNotifyConfig.validate(cfg.getConfigurationSection("player_notify"), issues);
        IpWhitelist.validate(cfg.getConfigurationSection("geoip"), issues);
        CommandPolicies.validate(cfg.getConfigurationSection("command_policies"), issues);
        SecurityGuardConfig.validate(cfg.getConfigurationSection("guard"), issues);
        ChatConfig.validate(cfg.getConfigurationSection("chat"), issues);
        LoginRateLimitConfig.validate(cfg.getConfigurationSection("login_rate_limit"), issues);
        ExploitHardeningConfig.validate(cfg.getConfigurationSection("exploit_hardening"), issues);
        RankColorsConfig.validate(cfg.getConfigurationSection("rank_colors"), issues);
    }

    /**
     * v12 起业务层 bot 参数权威在 config.yml {@code bot:} 段（easybot.yml 旧键仅作回退读取，搬迁见
     * ConfigService.migrateBotParamsToConfig）。校验 bot 段类型；若 bot 段缺失且 easybot 仍持旧键 → 迁移提示。
     */
    private static void validateBotSection(
            FileConfiguration configCfg, FileConfiguration easybotCfg, List<String> issues) {
        if (configCfg == null) {
            return; // config.yml 未加载已由 validateConfig 报
        }
        ConfigurationSection bot = configCfg.getConfigurationSection("bot");
        if (bot != null) {
            Object prompt = bot.get("cmd_prompt_char");
            if (prompt != null && !(prompt instanceof String)) {
                issues.add("类型错误: bot.cmd_prompt_char 需为字符串");
            } else if (prompt != null && String.valueOf(prompt).isBlank()) {
                issues.add("非法: bot.cmd_prompt_char 不可为空");
            }
            return; // bot 段存在即权威（含刚被搬迁/升级补齐）
        }
        // bot 段整体缺失：检查 easybot 旧键是否仍被回退读取（老装未迁移或文件异常）→ 提示
        if (easybotCfg != null
                && (easybotCfg.contains("cmd_prompt_char")
                        || easybotCfg.contains("discord_server_link")
                        || easybotCfg.contains("qq_group_id"))) {
            issues.add("建议迁移: bot 业务参数仍位于 easybot.yml（v12 起权威位置为 config.yml bot: 段，"
                    + "当前按回退读取生效）——迁移由插件启动自动完成，或手动配置 config.yml bot: 后删除 easybot 旧键");
        }
    }

    /**
     * im.yml 校验（IM 双通道配置；此前无任何校验——backend 拼错/平台段凭据键写错会静默不可用，评审 C1）：
     * backend 取值域；builtin 模式下 platforms 段类型与已知平台段凭据键存在性；proxy 段 host/port 类型。
     */
    private static void validateIm(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            return; // im.yml 未加载（测试/旧装无 im 不阻断）
        }
        Object backend = cfg.get("backend");
        if (backend != null && !(backend instanceof String)) {
            issues.add("类型错误: im.backend 需为字符串（easybot | builtin）");
        } else if (backend != null) {
            String b = String.valueOf(backend);
            if (!b.equals("easybot") && !b.equals("builtin")) {
                issues.add("非法: im.backend=\"" + b + "\"（仅支持 easybot | builtin）——通道选择失败将按 builtin 路径处理");
            }
        }
        ConfigurationSection platforms = cfg.getConfigurationSection("platforms");
        // 顶层全局 proxy 段类型（与平台段是否存在无关）
        validateProxySection(cfg.getConfigurationSection("proxy"), "proxy", issues);
        if (platforms == null) {
            return;
        }
        // 已知 builtin 平台段：启用时凭据键必须齐备（拼写错误/缺键 → 显性告警而非静默默认）
        validateImPlatform(platforms, "qq", new String[] {"app_id", "client_secret"}, issues);
        validateImPlatform(platforms, "feishu", new String[] {"app_id", "app_secret"}, issues);
        validateImPlatform(platforms, "telegram", new String[] {"token"}, issues);
        validateImPlatform(platforms, "discord", new String[] {"token"}, issues);
        // 平台级 proxy 段类型
        for (String p : new String[] {"qq", "feishu", "telegram", "discord"}) {
            ConfigurationSection proxy = platforms.getConfigurationSection(p + ".proxy");
            validateProxySection(proxy, "platforms." + p + ".proxy", issues);
        }
    }

    private static void validateImPlatform(
            ConfigurationSection platforms, String id, String[] requiredKeys, List<String> issues) {
        if (!platforms.contains(id) || !platforms.isConfigurationSection(id)) {
            return; // 未配置该平台段
        }
        ConfigurationSection sec = platforms.getConfigurationSection(id);
        if (sec == null) {
            return;
        }
        if (!sec.getBoolean("enabled", false)) {
            return; // 未启用不校验凭据
        }
        for (String key : requiredKeys) {
            Object v = sec.get(key);
            if (v == null || (v instanceof String && String.valueOf(v).isBlank())) {
                issues.add("缺失: platforms." + id + "." + key + "（平台已启用但凭据缺失）——builtin 平台不可用");
            }
        }
    }

    private static void validateProxySection(ConfigurationSection proxy, String path, List<String> issues) {
        if (proxy == null) {
            return;
        }
        Object host = proxy.get("host");
        if (host != null && !(host instanceof String)) {
            issues.add("类型错误: " + path + ".host 需为字符串");
        }
        Object port = proxy.get("port");
        if (port != null) {
            if (!(port instanceof Number)) {
                issues.add("类型错误: " + path + ".port 需为数字");
            } else {
                int p = ((Number) port).intValue();
                if (p <= 0 || p >= 65536) {
                    issues.add("非法: " + path + ".port=" + p + "（需在 1..65535）");
                }
            }
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
            // 事件正文已迁语言包 event.*（P4b）：磁盘 body 可缺（存量/定制存在时仍被优先渲染）
            if (TemplateKeys.isLangBacked(key)) {
                continue;
            }
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
                if (!cfg.contains("templates." + key) && !TemplateKeys.isLangBacked(key)) {
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

    /**
     * guide_book.yml（运行时数据文件，不参与 schema 迁移）：复用解析器做「可用性」校验——
     * 开关/书名/作者类型、颜色可解析性、条目类型与**页号/行号级**格式问题（解析器已做纯文本兜底，此处只上报）。
     */
    private static void validateGuideBook(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("guide_book.yml 未加载");
            return;
        }
        Object enable = cfg.get("enable");
        if (enable != null && !(enable instanceof Boolean)) {
            issues.add("类型错误: guide_book.enable 需为布尔值");
        }
        if (cfg.get("title") != null && !(cfg.get("title") instanceof String)) {
            issues.add("类型错误: guide_book.title 需为字符串");
        }
        if (cfg.get("author") != null && !(cfg.get("author") instanceof String)) {
            issues.add("类型错误: guide_book.author 需为字符串");
        }
        for (String issue : new GuideBookConfigParser().parse(cfg).issues()) {
            issues.add("guide_book.yml: " + issue);
        }
    }
}
