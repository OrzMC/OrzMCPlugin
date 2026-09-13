package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuidePage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 成书渲染：分页、链接点击/悬停、样式、颜色口径、原版页数/单页字符上限降级。 */
class GuideBookRendererTest {

    private final List<String> warnings = new ArrayList<>();
    private GuideBookRenderer renderer;

    @BeforeEach
    void setUp() {
        warnings.clear();
        renderer = new GuideBookRenderer(warnings::add);
    }

    private static GuideBookConfig config(GuidePage... pages) {
        return new GuideBookConfig(true, "指南", "服主", List.of(pages));
    }

    /** 渲染页组件（纯逻辑入口，无需 Bukkit 物品栈）。 */
    private List<TextComponent> renderedPages(GuideBookConfig config) {
        return renderer.renderPages(config);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<Component> flatten(Component component) {
        List<Component> flat = new ArrayList<>();
        flat.add(component);
        component.children().forEach(child -> flat.addAll(flatten(child)));
        return flat;
    }

    @Test
    void rendersTitleAuthorAndPages() {
        List<TextComponent> pages =
                renderedPages(config(new GuidePage(List.of(new TextItem("第一页", TextStyle.NONE, 1)))));

        assertEquals(1, pages.size());
    }

    @Test
    void multiplePages_renderOneComponentPerPage() {
        List<TextComponent> pages = renderedPages(config(
                new GuidePage(List.of(new TextItem("第一页", TextStyle.NONE, 1))),
                new GuidePage(List.of(new TextItem("第二页", TextStyle.NONE, 1)))));

        assertEquals(2, pages.size());
        assertEquals("第一页\n", plain(pages.get(0)));
        assertEquals("第二页\n", plain(pages.get(1)));
    }

    @Test
    void emptyPage_isSkipped() {
        List<TextComponent> pages = renderedPages(
                config(new GuidePage(List.of()), new GuidePage(List.of(new TextItem("有内容", TextStyle.NONE, 1)))));

        assertEquals(1, pages.size());
        assertEquals("有内容\n", plain(pages.get(0)));
    }

    @Test
    void blankLinesAfter_isRendered() {
        List<TextComponent> pages = renderedPages(config(
                new GuidePage(List.of(new TextItem("行一", TextStyle.NONE, 2), new TextItem("行二", TextStyle.NONE, 0)))));

        assertEquals("行一\n\n行二", plain(pages.get(0)));
    }

    @Test
    void link_getsClickEventHoverAndDefaultColor() {
        List<TextComponent> pages = renderedPages(
                config(new GuidePage(List.of(new LinkItem("主页", "https://orzmc.cn", "点我", TextStyle.NONE, 1)))));
        List<Component> flat = flatten(pages.get(0));

        ClickEvent<?> click = flat.stream()
                .map(Component::clickEvent)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        assertNotNull(click);
        assertEquals(ClickEvent.Action.OPEN_URL, click.action());
        assertEquals("https://orzmc.cn", ((ClickEvent.Payload.Text) click.payload()).value());

        HoverEvent<?> hover = flat.stream()
                .map(Component::hoverEvent)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        assertNotNull(hover);
        assertEquals(TextColor.fromHexString("#5555FF"), flat.get(1).color());
    }

    @Test
    void linkWithoutHover_hasNoHoverEvent() {
        List<TextComponent> pages = renderedPages(
                config(new GuidePage(List.of(new LinkItem("主页", "https://orzmc.cn", "", TextStyle.NONE, 1)))));

        assertTrue(flatten(pages.get(0)).stream().allMatch(c -> c.hoverEvent() == null));
    }

    @Test
    void styles_boldUnderlineAndNamedColor() {
        TextStyle style = new TextStyle(true, true, "AQUA");
        List<TextComponent> pages = renderedPages(config(new GuidePage(List.of(new TextItem("标题", style, 1)))));
        List<Component> flat = flatten(pages.get(0));

        assertEquals(NamedTextColor.AQUA, flat.get(1).color());
        assertTrue(flat.get(1).hasDecoration(TextDecoration.BOLD));
        assertTrue(flat.get(1).hasDecoration(TextDecoration.UNDERLINED));
        assertFalse(warnings.contains("guide_book: 第 1 页颜色 'AQUA' 不是命名色或 #RRGGBB，已忽略"));
    }

    @Test
    void invalidColor_warnsAndFallsBack() {
        TextStyle style = new TextStyle(false, false, "notacolor");
        renderedPages(config(new GuidePage(List.of(new TextItem("文字", style, 1)))));

        assertTrue(warnings.stream().anyMatch(w -> w.contains("颜色 'notacolor'")), warnings.toString());
    }

    @Test
    void pageOverCharLimit_dropsTrailingItemsAndWarns() {
        StringBuilder longText = new StringBuilder();
        longText.append("x".repeat(GuideBookRenderer.MAX_PAGE_CHARS - 4));
        List<com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem> items = new ArrayList<>();
        items.add(new TextItem(longText.toString(), TextStyle.NONE, 1));
        items.add(new TextItem("这行应被省略", TextStyle.NONE, 1));

        List<TextComponent> pages = renderedPages(config(new GuidePage(items)));

        assertFalse(plain(pages.get(0)).contains("这行应被省略"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("超过单页上限")), warnings.toString());
    }

    @Test
    void tooManyPages_keepsLimitAndWarns() {
        List<GuidePage> pages = new ArrayList<>();
        for (int i = 0; i < GuideBookRenderer.MAX_PAGES + 3; i++) {
            pages.add(new GuidePage(List.of(new TextItem("p" + i, TextStyle.NONE, 1))));
        }

        List<TextComponent> rendered = renderedPages(new GuideBookConfig(true, "指南", "服主", pages));

        assertEquals(GuideBookRenderer.MAX_PAGES, rendered.size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("超过上限")), warnings.toString());
    }
}
