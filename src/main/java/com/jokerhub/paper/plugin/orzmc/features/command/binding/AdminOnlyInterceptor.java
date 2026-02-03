package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.security.CommandPermissionService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminOnlyInterceptor implements CommandInterceptor {
    private final boolean adminOnly;
    private final CommandPermissionService permissionService = new CommandPermissionService();

    public AdminOnlyInterceptor(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    @Override
    public Component preHandle(CommandSender sender, String commandName) {
        if (!adminOnly) return null;
        if (sender instanceof Player p) {
            CommandPermissionService.PermissionResult result = permissionService.requireAdmin(p);
            if (!result.allowed()) return result.message();
        }
        return null;
    }
}
