package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class CooldownInterceptor implements CommandInterceptor {
    private final String commandName;
    private final int cooldownSeconds;
    private final CommandFeedbackService feedbackService = new CommandFeedbackService();

    public CooldownInterceptor(String commandName, int cooldownSeconds) {
        this.commandName = commandName;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Override
    public Component preHandle(CommandSender sender, String ignored) {
        String key = commandName + "|" + sender.getName();
        if (CooldownRegistry.isCoolingDown(key, cooldownSeconds)) {
            return feedbackService.cooldownTip();
        }
        return null;
    }
}
