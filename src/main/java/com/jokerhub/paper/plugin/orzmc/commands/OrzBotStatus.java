package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class OrzBotStatus implements CommandExecutor {
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        HealthRegistry.Status qq = HealthRegistry.get("qq");
        HealthRegistry.Status discord = HealthRegistry.get("discord");
        HealthRegistry.Status lark = HealthRegistry.get("lark");
        Component message = OrzTextStyles.warn("QQBot:")
                .append(Component.space())
                .append(qq.enabled ? OrzTextStyles.success("enabled") : OrzTextStyles.error("disabled"))
                .append(Component.space())
                .append(qq.httpOk ? OrzTextStyles.success("httpOk") : OrzTextStyles.error("httpNotOk"))
                .append(Component.space())
                .append(qq.wsConnected ? OrzTextStyles.success("wsOk") : OrzTextStyles.error("wsNotOk"))
                .append(Component.space())
                .append(
                        qq.lastError == null
                                ? OrzTextStyles.success("")
                                : OrzTextStyles.error("lastError: " + qq.lastError))
                .append(Component.newline())
                .append(OrzTextStyles.warn("DiscordBot:"))
                .append(Component.space())
                .append(discord.enabled ? OrzTextStyles.success("enabled") : OrzTextStyles.error("disabled"))
                .append(Component.space())
                .append(discord.apiReady ? OrzTextStyles.success("apiReady") : OrzTextStyles.error("apiNotReady"))
                .append(Component.space())
                .append(
                        discord.lastError == null
                                ? OrzTextStyles.success("")
                                : OrzTextStyles.error("larkError" + discord.lastError))
                .append(Component.newline())
                .append(OrzTextStyles.warn("LarkBot:"))
                .append(Component.space())
                .append(lark.enabled ? OrzTextStyles.success("enabled") : OrzTextStyles.error("disabled"))
                .append(Component.space())
                .append(lark.httpOk ? OrzTextStyles.success("httpOk") : OrzTextStyles.error("httpNotOk"))
                .append(Component.space())
                .append(
                        lark.lastError == null
                                ? OrzTextStyles.success("")
                                : OrzTextStyles.error("larkError: " + lark.lastError));
        sender.sendMessage(message);
        return true;
    }
}
