package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import org.jetbrains.annotations.NotNull;

/** 可点击链接行（不可变）：点击打开 {@code url}，悬停显示 {@code hoverText}。 */
public record LinkItem(String content, String url, String hoverText, TextStyle style, int blankLinesAfter)
        implements ContentItem {

    public LinkItem {
        content = content == null ? "" : content;
        url = url == null ? "" : url;
        hoverText = hoverText == null ? "" : hoverText;
        style = style == null ? TextStyle.NONE : style;
        blankLinesAfter = Math.max(0, blankLinesAfter);
    }

    /** 是否具备可点击地址（无地址时渲染为普通文字）。 */
    public boolean hasUrl() {
        return !url.isBlank();
    }

    @Override
    public @NotNull String toString() {
        return "LinkItem{content='" + content + "', url='" + url + "', hoverText='" + hoverText + "', style=" + style
                + ", blankLinesAfter=" + blankLinesAfter + "}";
    }
}
