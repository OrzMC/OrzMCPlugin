package com.jokerhub.paper.plugin.orzmc.infra.binding;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class CooldownInterceptor implements CommandInterceptor {
    private final String commandName;
    private final int cooldownSeconds;

    public CooldownInterceptor(String commandName, int cooldownSeconds) {
        this.commandName = commandName;
        this.cooldownSeconds = cooldownSeconds;
    }

    @Override
    public Component preHandle(CommandSender sender, String ignored) {
        String key = commandName + "|" + sender.getName();
        if (CooldownRegistry.isCoolingDown(key, cooldownSeconds)) {
            return Component.text("命令冷却中，请稍后再试");
        }
        return null;
    }
}
