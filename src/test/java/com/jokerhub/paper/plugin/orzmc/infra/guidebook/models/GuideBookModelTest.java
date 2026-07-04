package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuideBookModelTest {

    @Test
    void contentItem_setText() {
        ContentItem item = new ContentItem();
        TextContent tc = new TextContent("hello", new TextStyle(), 1, false);
        item.setText(tc);
        assertSame(tc, item.getText());
        assertTrue(item.isText());
        assertFalse(item.isLink());
    }

    @Test
    void contentItem_setLink() {
        ContentItem item = new ContentItem();
        LinkContent lc = new LinkContent("click", "https://x.com", null, new TextStyle(), 1, false);
        item.setLink(lc);
        assertSame(lc, item.getLink());
        assertTrue(item.isLink());
        assertFalse(item.isText());
    }

    @Test
    void textContent_constructsAndExposesFields() {
        TextStyle style = new TextStyle();
        style.setColor("green");
        style.setBold(true);
        TextContent tc = new TextContent("欢迎!", style, 2, true);
        assertEquals("欢迎!", tc.content());
        assertEquals("green", tc.style().getColor());
        assertTrue(tc.style().getBold());
        assertEquals(2, tc.newlineCount());
        assertTrue(tc.pageBreak());
    }

    @Test
    void linkContent_constructsAndExposesFields() {
        TextStyle style = new TextStyle();
        style.setColor("blue");
        LinkContent lc = new LinkContent("官网", "https://orzmc.cn", "点击访问", style, 1, false);
        assertEquals("官网", lc.content());
        assertEquals("https://orzmc.cn", lc.url());
        assertEquals("点击访问", lc.hoverText());
        assertEquals("blue", lc.style().getColor());
    }

    @Test
    void textStyle_defaults() {
        TextStyle ts = new TextStyle();
        assertEquals("", ts.getColor());
        assertFalse(ts.getBold());
        assertFalse(ts.getUnderlined());
    }

    @Test
    void textStyle_setters() {
        TextStyle ts = new TextStyle();
        ts.setColor("red");
        ts.setBold(true);
        ts.setUnderlined(true);
        assertEquals("red", ts.getColor());
        assertTrue(ts.getBold());
        assertTrue(ts.getUnderlined());
    }

    @Test
    void guideBookConfig_constructsAndExposesFields() {
        GuideBookConfig cfg = new GuideBookConfig(true, "指南", "admin", List.of());
        assertTrue(cfg.enable());
        assertEquals("指南", cfg.title());
        assertEquals("admin", cfg.author());
        assertTrue(cfg.content().isEmpty());
    }
}
