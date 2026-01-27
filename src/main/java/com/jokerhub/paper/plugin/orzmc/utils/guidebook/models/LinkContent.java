package com.jokerhub.paper.plugin.orzmc.utils.guidebook.models;

import org.jetbrains.annotations.NotNull;

/**
 * @param newlineCount 新增：换行数量
 */
// 链接内容类
public record LinkContent(
        String content, String url, String hoverText, TextStyle style, int newlineCount, boolean pageBreak) {

    @Override
    public @NotNull String toString() {
        return "LinkContent{content='" + content + "', url='" + url + "', hoverText='"
                + hoverText + "', style=" + style + ", newlineCount="
                + newlineCount + ", pageBreak=" + pageBreak + "}";
    }
}
