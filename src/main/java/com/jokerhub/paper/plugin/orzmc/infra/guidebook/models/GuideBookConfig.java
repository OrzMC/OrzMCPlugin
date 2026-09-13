package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 指南书完整配置（不可变）：书名 / 作者 / 页列表。
 *
 * <p>页结构自 v2 起显式建模（{@link GuidePage}），v1 的扁平 {@code content:} 列表在解析期归一化到此结构。</p>
 */
public record GuideBookConfig(boolean enable, String title, String author, List<GuidePage> pages) {

    public GuideBookConfig {
        title = title == null ? "" : title;
        author = author == null ? "" : author;
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    /** 是否有可渲染的内容（无内容时视作未配置）。 */
    public boolean hasContent() {
        return pages.stream().anyMatch(page -> !page.isEmpty());
    }

    @Override
    public @NotNull String toString() {
        return "GuideBookConfig{enable=" + enable + ", title='" + title + "', author='" + author + "', pages="
                + pages.size() + "}";
    }
}
