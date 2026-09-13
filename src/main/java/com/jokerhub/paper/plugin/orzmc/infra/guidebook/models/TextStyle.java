package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

/**
 * 单行文本样式（不可变，字段一律非空：空值在解析期就已兜底，渲染层无需再判空）。
 *
 * <p>{@code color} 保留配置原文（命名色如 {@code AQUA} 或 {@code #RRGGBB}），由
 * {@code GuideColorParser} 在渲染时解析；解析失败回退默认色并告警，不会抛异常。</p>
 */
public record TextStyle(boolean bold, boolean underlined, String color) {

    /** 无样式。 */
    public static final TextStyle NONE = new TextStyle(false, false, "");

    public TextStyle {
        color = color == null ? "" : color.trim();
    }

    /** 是否配置了颜色值（是否合法由渲染期解析器判定）。 */
    public boolean hasColor() {
        return !color.isEmpty();
    }

    public TextStyle withBold(boolean value) {
        return new TextStyle(value, underlined, color);
    }

    public TextStyle withUnderlined(boolean value) {
        return new TextStyle(bold, value, color);
    }
}
