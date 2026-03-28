package com.jokerhub.paper.plugin.orzmc.infra.server;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.ConsoleCommandSender;

public class OrzUtil {
    private static TextComponent textComponent(String content) {
        if (!content.isEmpty()) {
            return Component.text(content);
        }
        return Component.empty();
    }

    public static TextComponent successText(OrzTextStyles styles, String content) {
        return styles.success(content);
    }

    public static TextComponent failureText(OrzTextStyles styles, String content) {
        return styles.error(content);
    }

    public static TextComponent warningText(OrzTextStyles styles, String content) {
        return styles.warn(content);
    }

    public static void executeConsoleCmd(ServerFacade server, Runnable task, String... consoleCmds) {
        ConsoleCommandSender console = server.server().getConsoleSender();
        server.runSync(() -> {
            for (String consoleCmd : consoleCmds) {
                server.server().dispatchCommand(console, consoleCmd);
            }
            if (task != null) {
                task.run();
            }
        });
    }
}
