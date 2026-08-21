package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.Map;
import net.kyori.adventure.text.Component;
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
    private Runnable rankColorsReload = () -> {};

    public OrzConfigCommand(ConfigService configService, OrzTextStyles textStyles) {
        this(configService, textStyles, () -> {});
    }

    public OrzConfigCommand(ConfigService configService, OrzTextStyles textStyles, Runnable easyBotConfigReload) {
        this.configService = configService;
        this.textStyles = textStyles;
        this.registry = ConfigPath.all();
        this.easyBotConfigReload = easyBotConfigReload == null ? () -> {} : easyBotConfigReload;
    }

    /**
     * 注册 rank_colors.* 配置改动后的回调（组合根注入：{@code /orzmc config} 改完即重刷在线玩家，
     * 消除最长 ~60s 周期自愈才生效的延迟）。回调需自行保证在调度线程执行。
     */
    public void setRankColorsReload(Runnable rankColorsReload) {
        this.rankColorsReload = rankColorsReload == null ? () -> {} : rankColorsReload;
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
        sender.sendMessage(textStyles.error("用法: /config <子命令> [参数]"));
        sender.sendMessage(textStyles.info("  list                     列出所有可调配置项"));
        sender.sendMessage(textStyles.info("  get <路径>                查看配置值"));
        sender.sendMessage(textStyles.info("  set <路径> <值>           修改配置并持久化"));
        sender.sendMessage(textStyles.info("  reset <路径>              恢复默认值"));
        sender.sendMessage(textStyles.info("  dump                     打印完整配置树"));
        sender.sendMessage(textStyles.info("  reload [name]            重新加载配置文件"));
        sender.sendMessage(textStyles.info("示例: /config get tnt.enable"));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(
                Component.text("=== 可运行时配置项 (" + registry.size() + " 项) ===").color(textStyles.colorSuccess()));
        String lastConfig = null;
        for (Map.Entry<String, ConfigPath> entry : registry.entrySet()) {
            ConfigPath cp = entry.getValue();
            if (!cp.configName().equals(lastConfig)) {
                lastConfig = cp.configName();
                sender.sendMessage(
                        Component.text(" [" + cp.configName() + ".yml]").color(textStyles.colorSuccess()));
            }
            String current = readCurrentDisplay(cp);
            sender.sendMessage(Component.text("  " + entry.getKey())
                    .append(Component.text(" = " + current + "  "))
                    .append(Component.text(cp.description()))
                    .color(textStyles.colorInfo()));
        }
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(textStyles.error("用法: /orzmc config get <路径>"));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error("未知配置路径: " + key));
            return;
        }
        FileConfiguration cfg = configService.getConfig(cp.configName());
        Object current = cfg == null ? null : cfg.get(cp.path());
        sender.sendMessage(
                Component.text(" " + key + " = " + formatValue(current)).color(textStyles.colorInfo()));
        sender.sendMessage(Component.text("  类型: " + typeDisplay(cp.type())).color(textStyles.colorInfo()));
        sender.sendMessage(
                Component.text("  默认: " + formatValue(cp.defaultValue())).color(textStyles.colorInfo()));
        sender.sendMessage(Component.text("  文件: " + cp.configName() + ".yml").color(textStyles.colorInfo()));
        sender.sendMessage(Component.text("  说明: " + cp.description()).color(textStyles.colorInfo()));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(textStyles.error("用法: /orzmc config set <路径> <值>"));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error("未知配置路径: " + key));
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
                sender.sendMessage(textStyles.error("值为空或不合法"));
                return;
            }
            FileConfiguration cfg = configService.getConfig(cp.configName());
            if (cfg == null) {
                sender.sendMessage(textStyles.error("配置文件未加载: " + cp.configName()));
                return;
            }
            cfg.set(cp.path(), parsed);
            configService.saveConfig(cp.configName());
            configService.reloadConfig(cp.configName());
            notifyConfigReload(cp);
            sender.sendMessage(textStyles.success("已设置: " + key + " = " + formatValue(parsed)));
        } catch (NumberFormatException e) {
            sender.sendMessage(textStyles.error("类型错误: " + key + " 需为 " + typeDisplay(cp.type()) + "，输入值无法解析"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(textStyles.error(e.getMessage()));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(textStyles.error("用法: /orzmc config reset <路径>"));
            return;
        }
        String key = args[1];
        ConfigPath cp = registry.get(key);
        if (cp == null) {
            sender.sendMessage(textStyles.error("未知配置路径: " + key));
            return;
        }
        FileConfiguration cfg = configService.getConfig(cp.configName());
        if (cfg == null) {
            sender.sendMessage(textStyles.error("配置文件未加载: " + cp.configName()));
            return;
        }
        cfg.set(cp.path(), cp.defaultValue());
        configService.saveConfig(cp.configName());
        configService.reloadConfig(cp.configName());
        notifyConfigReload(cp);
        sender.sendMessage(textStyles.success("已恢复默认: " + key + " = " + formatValue(cp.defaultValue())));
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (configService.reloadConfig(args[1])) {
                notifyConfigNameReload(args[1]);
                sender.sendMessage(textStyles.success("配置文件 " + args[1] + " 已重新加载"));
            } else {
                sender.sendMessage(textStyles.error("配置文件 " + args[1] + " 不存在"));
            }
        } else {
            configService.reloadAll();
            easyBotConfigReload.run();
            rankColorsReload.run();
            sender.sendMessage(textStyles.success("所有配置文件已重新加载"));
        }
    }

    /** 按配置路径派发改动后回调：easybot → 连接协调；rank_colors.* → 重刷在线玩家着色。 */
    private void notifyConfigReload(ConfigPath cp) {
        if ("easybot".equalsIgnoreCase(cp.configName())) {
            easyBotConfigReload.run();
        } else if (cp.path().startsWith("rank_colors.")) {
            rankColorsReload.run();
        }
    }

    /** 按配置文件名派发改动后回调：easybot → 连接协调；config.yml（含 rank_colors）→ 重刷在线玩家着色。 */
    private void notifyConfigNameReload(String configName) {
        if ("easybot".equalsIgnoreCase(configName)) {
            easyBotConfigReload.run();
        } else if ("config".equalsIgnoreCase(configName)) {
            rankColorsReload.run();
        }
    }

    private void handleDump(CommandSender sender) {
        sender.sendMessage(Component.text("=== 完整配置 Dump ===").color(textStyles.colorSuccess()));
        String lastConfig = null;
        for (ConfigPath cp : registry.values()) {
            if (!cp.configName().equals(lastConfig)) {
                lastConfig = cp.configName();
                sender.sendMessage(
                        Component.text(" [" + cp.configName() + ".yml]").color(textStyles.colorSuccess()));
            }
            FileConfiguration cfg = configService.getConfig(cp.configName());
            Object current = cfg == null ? null : cfg.get(cp.path());
            sender.sendMessage(Component.text("  " + cp.path())
                    .append(Component.text(" = " + formatValue(current)))
                    .append(Component.text("  (默认: " + formatValue(cp.defaultValue()) + ")"))
                    .color(textStyles.colorInfo()));
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
