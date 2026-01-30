package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import org.jetbrains.annotations.NotNull;

public record LinkContent(
        String content, String url, String hoverText, TextStyle style, int newlineCount, boolean pageBreak) {
    @Override
    public @NotNull String toString() {
        return "LinkContent{content='" + content + "', url='" + url + "', hoverText='" + hoverText + "', style=" + style
                + ", newlineCount=" + newlineCount + ", pageBreak=" + pageBreak + "}";
    }
}
