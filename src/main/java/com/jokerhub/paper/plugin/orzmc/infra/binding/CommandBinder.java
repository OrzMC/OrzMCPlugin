package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandBinder {
    private CommandBinder() {}

    private static final Map<String, CommandMeta> COMMAND_META = Map.of(
            "tpbow", new CommandMeta("传送弓，射出的箭落地时会把自己传送到箭落地的位置", "/tpbow", List.of("tpb")),
            "guide", new CommandMeta("获取新手教程，更快的熟悉服务器", "/guide", List.of()),
            "menu", new CommandMeta("菜单展示", "/menu", List.of()),
            "bot", new CommandMeta("查看机器人健康状态", "/bot", List.of()),
            "portal", new CommandMeta("创建或移除传送门", "/portal <host> [port] 或 /portal remove <host> [port]", List.of()),
            "orzmc",
                    new CommandMeta(
                            "OrzMC 管理命令",
                            "/orzmc reload [config-name] | /orzmc config <list|get|set|reset|dump>",
                            List.of()));

    /**
     * 注册所有命令到服务端命令映射表。
     *
     * <p>Paper 26.1 插件模式忽略 YAML 命令声明（包括 paper-plugin.yml 和 plugin.yml），
     * 改由 {@code CommandMap#register} 直接注册。此方式在 Paper 26.1 和 MockBukkit 中均有效。
     */
    public static void bind(JavaPlugin plugin, Map<String, CommandExecutor> handlers) {
        CommandMap commandMap = plugin.getServer().getCommandMap();
        handlers.forEach((key, executor) -> {
            CommandMeta meta = COMMAND_META.getOrDefault(key, new CommandMeta("", "/" + key, List.of()));
            Command cmd = new Command(key, meta.description(), meta.usage(), meta.aliases()) {
                @Override
                public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                    return executor.onCommand(sender, this, label, args);
                }
            };
            if ("orzmc".equals(key)) {
                cmd.setPermission("orzmc.admin");
            }
            commandMap.register(key, cmd);
        });
    }

    private record CommandMeta(String description, String usage, List<String> aliases) {}
}
