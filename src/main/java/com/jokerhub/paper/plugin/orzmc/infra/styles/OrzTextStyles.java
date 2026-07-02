package com.jokerhub.paper.plugin.orzmc.infra.styles;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Styles;
import com.jokerhub.paper.plugin.orzmc.infra.core.OrzConstants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class OrzTextStyles {
    private final ConfigService configService;

    public OrzTextStyles(ConfigService configService) {
        this.configService = configService;
    }

    private TextColor colorOrDefault(String key, String defaultHex) {
        try {
            ConfigurationSection stylesSection =
                    configService.getConfig("templates").getConfigurationSection("styles");
            if (stylesSection == null) {
                FileConfiguration legacy = configService.loadFile("styles.yml");
                stylesSection = legacy != null ? legacy.getConfigurationSection("styles") : null;
            }
            Styles styles = Styles.from(stylesSection);
            String hex = styles.colors().getOrDefault(key, defaultHex);
            if (hex == null || hex.isEmpty()) return TextColor.fromCSSHexString(defaultHex);
            return TextColor.fromCSSHexString(hex);
        } catch (Exception ignored) {
            return TextColor.fromCSSHexString(defaultHex);
        }
    }

    public TextColor colorAlertTnt() {
        return colorOrDefault("tnt_alert", "#FF5555");
    }

    public TextColor colorAlertExplosion() {
        return colorOrDefault("explosion_alert", "#FFAA00");
    }

    public TextColor colorCoord() {
        return colorOrDefault("coord", "#55FF55");
    }

    public TextColor colorSuccess() {
        return colorOrDefault("success", "#00FF00");
    }

    public TextColor colorInfo() {
        return colorOrDefault("info", "#55AAFF");
    }

    public TextColor colorWarn() {
        return colorOrDefault("warn", "#FFAA00");
    }

    public TextColor colorError() {
        return colorOrDefault("error", "#FF5555");
    }

    public TextColor colorPlayer() {
        return colorOrDefault("player", "#FF5555");
    }

    public TextColor colorUnknown() {
        return colorOrDefault("unknown", "#AAAAAA");
    }

    public TextComponent prefix(String text, TextColor color) {
        return Component.text(text).color(color);
    }

    public TextComponent tntPrefix() {
        return prefix(OrzConstants.PREFIX_TNT_ALERT, colorAlertTnt());
    }

    public TextComponent explosionPrefix() {
        return prefix(OrzConstants.PREFIX_EXPLOSION_ALERT, colorAlertExplosion());
    }

    public TextComponent tpbowPrefix() {
        return Component.text("[传送弓]").color(colorWarn());
    }

    public TextComponent coordComponent(String locString) {
        return Component.text(locString)
                .color(colorCoord())
                .hoverEvent(HoverEvent.showText(Component.text("点击复制坐标")))
                .clickEvent(ClickEvent.copyToClipboard(locString.trim()));
    }

    public TextComponent success(String content) {
        return Component.text(content).color(colorSuccess());
    }

    public TextComponent info(String content) {
        return Component.text(content).color(colorInfo());
    }

    public TextComponent warn(String content) {
        return Component.text(content).color(colorWarn());
    }

    public TextComponent error(String content) {
        return Component.text(content).color(colorError());
    }

    public String coordString(org.bukkit.Location location) {
        return String.format(
                " [%s] %d %d %d ",
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public TextComponent playerName(String name) {
        return Component.text(name).color(colorPlayer());
    }

    public TextComponent unknownLabel() {
        return Component.text("未知玩家").color(colorUnknown());
    }
}
