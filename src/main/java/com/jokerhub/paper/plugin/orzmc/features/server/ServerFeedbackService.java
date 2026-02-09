package com.jokerhub.paper.plugin.orzmc.features.server;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.OrzUserCmd;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.server.ServerLoadEvent;

public final class ServerFeedbackService {
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;

    public ServerFeedbackService(ServerFacade server, TypedConfigProvider configs, OrzTextStyles styles) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
    }

    public String buildServerLoadMessage(ServerLoadEvent event) {
        String onlineMode = server.server().getOnlineMode() ? "正版服" : "离线服";
        String minecraftVersion = server.server().getMinecraftVersion();
        String[] parts = {"Minecraft", minecraftVersion, onlineMode};
        StringBuilder stringBuilder = new StringBuilder(String.join(" ", parts)).append("\n");
        stringBuilder.append("------").append("\n");
        switch (event.getType()) {
            case STARTUP -> stringBuilder.append("启动完成");
            case RELOAD -> stringBuilder.append("重启完成");
        }
        stringBuilder.append("\n\n");
        String prompt = configs.bot().cmdPromptChar();
        stringBuilder
                .append("发送 \"")
                .append(prompt)
                .append(OrzUserCmd.SHOW_HELP.cmdName())
                .append("\" 查看支持的命令消息");
        return stringBuilder.toString();
    }

    public Component buildMaintenanceMotd() {
        TypedConfigs.MaintenanceConfig maintenance = configs.maintenance();
        TypedConfigs.BotConfig botConfig = configs.bot();
        String msg = maintenance.backupMaintenanceMotd();
        String discordLink = botConfig.discordServerLink();
        String qqGroupId = botConfig.qqGroupId();
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
