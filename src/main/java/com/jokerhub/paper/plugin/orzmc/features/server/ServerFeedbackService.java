package com.jokerhub.paper.plugin.orzmc.features.server;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.OrzUserCmd;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.server.ServerLoadEvent;

public final class ServerFeedbackService {
    private final ConfigService configService;
    private final OrzTextStyles styles;

    public ServerFeedbackService(ConfigService configService, OrzTextStyles styles) {
        this.configService = configService;
        this.styles = styles;
    }

    public String buildServerLoadMessage(ServerLoadEvent event) {
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
        stringBuilder
                .append("发送 \"")
                .append(OrzUserCmd.SHOW_HELP.getCmdString())
                .append("\" 查看支持的命令消息");
        return stringBuilder.toString();
    }

    public Component buildMaintenanceMotd() {
        String msg = configService.getConfig("maintenance").getString("backup_maintenance_motd", "服务器维护中，稍后再试");
        String discordLink = configService.getConfig("bot").getString("discord_server_link");
        String qqGroupId = configService.getConfig("bot").getString("qq_group_id");
        TextComponent.Builder motdBuilder = Component.text();
        motdBuilder.append(styles.warn("⚠ 维护中").decorate(TextDecoration.BOLD));
        motdBuilder.append(Component.newline());
        motdBuilder.append(styles.info(msg));
        if (qqGroupId != null && !qqGroupId.isEmpty()) {
            motdBuilder.append(Component.newline());
            motdBuilder.append(styles.info("QQ群: ")).append(styles.warn(qqGroupId));
        }
        if (discordLink != null && !discordLink.isEmpty()) {
            motdBuilder.append(Component.newline());
            motdBuilder
                    .append(styles.info("Discord: "))
                    .append(Component.text(discordLink)
                            .decorate(TextDecoration.UNDERLINED)
                            .hoverEvent(HoverEvent.showText(Component.text("点击加入 Discord")))
                            .clickEvent(ClickEvent.openUrl(discordLink)));
        }
        return motdBuilder.build();
    }
}
