package com.jokerhub.paper.plugin.orzmc.features.bot;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;

public final class BotStatusService {
    private final OrzTextStyles styles;
    private final HealthStatus health;

    public BotStatusService(OrzTextStyles styles, HealthStatus health) {
        this.styles = styles;
        this.health = health;
    }

    public Component buildStatusMessage() {
        HealthStatus.Entry qq = health.get("qq");
        HealthStatus.Entry discord = health.get("discord");
        HealthStatus.Entry lark = health.get("lark");
        return styles.warn("QQBot:")
                .append(Component.space())
                .append(qq.enabled() ? styles.success("enabled") : styles.error("disabled"))
                .append(Component.space())
                .append(qq.httpOk() ? styles.success("httpOk") : styles.error("httpNotOk"))
                .append(Component.space())
                .append(qq.wsConnected() ? styles.success("wsOk") : styles.error("wsNotOk"))
                .append(Component.space())
                .append(qq.lastError() == null ? styles.success("") : styles.error("lastError: " + qq.lastError()))
                .append(Component.newline())
                .append(styles.warn("DiscordBot:"))
                .append(Component.space())
                .append(discord.enabled() ? styles.success("enabled") : styles.error("disabled"))
                .append(Component.space())
                .append(discord.apiReady() ? styles.success("apiReady") : styles.error("apiNotReady"))
                .append(Component.space())
                .append(
                        discord.lastError() == null
                                ? styles.success("")
                                : styles.error("lastError: " + discord.lastError()))
                .append(Component.newline())
                .append(styles.warn("LarkBot:"))
                .append(Component.space())
                .append(lark.enabled() ? styles.success("enabled") : styles.error("disabled"))
                .append(Component.space())
                .append(lark.httpOk() ? styles.success("httpOk") : styles.error("httpNotOk"))
                .append(Component.space())
                .append(lark.lastError() == null ? styles.success("") : styles.error("larkError: " + lark.lastError()));
    }
}
