package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkContent;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextContent;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GuideBookConfigParserTest {

    @Mock
    private JavaPlugin plugin;

    @Mock
    private ConfigService configService;

    @Mock
    private Logger logger;

    private GuideBookConfigParser parser;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        configService = mock(ConfigService.class);
        logger = mock(Logger.class);
        parser = new GuideBookConfigParser(plugin, configService);
    }

    private YamlConfiguration load(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    void parseConfig_fullConfig_parsesAllFields() {
        String yaml = """
                enable: true
                title: "服务器指南"
                author: "OrzMC"
                content:
                  - text:
                      content: "欢迎"
                      newline_count: 2
                      page_break: true
                      style:
                        bold: true
                        color: "AQUA"
                  - link:
                      content: "官网"
                      url: "https://example.com"
                      hover_text: "点击访问"
                """;
        when(configService.getConfig("guide_book")).thenReturn(load(yaml));

        GuideBookConfig config = parser.parseConfig();

        assertNotNull(config);
        assertTrue(config.enable());
        assertEquals("服务器指南", config.title());
        assertEquals("OrzMC", config.author());
        assertEquals(2, config.content().size());

        ContentItem first = config.content().get(0);
        assertTrue(first.isText());
        TextContent text = first.getText();
        assertEquals("欢迎", text.content());
        assertEquals(2, text.newlineCount());
        assertTrue(text.pageBreak());
        assertTrue(text.style().getBold());
        assertEquals("AQUA", text.style().getColor());

        ContentItem second = config.content().get(1);
        assertTrue(second.isLink());
        LinkContent link = second.getLink();
        assertEquals("官网", link.content());
        assertEquals("https://example.com", link.url());
        assertEquals("点击访问", link.hoverText());
        assertFalse(link.style().getBold());
        assertEquals("", link.style().getColor());
    }

    @Test
    void parseConfig_minimalConfig_usesDefaults() {
        String yaml = "content: []\n";
        when(configService.getConfig("guide_book")).thenReturn(load(yaml));

        GuideBookConfig config = parser.parseConfig();

        assertNotNull(config);
        assertTrue(config.enable(), "enable 默认应为 true");
        assertEquals("新手指南", config.title(), "title 默认应为 新手指南");
        assertEquals("服务器", config.author(), "author 默认应为 服务器");
        assertTrue(config.content().isEmpty());
    }

    @Test
    void parseConfig_unknownContentType_skipsWithWarning() {
        when(plugin.getLogger()).thenReturn(logger);
        String yaml = """
                content:
                  - foo: "bar"
                """;
        when(configService.getConfig("guide_book")).thenReturn(load(yaml));

        GuideBookConfig config = parser.parseConfig();

        assertNotNull(config);
        assertFalse(config.content().isEmpty());
        assertFalse(config.content().get(0).isText());
        assertFalse(config.content().get(0).isLink());
        verify(logger).warning(contains("类型未知"));
    }

    @Test
    void parseConfig_textStyleUnderlined_parsed() {
        String yaml = """
                content:
                  - text:
                      content: "下划线"
                      style:
                        underlined: true
                """;
        when(configService.getConfig("guide_book")).thenReturn(load(yaml));

        GuideBookConfig config = parser.parseConfig();

        ContentItem item = config.content().get(0);
        assertTrue(item.getText().style().getUnderlined());
        assertFalse(item.getText().style().getBold());
    }

    @Test
    void parseConfig_nonYamlConfig_returnsNull() {
        when(configService.getConfig("guide_book")).thenReturn(mock(FileConfiguration.class));

        assertNull(parser.parseConfig());
    }

    @Test
    void parseConfig_reloadThrows_returnsNullAndLogsSevere() {
        when(plugin.getLogger()).thenReturn(logger);
        doThrow(new RuntimeException("io error")).when(configService).reloadConfig("guide_book");

        assertNull(parser.parseConfig());

        verify(logger).log(eq(Level.SEVERE), contains("guide_book"), any(Throwable.class));
    }
}
