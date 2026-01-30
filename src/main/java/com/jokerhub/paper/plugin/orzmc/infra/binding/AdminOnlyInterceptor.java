package com.jokerhub.paper.plugin.orzmc.infra.binding;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminOnlyInterceptor implements CommandInterceptor {
    private final boolean adminOnly;

    public AdminOnlyInterceptor(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    @Override
    public Component preHandle(CommandSender sender, String commandName) {
        if (!adminOnly) return null;
        if (sender instanceof Player p) {
            if (!(p.isOp() || p.hasPermission("orzmc.admin"))) {
                return Component.text("需要管理员权限");
            }
        }
        return null;
    }
}
