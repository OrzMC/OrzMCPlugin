package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import org.jetbrains.annotations.NotNull;

/** 纯文本行（不可变）。 */
public record TextItem(String content, TextStyle style, int blankLinesAfter) implements ContentItem {

    public TextItem {
        content = content == null ? "" : content;
        style = style == null ? TextStyle.NONE : style;
        blankLinesAfter = Math.max(0, blankLinesAfter);
    }

    /** 一个空行（等价于简写格式里的 {@code ""}）。 */
    public static TextItem blank() {
        return new TextItem("", TextStyle.NONE, 1);
    }

    @Override
    public @NotNull String toString() {
        return "TextItem{content='" + content + "', style=" + style + ", blankLinesAfter=" + blankLinesAfter + "}";
    }
}
