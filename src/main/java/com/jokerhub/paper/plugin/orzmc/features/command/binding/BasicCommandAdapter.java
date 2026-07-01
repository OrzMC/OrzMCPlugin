package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.Nullable;

/**
 * 将 Bukkit 的 {@link CommandExecutor} + 拦截器链适配为 Paper 26.1 的 {@link BasicCommand} 接口。
 *
 * <p>使通过 {@code JavaPlugin#registerCommand(String, String, Collection, BasicCommand)}
 * 注册的命令能复用现有的命令实现和拦截器（PlayerOnly、AdminOnly、Cooldown）。
 * Tab 补全通过可选的 {@link TabCompleter} 委托实现。
 */
public final class BasicCommandAdapter implements BasicCommand {

    private final String commandName;
    private final CommandExecutor executor;
    private final List<CommandInterceptor> interceptors;
    private final @Nullable TabCompleter tabCompleter;

    public BasicCommandAdapter(
            String commandName,
            CommandExecutor executor,
            List<CommandInterceptor> interceptors,
            @Nullable TabCompleter tabCompleter) {
        this.commandName = commandName;
        this.executor = executor;
        this.interceptors = interceptors;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        // Run interceptor chain
        for (CommandInterceptor ci : interceptors) {
            Component res = ci.preHandle(sender, commandName);
            if (res != null) {
                sender.sendMessage(res);
                return;
            }
        }
        // Delegate to original Bukkit CommandExecutor
        // Command parameter is null — none of the existing handlers use it
        executor.onCommand(sender, null, commandName, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (tabCompleter == null) {
            return List.of();
        }
        return tabCompleter.onTabComplete(stack.getSender(), null, commandName, args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        for (CommandInterceptor ci : interceptors) {
            if (ci instanceof AdminOnlyInterceptor aoi) {
                return aoi.canUse(sender);
            }
        }
        return true;
    }
}
