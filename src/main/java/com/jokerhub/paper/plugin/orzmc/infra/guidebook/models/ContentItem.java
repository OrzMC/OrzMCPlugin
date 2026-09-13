package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

/**
 * 指南书的一「行」内容（sealed：渲染层用模式匹配穷举，新增类型编译器即报错）。
 *
 * <p>约定：{@link #blankLinesAfter()} 表示本行内容之后追加的换行数——即 v1 的 {@code newline_count}
 * 语义（默认 1，即段后空一行）；同一视觉行被拆成多段（行内混排）时，仅最后一段带换行数，其余为 0。</p>
 */
public sealed interface ContentItem permits TextItem, LinkItem {

    /** 显示文本（空串表示一个空行）。 */
    String content();

    /** 整段样式（无样式时为 {@link TextStyle#NONE}）。 */
    TextStyle style();

    /** 本行之后追加的换行数（0 = 与下一行紧贴）。 */
    int blankLinesAfter();
}
