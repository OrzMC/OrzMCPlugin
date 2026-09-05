package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 玩家名颜色（按权限等级）配置。
 *
 * <p>对应 config.yml 的 {@code rank_colors:} 段：是否启用、是否启用头顶名牌着色、
 * 是否启用 Tab 列表着色、OP 专用色（OP 与四级权限独立，isOp 优先），
 * 以及四级权限组 → 命名色映射。</p>
 *
 * <p>计分板队伍颜色协议只支持 16 个命名色（NamedTextColor，无任意 hex 重载）：
 * 为让头顶名牌/聊天/Tab 三处颜色完全一致，配置统一用命名色；也兼容
 * {@code #RRGGBB}，自动吸附到最近的命名色。解析失败的键保留默认值。</p>
 */
public record RankColorsConfig(
        boolean enabled,
        boolean nametagEnabled,
        boolean tabEnabled,
        NamedTextColor opColor,
        Map<String, NamedTextColor> colors) {

    /** 默认 OP 色（金色）。 */
    public static final NamedTextColor DEFAULT_OP_COLOR = NamedTextColor.GOLD;

    /** 四级权限组默认颜色映射（default→member→builder→admin）。 */
    public static final Map<String, NamedTextColor> DEFAULTS = Map.of(
            "default", NamedTextColor.GRAY,
            "member", NamedTextColor.AQUA,
            "builder", NamedTextColor.GREEN,
            "admin", NamedTextColor.RED);

    public static RankColorsConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new RankColorsConfig(true, true, false, DEFAULT_OP_COLOR, DEFAULTS);
        }
        Map<String, NamedTextColor> colors = new HashMap<>();
        ConfigurationSection colorsSection = cfg.getConfigurationSection("colors");
        if (colorsSection != null) {
            DEFAULTS.forEach((key, defaultValue) -> {
                NamedTextColor parsed = parseColor(colorsSection.getString(key));
                if (parsed != null) {
                    colors.put(key, parsed);
                }
            });
        }
        // 未配置/解析失败的键补齐默认，保证四组都有色
        DEFAULTS.forEach((key, defaultValue) -> colors.putIfAbsent(key, defaultValue));
        NamedTextColor opColor = parseColor(cfg.getString("op_color"));
        return new RankColorsConfig(
                cfg.getBoolean("enabled", true),
                cfg.getBoolean("nametag_enabled", true),
                cfg.getBoolean("tab_enabled", false),
                opColor != null ? opColor : DEFAULT_OP_COLOR,
                Map.copyOf(colors));
    }

    /** 解析命名色或 {@code #RRGGBB}；无效输入返回 null（调用方保留默认）。 */
    private static NamedTextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        NamedTextColor named = NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        TextColor hex = TextColor.fromCSSHexString(trimmed);
        if (hex != null) {
            return NamedTextColor.nearestTo(hex);
        }
        return null;
    }

    /**
     * 启动健康校验：段缺失为建议（默认配置完整可用，Tab 着色默认关闭）；
     * 颜色接受范围与 {@link #parseColor} 同一口径（命名色或 CSS hex，含 #RRGGBB）。
     */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("建议: config.yml 缺失 rank_colors 配置段，将使用默认配置（Tab 着色默认关闭）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: rank_colors.enabled 需为布尔值");
        Object nt = section.get("nametag_enabled");
        if (nt != null && !(nt instanceof Boolean)) issues.add("类型错误: rank_colors.nametag_enabled 需为布尔值");
        Object tab = section.get("tab_enabled");
        if (tab != null && !(tab instanceof Boolean)) issues.add("类型错误: rank_colors.tab_enabled 需为布尔值");
        String opColor = section.getString("op_color", "");
        if (!opColor.isBlank() && parseColor(opColor) == null) {
            issues.add("非法: rank_colors.op_color '" + opColor + "' 不是合法命名色或 #RRGGBB");
        }
        ConfigurationSection colorsSection = section.getConfigurationSection("colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                String raw = colorsSection.getString(key, "");
                if (!raw.isBlank() && parseColor(raw) == null) {
                    issues.add("非法: rank_colors.colors." + key + " '" + raw + "' 不是合法命名色或 #RRGGBB");
                }
            }
        }
    }
}
