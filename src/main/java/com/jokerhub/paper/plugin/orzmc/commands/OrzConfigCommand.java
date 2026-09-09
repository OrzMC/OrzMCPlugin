package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /orzmc config} subcommand handler for runtime config inspection and modification.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code list} — list all configurable paths
 *   <li>{@code get <path>} — show current value, default, type, file, description
 *   <li>{@code set <path> <value>} — modify and persist a single value
 *   <li>{@code reset <path>} — restore registered default
 *   <li>{@code dump} — print complete config tree
 * </ul>
 */
public class OrzConfigCommand implements CommandExecutor {
    private final ConfigService configService;
    private final OrzTextStyles textStyles;
    private final Map<String, ConfigPath> registry;
    private final Runnable easyBotConfigReload;
    private final I18nService i18n;
    private Runnable rankColorsReload = () -> {};
    private Runnable accessRulesReload = () -> {};
    private Runnable commandPoliciesReload = () -> {};
    /** 数据目录 i18n 覆盖层（messages_custom_<lang>.yml）改动后的回调（组合根注入）。 */
    private Runnable i18nCustomReload = () -> {};

    public OrzConfigCommand(ConfigService configService, OrzTextStyles textStyles) {
        this(configService, textStyles, () -> {}, null);
    }

    public OrzConfigCommand(ConfigService configService, OrzTextStyles textStyles, Runnable easyBotConfigReload) {
        this(configService, textStyles, easyBotConfigReload, null);
    }

    public OrzConfigCommand(
            ConfigService configService, OrzTextStyles textStyles, Runnable easyBotConfigReload, I18nService i18n) {
        this.configService = configService;
        this.textStyles = textStyles;
        this.registry = ConfigPath.all();
        this.easyBotConfigReload = easyBotConfigReload == null ? () -> {} : easyBotConfigReload;
        this.i18n = i18n;
    }

    /** /config 为运维命令：文案按 default_lang（R1）决议；i18n 未注入（测试/早期）时返回 key 本体。 */
    private String t(String key) {
        return t(key, Map.of());
    }

    private String t(String key, Map<String, String> vars) {
        if (i18n == null) {
            return key;
        }
        return i18n.msg(i18n.langFor(), key, vars);
    }

    /**
     * 注册 rank_colors.* 配置改动后的回调（组合根注入：{@code /orzmc config} 改完即重刷在线玩家，
     * 消除最长 ~60s 周期自愈才生效的延迟）。回调需自行保证在调度线程执行。
     */
    public void setRankColorsReload(Runnable rankColorsReload) {
        this.rankColorsReload = rankColorsReload == null ? () -> {} : rankColorsReload;
    }

    /**
     * 注册 access_rules 配置改动后的回调（组合根注入：{@code /orzmc config reload} 后
     * 刷新 {@code AccessRuleService} 内存缓存，手动编辑 access_rules.yml 即改即生效）。
     */
    public void setAccessRulesReload(Runnable accessRulesReload) {
        this.accessRulesReload = accessRulesReload == null ? () -> {} : accessRulesReload;
    }

    /**
     * 注册 command_policies.* 配置改动后的回调（组合根注入：{@code /orzmc config} 改完即
     * 刷新命令拦截器的策略快照缓存，消除热路径每次全量重解析，同时保持即改即生效）。
     */
    public void setCommandPoliciesReload(Runnable commandPoliciesReload) {
        this.commandPoliciesReload = commandPoliciesReload == null ? () -> {} : commandPoliciesReload;
    }

