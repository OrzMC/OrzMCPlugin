package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public interface CommandInterceptor {
    Component preHandle(CommandSender sender, String commandName);
}
