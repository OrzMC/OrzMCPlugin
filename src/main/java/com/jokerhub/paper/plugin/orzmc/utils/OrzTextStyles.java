package com.jokerhub.paper.plugin.orzmc.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import com.jokerhub.paper.plugin.orzmc.OrzMC;

public final class OrzTextStyles {
    private static TextColor colorOrDefault(String path, String defaultHex) {
        try {
            String hex = OrzMC.plugin().configManager.getConfig("config").getString(path, defaultHex);
            if (hex == null || hex.isEmpty()) return TextColor.fromCSSHexString(defaultHex);
            return TextColor.fromCSSHexString(hex);
        } catch (Exception ignored) {
            return TextColor.fromCSSHexString(defaultHex);
        }
    }

    public static TextColor colorAlertTnt() { return colorOrDefault("styles.colors.tnt_alert", "#FF5555"); }
    public static TextColor colorAlertExplosion() { return colorOrDefault("styles.colors.explosion_alert", "#FFAA00"); }
    public static TextColor colorCoord() { return colorOrDefault("styles.colors.coord", "#55FF55"); }
    public static TextColor colorSuccess() { return colorOrDefault("styles.colors.success", "#00FF00"); }
    public static TextColor colorInfo() { return colorOrDefault("styles.colors.info", "#55AAFF"); }
    public static TextColor colorWarn() { return colorOrDefault("styles.colors.warn", "#FFAA00"); }
    public static TextColor colorError() { return colorOrDefault("styles.colors.error", "#FF5555"); }
    public static TextColor colorPlayer() { return colorOrDefault("styles.colors.player", "#FF5555"); }
    public static TextColor colorUnknown() { return colorOrDefault("styles.colors.unknown", "#AAAAAA"); }

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
        return Component.text("[" + com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow.name + "]").color(colorSuccess());
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
        return String.format(" [%s] %d %d %d ", location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static TextComponent playerName(String name) {
        return Component.text(name).color(colorPlayer());
    }

    public static TextComponent unknownLabel() {
        return Component.text("未知玩家").color(colorUnknown());
    }
}
