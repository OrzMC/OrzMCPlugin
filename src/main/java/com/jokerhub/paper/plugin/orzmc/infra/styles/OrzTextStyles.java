package com.jokerhub.paper.plugin.orzmc.infra.styles;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.core.OrzConstants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;

public final class OrzTextStyles {
    private static TextColor colorOrDefault(String key, String defaultHex) {
        try {
            TypedConfigs.Styles styles =
                    TypedConfigs.Styles.from(OrzMC.plugin().configManager.getConfig("styles"));
            String hex = styles.colors().getOrDefault(key, defaultHex);
            if (hex == null || hex.isEmpty()) return TextColor.fromCSSHexString(defaultHex);
            return TextColor.fromCSSHexString(hex);
        } catch (Exception ignored) {
            return TextColor.fromCSSHexString(defaultHex);
        }
    }

    public static TextColor colorAlertTnt() {
        return colorOrDefault("tnt_alert", "#FF5555");
    }

    public static TextColor colorAlertExplosion() {
        return colorOrDefault("explosion_alert", "#FFAA00");
    }

    public static TextColor colorCoord() {
        return colorOrDefault("coord", "#55FF55");
    }

    public static TextColor colorSuccess() {
        return colorOrDefault("success", "#00FF00");
    }

    public static TextColor colorInfo() {
        return colorOrDefault("info", "#55AAFF");
    }

    public static TextColor colorWarn() {
        return colorOrDefault("warn", "#FFAA00");
    }

    public static TextColor colorError() {
        return colorOrDefault("error", "#FF5555");
    }

    public static TextColor colorPlayer() {
        return colorOrDefault("player", "#FF5555");
    }

    public static TextColor colorUnknown() {
        return colorOrDefault("unknown", "#AAAAAA");
    }

    public static TextComponent prefix(String text, TextColor color) {
        return Component.text(text).color(color);
    }

    public static TextComponent tntPrefix() {
        return prefix(OrzConstants.PREFIX_TNT_ALERT, colorAlertTnt());
    }

    public static TextComponent explosionPrefix() {
        return prefix(OrzConstants.PREFIX_EXPLOSION_ALERT, colorAlertExplosion());
    }

    public static TextComponent tpbowPrefix() {
        return Component.text("[" + OrzTPBow.name + "]").color(colorWarn());
    }

    public static TextComponent coordComponent(String locString) {
        return Component.text(locString)
                .color(colorCoord())
                .hoverEvent(HoverEvent.showText(Component.text("点击复制坐标")))
                .clickEvent(ClickEvent.copyToClipboard(locString.trim()));
    }

    public static TextComponent success(String content) {
        return Component.text(content).color(colorSuccess());
    }

    public static TextComponent info(String content) {
        return Component.text(content).color(colorInfo());
    }

    public static TextComponent warn(String content) {
        return Component.text(content).color(colorWarn());
    }

    public static TextComponent error(String content) {
        return Component.text(content).color(colorError());
    }

    public static String coordString(org.bukkit.Location location) {
        return String.format(
                " [%s] %d %d %d ",
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static TextComponent playerName(String name) {
        return Component.text(name).color(colorPlayer());
    }

    public static TextComponent unknownLabel() {
        return Component.text("未知玩家").color(colorUnknown());
    }
}
