package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.utils.HealthRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public class OrzBotStatus implements CommandExecutor {
    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        HealthRegistry.Status qq = HealthRegistry.get("qq");
        HealthRegistry.Status discord = HealthRegistry.get("discord");
        HealthRegistry.Status lark = HealthRegistry.get("lark");
        String sb = "QQ: enabled=" + qq.enabled + ", httpOk=" + qq.httpOk + ", wsConnected=" + qq.wsConnected + ", lastError=" + qq.lastError + "\n" +
                "Discord: enabled=" + discord.enabled + ", apiReady=" + discord.apiReady + ", lastError=" + discord.lastError + "\n" +
                "Lark: enabled=" + lark.enabled + ", httpOk=" + lark.httpOk + ", lastError=" + lark.lastError;
        sender.sendMessage(sb);
        return true;
    }
}
