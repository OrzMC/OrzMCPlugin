package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuidePage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * 指南书渲染：{@link GuideBookConfig} → 成书 {@link ItemStack}。
 *
 * <p>职责与解析解耦（解析在 {@link GuideBookConfigParser}）：本类只负责组件拼装、链接点击/悬停、
 * 样式与分页，并在超出原版上限时降级——单页超过 {@link #MAX_PAGE_CHARS} 字符省略该页末尾行、
 * 页数超过 {@link #MAX_PAGES} 省略末尾页，均通过告警上报，避免 {@code /guide} 因超限直接抛异常。</p>
 */
public final class GuideBookRenderer {

    /** 原版成书上限：页数。 */
    public static final int MAX_PAGES = 100;

    /** 原版成书上限：单页字符数（含换行）。 */
    public static final int MAX_PAGE_CHARS = 1024;

    /** 未配置颜色时链接的默认色。 */
    private static final TextColor DEFAULT_LINK_COLOR = TextColor.fromCSSHexString("#5555FF");

    private final Consumer<String> warningSink;

    public GuideBookRenderer(Consumer<String> warningSink) {
        this.warningSink = warningSink == null ? message -> {} : warningSink;
    }

    /** 渲染成书；配置无内容时调用方应先行判定为「未配置」。 */
    public ItemStack render(GuideBookConfig config) {
        ItemStack guideBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) guideBook.getItemMeta();
        bookMeta.setTitle(config.title());
        bookMeta.setAuthor(config.author());
        bookMeta.setGeneration(BookMeta.Generation.COPY_OF_COPY);
        List<TextComponent> renderedPages = renderPages(config);
        bookMeta.addPages(renderedPages.toArray(new TextComponent[0]));
        guideBook.setItemMeta(bookMeta);
        return guideBook;
    }

    /** 渲染页组件（纯逻辑，不依赖 Bukkit 物品栈，便于单测）：一页一个组件、空页不产出。 */
    public List<TextComponent> renderPages(GuideBookConfig config) {
        List<GuidePage> pages = config.pages();
        if (pages.size() > MAX_PAGES) {
            warningSink.accept("guide_book: 页数 " + pages.size() + " 超过上限 " + MAX_PAGES + "，已省略末尾 "
                    + (pages.size() - MAX_PAGES) + " 页");
            pages = pages.subList(0, MAX_PAGES);
        }

        List<TextComponent> renderedPages = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            List<ContentItem> items = fitToPageLimit(pages.get(index).items(), index + 1);
            if (items.isEmpty()) {
                continue; // 空页不渲染（避免书里出现无内容的白页）
            }
            TextComponent.Builder pageBuilder = Component.text();
            for (ContentItem item : items) {
                pageBuilder.append(renderItem(item, index + 1));
            }
            renderedPages.add(pageBuilder.build());
        }
        return List.copyOf(renderedPages);
    }

    /**
     * 单页字符数上限降级：超出时省略该页末尾行（而非截断某行中间，避免把链接/文字切一半），并告警。
     * 估算口径为「各行文本长度 + 行后换行数」，不含样式/组件开销。
     */
    private List<ContentItem> fitToPageLimit(List<ContentItem> items, int pageNo) {
        List<ContentItem> kept = new ArrayList<>(items.size());
        int used = 0;
        for (int index = 0; index < items.size(); index++) {
            ContentItem item = items.get(index);
            used += item.content().length() + item.blankLinesAfter();
            if (used > MAX_PAGE_CHARS) {
                warningSink.accept("guide_book: 第 " + pageNo + " 页超过单页上限 " + MAX_PAGE_CHARS + " 字符，已省略末尾 "
                        + (items.size() - index) + " 行（请在 pages: 下再起一个 '-' 列表拆成多页）");
                break;
            }
            kept.add(item);
        }
        return kept;
    }

    private TextComponent renderItem(ContentItem item, int pageNo) {
        TextComponent.Builder builder = Component.text();
        TextStyle style = item.style();
        String plain = item.content();
        if (item instanceof LinkItem link) {
            builder.append(Component.text(link.content()));
            TextColor color = colorOf(style, pageNo);
            builder.color(color != null ? color : DEFAULT_LINK_COLOR);
            if (link.hasUrl()) {
                builder.clickEvent(ClickEvent.openUrl(link.url()));
                if (!link.hoverText().isBlank()) {
                    builder.hoverEvent(HoverEvent.showText(Component.text(link.hoverText())));
                }
            }
        } else if (!plain.isEmpty()) {
            builder.append(Component.text(plain));
            TextColor color = colorOf(style, pageNo);
            if (color != null) {
                builder.color(color);
            }
        }
        if (style.bold()) {
            builder.decorate(TextDecoration.BOLD);
        }
        if (style.underlined()) {
            builder.decorate(TextDecoration.UNDERLINED);
        }
        for (int i = 0; i < item.blankLinesAfter(); i++) {
            builder.append(Component.newline());
        }
        return builder.build();
    }

    private TextColor colorOf(TextStyle style, int pageNo) {
        if (!style.hasColor()) {
            return null;
        }
        TextColor color = GuideColorParser.parse(style.color());
        if (color == null) {
            warningSink.accept("guide_book: 第 " + pageNo + " 页颜色 '" + style.color() + "' 不是命名色或 #RRGGBB，已忽略");
        }
        return color;
    }
}
