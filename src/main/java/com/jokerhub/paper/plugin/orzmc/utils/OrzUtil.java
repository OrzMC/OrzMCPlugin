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
        return OrzTextStyles.success(content);
    }

    public static TextComponent failureText(String content) {
        return OrzTextStyles.error(content);
    }

    public static TextComponent warningText(String content) {
        return OrzTextStyles.warn(content);
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
