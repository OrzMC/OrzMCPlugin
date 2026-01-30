package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import org.jetbrains.annotations.NotNull;

public record TextContent(String content, TextStyle style, int newlineCount, boolean pageBreak) {
    @Override
    public @NotNull String toString() {
        return "TextContent{content='" + content + "', style=" + style + ", newlineCount=" + newlineCount
                + ", pageBreak=" + pageBreak + "}";
    }
}
