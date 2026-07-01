package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/**
 * 通用的 {@link TabCompleter} 委托实现，支持固定列表补全和多级上下文的函数式补全。
 *
 * <p>用法：
 * <pre>{@code
 * // 一级子命令补全
 * TabCompleterDelegate.of(List.of("list", "get", "set"));
 *
 * // 多级上下文补全
 * TabCompleterDelegate.of(args -> {
 *     if (args.length == 1) return List.of("list", "get", "set");
 *     if (args.length == 2 && "get".equals(args[0])) return configPaths;
 *     return List.of();
 * });
 * }</pre>
 */
public final class TabCompleterDelegate implements TabCompleter {

    private final Function<String[], Collection<String>> completer;

    private TabCompleterDelegate(Function<String[], Collection<String>> completer) {
        this.completer = completer;
    }

    /**
     * 创建固定列表补全 — 仅第一参数有建议。
     *
     * @param suggestions 第一参数的可选值
     */
    public static TabCompleterDelegate of(Collection<String> suggestions) {
        List<String> fixed = List.copyOf(suggestions);
        return new TabCompleterDelegate(args -> {
            if (args.length == 1) {
                return filterByPrefix(fixed, args[0]);
            }
            return List.of();
        });
    }

    /**
     * 创建多级上下文补全 — 根据当前 {@code args} 动态返回建议列表。
     *
     * @param completer 接收当前 args（不含命令名），返回补全建议
     */
    public static TabCompleterDelegate of(Function<String[], Collection<String>> completer) {
        return new TabCompleterDelegate(completer);
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // skip first empty arg
        String[] partial = args.length > 0 && args[0].isEmpty() ? new String[] {""} : args;
        Collection<String> suggestions = completer.apply(partial);
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }
        // If there's exactly one suggestion that matches, return it to auto-complete
        String prefix = partial.length > 0 ? partial[partial.length - 1].toLowerCase() : "";
        List<String> filtered = new ArrayList<>();
        for (String s : suggestions) {
            if (prefix.isEmpty() || s.toLowerCase().startsWith(prefix)) {
                filtered.add(s);
            }
        }
        Collections.sort(filtered);
        return filtered;
    }

    private static List<String> filterByPrefix(Collection<String> suggestions, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(suggestions);
        }
        String lower = prefix.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String s : suggestions) {
            if (s.toLowerCase().startsWith(lower)) {
                filtered.add(s);
            }
        }
        Collections.sort(filtered);
        return filtered;
    }
}
