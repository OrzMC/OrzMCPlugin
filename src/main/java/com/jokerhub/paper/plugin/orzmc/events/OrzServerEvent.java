package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.utils.OrzUserCmd;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;

public class OrzServerEvent extends OrzBaseListener {
    public OrzServerEvent(OrzMC plugin) {
        super(plugin);
    }

    @EventHandler
    public void onException(ServerExceptionEvent event) {
        ServerException exception = event.getException();
        plugin.sendPrivateMessage(exception.toString());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        String onlineMode = OrzMC.server().getOnlineMode() ? "正版服" : "离线服";
        String minecraftVersion = OrzMC.server().getMinecraftVersion();
        String[] parts = {"Minecraft", minecraftVersion, onlineMode};
        StringBuilder stringBuilder = new StringBuilder(String.join(" ", parts)).append("\n");
        stringBuilder.append("------").append("\n");
        switch (event.getType()) {
            case STARTUP -> stringBuilder.append("启动完成");
            case RELOAD -> stringBuilder.append("重启完成");
        }
        stringBuilder.append("\n\n");
        stringBuilder.append("发送 \"").append(OrzUserCmd.SHOW_HELP.getCmdString()).append("\" 查看支持的命令消息");
        plugin.sendPublicMessage(stringBuilder.toString());
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (OrzMessageParser.isBackupRunning) {
            String msg = plugin.getConfig().getString("backup_maintenance_motd", "服务器维护中，稍后再试");
            String discordLink = plugin.configManager.getConfig("bot").getString("discord_server_link");
            String qqGroupId = plugin.configManager.getConfig("config").getString("qq_player_group_id");
            TextComponent.Builder motdBuilder = Component.text();
            motdBuilder.append(OrzTextStyles.warn("⚠ 维护中").decorate(TextDecoration.BOLD));
            motdBuilder.append(Component.newline());
            motdBuilder.append(OrzTextStyles.info(msg));
            if (qqGroupId != null && !qqGroupId.isEmpty()) {
                motdBuilder.append(Component.newline());
                motdBuilder.append(OrzTextStyles.info("QQ群: ")).append(OrzTextStyles.warn(qqGroupId));
            }
            if (discordLink != null && !discordLink.isEmpty()) {
                motdBuilder.append(Component.newline());
                motdBuilder.append(OrzTextStyles.info("Discord: ")).append(Component.text(discordLink).decorate(TextDecoration.UNDERLINED).hoverEvent(HoverEvent.showText(Component.text("点击加入 Discord"))).clickEvent(ClickEvent.openUrl(discordLink)));
            }
            Component comp = motdBuilder.build();
            event.motd(comp);
        }
    }
}
