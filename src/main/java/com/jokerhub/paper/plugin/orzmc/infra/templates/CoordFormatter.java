package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * 坐标格式化工具。
 *
 * <p>将 Bukkit {@link Location} 转换为模板引擎使用的模板变量键值对。
 * 消除 TntEventService、PlayerEventService 等业务服务中重复的坐标格式化代码。</p>
 */
public final class CoordFormatter {

    private CoordFormatter() {}

    /** 模板变量键：世界别名 */
    private static final String KEY_WORLD = "world";

    /** 模板变量键：原始世界名 */
    private static final String KEY_WORLD_ALIAS = "world_alias";

    /** 模板变量键：方块 X */
    private static final String KEY_X = "x";

    /** 模板变量键：方块 Y */
    private static final String KEY_Y = "y";

    /** 模板变量键：方块 Z */
    private static final String KEY_Z = "z";

    /** 模板变量键：X 坐标（带单位缩放） */
    private static final String KEY_X_UNIT = "x_unit";

    /** 模板变量键：Y 坐标（带单位缩放） */
    private static final String KEY_Y_UNIT = "y_unit";

    /** 模板变量键：Z 坐标（带单位缩放） */
    private static final String KEY_Z_UNIT = "z_unit";

    /** 模板变量键：坐标单位标签 */
    private static final String KEY_COORD_UNIT = "coord_unit";

    /**
     * 根据 {@link Location} 和 {@link TemplateOptions} 构建坐标相关模板变量。
     *
     * <p>返回的 map 包含以下键：{@code world}, {@code world_alias}, {@code x}, {@code y}, {@code z},
     * {@code x_unit}, {@code y_unit}, {@code z_unit}, {@code coord_unit}。</p>
     *
     * @param location Bukkit 位置对象
     * @param opt      模板选项（含缩放、精度、别名映射）
     * @return 模板变量键值对，不会返回 null
     */
    public static @NotNull Map<String, String> format(@NotNull Location location, @NotNull TemplateOptions opt) {
        Map<String, String> vars = new HashMap<>();
        String world = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        String env = location.getWorld() != null
                ? location.getWorld().getEnvironment().name()
                : "";
        String worldAlias = TemplateResolvers.worldAlias(world, env, opt);
        double scale = opt.coordScale() <= 0 ? 1.0 : opt.coordScale();
        int precision = Math.max(0, opt.coordPrecision());
        String fmt = "%." + precision + "f";
        String xUnit = String.format(fmt, location.getBlockX() * scale);
        String yUnit = String.format(fmt, location.getBlockY() * scale);
        String zUnit = String.format(fmt, location.getBlockZ() * scale);
        vars.put(KEY_WORLD, worldAlias);
        vars.put(KEY_WORLD_ALIAS, worldAlias);
        vars.put(KEY_X, String.valueOf(location.getBlockX()));
        vars.put(KEY_Y, String.valueOf(location.getBlockY()));
        vars.put(KEY_Z, String.valueOf(location.getBlockZ()));
        vars.put(KEY_X_UNIT, xUnit);
        vars.put(KEY_Y_UNIT, yUnit);
        vars.put(KEY_Z_UNIT, zUnit);
        vars.put(KEY_COORD_UNIT, opt.coordUnitLabel());
        return vars;
    }
}
