package com.jokerhub.paper.plugin.orzmc.utils.guidebook.models;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

// 指南书配置主类
public record GuideBookConfig(boolean enable, String title, String author, List<ContentItem> content) {
    public GuideBookConfig(boolean enable, String title, String author, List<ContentItem> content) {
        this.enable = enable;
        this.title = title;
        this.author = author;
        this.content = content != null ? content : new ArrayList<>();
    }

    @Override
    public @NotNull String toString() {
        return "GuideBookConfig{title='" + title + "', author='" + author + "', content=" + content + "}";
    }
}
