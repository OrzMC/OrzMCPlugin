package com.jokerhub.paper.plugin.orzmc.utils.guidebook.models;

import org.jetbrains.annotations.NotNull;

/**
 * @param newlineCount 新增：换行数量
 */
// 文本内容类
public record TextContent(String content, TextStyle style, int newlineCount, boolean pageBreak) {

    @Override
    public @NotNull String toString() {
        return "TextContent{content='" + content + "', style=" + style + ", newlineCount=" + newlineCount
                + ", pageBreak=" + pageBreak + "}";
    }
}
