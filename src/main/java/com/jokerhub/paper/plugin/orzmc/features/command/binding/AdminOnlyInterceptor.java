package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.security.CommandPermissionService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminOnlyInterceptor implements CommandInterceptor {
    private final Supplier<CommandPolicy> policy;
    private final CommandPermissionService permissionService = new CommandPermissionService();

    /** 静态策略（历史构造器，委托为惰性读取）：等价于固定 {@code adminOnly}。 */
    public AdminOnlyInterceptor(boolean adminOnly) {
        this(() -> new CommandPolicy(0, adminOnly));
    }

    /**
     * 惰性策略：每次 {@code preHandle}/{@code canUse} 都重新读取 {@link CommandPolicy#adminOnly()}，
     * 使 {@code /orzmc config set command_policies.*.admin_only} 改动即时生效，无需重启。
     */
    public AdminOnlyInterceptor(Supplier<CommandPolicy> policy) {
        this.policy = policy;
    }

    @Override
    public Component preHandle(CommandSender sender, String commandName) {
        if (!policy.get().adminOnly()) return null;
        if (sender instanceof Player p) {
            CommandPermissionService.PermissionResult result = permissionService.requireAdmin(p);
            if (!result.allowed()) return result.message();
        }
        return null;
    }

    /**
     * 检查发送者是否有权限使用此命令（用于 {@link io.papermc.paper.command.brigadier.BasicCommand#canUse} 的 Tab 补全过滤）。
     */
    public boolean canUse(CommandSender sender) {
        if (!policy.get().adminOnly()) return true;
        if (sender instanceof Player p) {
            return permissionService.requireAdmin(p).allowed();
        }
        return true; // console
    }
}
