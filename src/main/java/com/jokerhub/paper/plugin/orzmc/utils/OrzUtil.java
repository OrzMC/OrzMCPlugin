package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.ConsoleCommandSender;

public class OrzUtil {

    private static TextComponent textComponent(String content) {
        if (!content.isEmpty()) {
            return Component.text(content);
        }
        return Component.empty();
    }

    public static TextComponent successText(String content) {
        return textComponent(content).color(TextColor.fromCSSHexString("#00FF00"));
    }

    public static TextComponent failureText(String content) {
        return textComponent(content).color(TextColor.fromCSSHexString("#FF0000"));
    }

    public static TextComponent warningText(String content) {
        return textComponent(content).color(TextColor.fromCSSHexString("#FFFF00"));
    }

    public static void executeConsoleCmd(Runnable task, String... consoleCmds) {
        ConsoleCommandSender console = OrzMC.server().getConsoleSender();
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            for (String consoleCmd : consoleCmds) {
                OrzMC.server().dispatchCommand(console, consoleCmd);
            }
            if (task != null) {
                task.run();
            }
        });
    }
}
