package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PlayerOnlyInterceptor implements CommandInterceptor {
    private final CommandFeedbackService feedbackService = new CommandFeedbackService();

    @Override
    public Component preHandle(CommandSender sender, String commandName) {
        if (sender instanceof Player) return null;
        return feedbackService.playerRequiredTip();
    }
}