    /**
     * 注册 i18n 覆盖层重读回调（组合根注入：{@code /orzmc config reload} 后重读
     * 数据目录 {@code messages_custom_<lang>.yml}，手动编辑即改即生效，无需重启）。
     */
    public void setI18nCustomReload(Runnable i18nCustomReload) {
        this.i18nCustomReload = i18nCustomReload == null ? () -> {} : i18nCustomReload;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender);
            case "get" -> handleGet(sender, args);
            case "set" -> handleSet(sender, args);
            case "reset" -> handleReset(sender, args);
            case "dump" -> handleDump(sender);
            case "reload" -> handleReload(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------
    // subcommand handlers
    // ---------------------------------------------------------------

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_USAGE_TITLE)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_LIST)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_GET)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_SET)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_RESET)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_DUMP)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_RELOAD)));
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_USAGE_EXAMPLE)));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(textStyles.success(
                t(MessageKeys.CMD_CONFIG_LIST_TITLE, Map.of("count", String.valueOf(registry.size())))));
        String lastConfig = null;
        for (Map.Entry<String, ConfigPath> entry : registry.entrySet()) {
            ConfigPath cp = entry.getValue();
            if (!cp.configName().equals(lastConfig)) {
                lastConfig = cp.configName();
                sender.sendMessage(textStyles.success(" [" + cp.configName() + ".yml]"));
            }
            String current = readCurrentDisplay(cp);
            // 说明 = 配置元数据（cp.description()，数据面豁免，不 i18n）
            sender.sendMessage(textStyles.info("  " + entry.getKey() + " = " + current + "  " + cp.description()));
        }
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_GET_USAGE)));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_UNKNOWN_PATH, Map.of("path", key))));
            return;
        }
        FileConfiguration cfg = configService.getConfig(cp.configName());
        Object current = cfg == null ? null : cfg.get(cp.path());
        sender.sendMessage(textStyles.info(" " + key + " = " + formatValue(current)));
        sender.sendMessage(
                textStyles.info(t(MessageKeys.CMD_CONFIG_FIELD_TYPE, Map.of("type", typeDisplay(cp.type())))));
        sender.sendMessage(textStyles.info(
                t(MessageKeys.CMD_CONFIG_FIELD_DEFAULT, Map.of("value", formatValue(cp.defaultValue())))));
        sender.sendMessage(
                textStyles.info(t(MessageKeys.CMD_CONFIG_FIELD_FILE, Map.of("file", cp.configName() + ".yml"))));
        // 说明 = 配置元数据（cp.description()，数据面豁免）
        sender.sendMessage(textStyles.info(t(MessageKeys.CMD_CONFIG_FIELD_DESC, Map.of("desc", cp.description()))));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_SET_USAGE)));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_UNKNOWN_PATH, Map.of("path", key))));
            return;
        }
        // Support multi-word values (e.g. strings with spaces)
        StringBuilder rawValue = new StringBuilder(args[2]);
        for (int i = 3; i < args.length; i++) {
            rawValue.append(" ").append(args[i]);
        }
        try {
            Object parsed = parseValue(rawValue.toString(), cp.type());
            if (parsed == null) {
                sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_VALUE_EMPTY)));
                return;
            }
            FileConfiguration cfg = configService.getConfig(cp.configName());
            if (cfg == null) {
                sender.sendMessage(
                        textStyles.error(t(MessageKeys.CMD_CONFIG_CFG_NOT_LOADED, Map.of("name", cp.configName()))));
                return;
            }
            cfg.set(cp.path(), parsed);
            configService.saveConfig(cp.configName());
            configService.reloadConfig(cp.configName());
            notifyConfigReload(cp);
            sender.sendMessage(textStyles.success(
                    t(MessageKeys.CMD_CONFIG_SET_OK, Map.of("key", key, "value", formatValue(parsed)))));
        } catch (NumberFormatException e) {
            sender.sendMessage(textStyles.error(
                    t(MessageKeys.CMD_CONFIG_TYPE_ERROR, Map.of("key", key, "type", typeDisplay(cp.type())))));
        } catch (IllegalArgumentException e) {
            // parseValue 异常统一为「无法解析」模板（Boolean/不支持类型文案不再 UI 直显）
            sender.sendMessage(textStyles.error(
                    t(MessageKeys.CMD_CONFIG_INVALID_VALUE, Map.of("key", key, "type", typeDisplay(cp.type())))));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_RESET_USAGE)));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error(t(MessageKeys.CMD_CONFIG_UNKNOWN_PATH, Map.of("path", key))));
            return;
        }
        FileConfiguration cfg = configService.getConfig(cp.configName());
        if (cfg == null) {
            sender.sendMessage(
                    textStyles.error(t(MessageKeys.CMD_CONFIG_CFG_NOT_LOADED, Map.of("name", cp.configName()))));
            return;
        }
        cfg.set(cp.path(), cp.defaultValue());
        configService.saveConfig(cp.configName());
        configService.reloadConfig(cp.configName());
        notifyConfigReload(cp);
        sender.sendMessage(textStyles.success(
                t(MessageKeys.CMD_CONFIG_RESET_OK, Map.of("key", key, "value", formatValue(cp.defaultValue())))));
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (configService.reloadConfig(args[1])) {
                notifyConfigNameReload(args[1]);
                sender.sendMessage(textStyles.success(t(MessageKeys.CMD_CONFIG_RELOAD_OK, Map.of("name", args[1]))));
            } else {
                sender.sendMessage(
                        textStyles.error(t(MessageKeys.CMD_CONFIG_RELOAD_NOT_FOUND, Map.of("name", args[1]))));
            }
        } else {
            configService.reloadAll();
            easyBotConfigReload.run();
            rankColorsReload.run();
            accessRulesReload.run();
            commandPoliciesReload.run();
            i18nCustomReload.run();
            sender.sendMessage(textStyles.success(t(MessageKeys.CMD_CONFIG_RELOAD_ALL_OK)));
        }
    }

    /** 按配置路径派发改动后回调：easybot → 连接协调；rank_colors.* → 重刷在线玩家着色；command_policies.* → 刷新命令策略缓存。 */
    private void notifyConfigReload(ConfigPath cp) {
        if ("easybot".equalsIgnoreCase(cp.configName())) {
            easyBotConfigReload.run();
        } else if (cp.path().startsWith("rank_colors.")) {
            rankColorsReload.run();
        } else if (cp.path().startsWith("command_policies.")) {
            commandPoliciesReload.run();
        }
    }

    /** 按配置文件名派发改动后回调：easybot → 连接协调；config.yml（含 rank_colors/command_policies）→ 重刷在线玩家着色 + 命令策略缓存；access_rules → 刷新访问规则缓存。 */
    private void notifyConfigNameReload(String configName) {
        if ("easybot".equalsIgnoreCase(configName)) {
            easyBotConfigReload.run();
        } else if ("config".equalsIgnoreCase(configName)) {
            rankColorsReload.run();
            commandPoliciesReload.run();
        } else if ("access_rules".equalsIgnoreCase(configName)) {
            accessRulesReload.run();
        }
    }

    private void handleDump(CommandSender sender) {
        sender.sendMessage(textStyles.success(t(MessageKeys.CMD_CONFIG_DUMP_TITLE)));
        String lastConfig = null;
        for (ConfigPath cp : registry.values()) {
            if (!cp.configName().equals(lastConfig)) {
                lastConfig = cp.configName();
                sender.sendMessage(textStyles.success(" [" + cp.configName() + ".yml]"));
            }
            FileConfiguration cfg = configService.getConfig(cp.configName());
            Object current = cfg == null ? null : cfg.get(cp.path());
            sender.sendMessage(textStyles.info("  " + cp.path()
                    + " = " + formatValue(current)
                    + "  " + t(MessageKeys.CMD_CONFIG_DUMP_DEFAULT, Map.of("value", formatValue(cp.defaultValue())))));
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private String readCurrentDisplay(ConfigPath cp) {
        FileConfiguration cfg = configService.getConfig(cp.configName());
        Object current = cfg == null ? null : cfg.get(cp.path());
        return formatValue(current);
    }

    private static String formatValue(Object val) {
        if (val == null) return "<null>";
        return String.valueOf(val);
    }

    private static String typeDisplay(Class<?> type) {
        if (type == Boolean.class || type == boolean.class) return "Boolean";
        if (type == Integer.class || type == int.class) return "Integer";
        if (type == Long.class || type == long.class) return "Long";
        if (type == Double.class || type == double.class) return "Double";
        if (type == String.class) return "String";
        return type.getSimpleName();
    }

    private static Object parseValue(String raw, Class<?> type) {
        if (type == Boolean.class || type == boolean.class) {
            return switch (raw.toLowerCase()) {
                case "true", "yes", "1" -> Boolean.TRUE;
                case "false", "no", "0" -> Boolean.FALSE;
                default -> throw new IllegalArgumentException("Boolean 类型需要 true/false/yes/no/1/0，输入: " + raw);
            };
        }
        if (type == Integer.class || type == int.class) return Integer.parseInt(raw.trim());
        if (type == Long.class || type == long.class) return Long.parseLong(raw.trim());
        if (type == Double.class || type == double.class) return Double.parseDouble(raw.trim());
        if (type == String.class) return raw;
        throw new IllegalArgumentException("不支持的类型: " + type.getSimpleName());
    }
}
