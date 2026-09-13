package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 指南书的一页（不可变）。
 *
 * <p>对应 v2 简写格式里 {@code pages:} 下的一个列表；v1 的 {@code content:} 通过
 * {@code page_break: true} 切分后同样归一化为页列表。</p>
 */
public record GuidePage(List<ContentItem> items) {

    public GuidePage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public @NotNull String toString() {
        return "GuidePage" + items;
    }
}
