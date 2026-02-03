package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;

public final class CommandPermissionService {
    public record PermissionResult(boolean allowed, TextComponent message) {}

    private final CommandFeedbackService feedbackService = new CommandFeedbackService();

    public PermissionResult requireAdmin(Player player) {
        if (!(player.isOp() || player.hasPermission("orzmc.admin"))) {
            return new PermissionResult(false, feedbackService.adminRequiredTip());
        }
        return new PermissionResult(true, Component.text(""));
    }
}
