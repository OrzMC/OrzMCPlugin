package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GuideBookConfigParser {
    private final OrzMC plugin;

    public GuideBookConfigParser(OrzMC plugin) {
        this.plugin = plugin;
    }

    public GuideBookConfig parseConfig() {
        String guideBookConfigFileName = "guide_book";
        try {
            plugin.configManager.reloadConfig(guideBookConfigFileName);
            FileConfiguration configFile = plugin.configManager.getConfig("guide_book");
            if (configFile instanceof YamlConfiguration) {
                return parseGuideBookConfig((YamlConfiguration) configFile);
            } else {
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "解析" + guideBookConfigFileName + "配置文件时发生错误", e);
            return null;
        }
    }

    private GuideBookConfig parseGuideBookConfig(YamlConfiguration config) {
        boolean enable = config.getBoolean("enable", true);
        String title = config.getString("title", "新手指南");
        String author = config.getString("author", "服务器");
        List<ContentItem> contentItems = parseContentList(config.getMapList("content"));
        return new GuideBookConfig(enable, title, author, contentItems);
    }

    private List<ContentItem> parseContentList(List<Map<?, ?>> contentList) {
        List<ContentItem> contentItems = new ArrayList<>();
        if (contentList == null) {
            plugin.getLogger().warning("配置中的 content 列表为空或不存在");
            return contentItems;
        }
        for (int i = 0; i < contentList.size(); i++) {
            Map<?, ?> itemMap = contentList.get(i);
            ContentItem contentItem = parseContentItem(itemMap, i);
            contentItems.add(contentItem);
        }
        return contentItems;
    }

    @SuppressWarnings("unchecked")
    private ContentItem parseContentItem(Map<?, ?> itemMap, int index) {
        ContentItem contentItem = new ContentItem();
        try {
            if (itemMap.containsKey("text")) {
                Object textObj = itemMap.get("text");
                if (textObj instanceof Map) {
                    Map<String, Object> textMap = (Map<String, Object>) textObj;
                    contentItem.setText(parseTextContent(textMap));
                }
            } else if (itemMap.containsKey("link")) {
                Object linkObj = itemMap.get("link");
                if (linkObj instanceof Map) {
                    Map<String, Object> linkMap = (Map<String, Object>) linkObj;
                    contentItem.setLink(parseLinkContent(linkMap));
                }
            } else {
                plugin.getLogger().warning("第 " + (index + 1) + " 个内容项类型未知，已跳过");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("解析第 " + (index + 1) + " 个内容项时发生错误: " + e.getMessage());
        }
        return contentItem;
    }

    private TextContent parseTextContent(Map<String, Object> textMap) {
        String content = (String) textMap.get("content");
        TextStyle style = parseTextStyle(getStyleMap(textMap));
        int newlineCount = getNewlineCount(textMap);
        boolean pageBreak = getPageBreak(textMap);
        return new TextContent(content, style, newlineCount, pageBreak);
    }

    private LinkContent parseLinkContent(Map<String, Object> linkMap) {
        String content = (String) linkMap.get("content");
        String url = (String) linkMap.get("url");
        String hoverText = (String) linkMap.get("hover_text");
        TextStyle style = parseTextStyle(getStyleMap(linkMap));
        int newlineCount = getNewlineCount(linkMap);
        boolean pageBreak = getPageBreak(linkMap);
        return new LinkContent(content, url, hoverText, style, newlineCount, pageBreak);
    }

    private TextStyle parseTextStyle(Map<String, Object> styleMap) {
        TextStyle textStyle = new TextStyle();
        if (styleMap == null || styleMap.isEmpty()) {
            return textStyle;
        }
        if (styleMap.containsKey("bold")) {
            textStyle.setBold((Boolean) styleMap.get("bold"));
        }
        if (styleMap.containsKey("underlined")) {
            textStyle.setUnderlined((Boolean) styleMap.get("underlined"));
        }
        if (styleMap.containsKey("color")) {
            textStyle.setColor((String) styleMap.get("color"));
        }
        return textStyle;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStyleMap(Map<String, Object> contentMap) {
        Object styleObj = contentMap.get("style");
        if (styleObj instanceof Map) {
            return (Map<String, Object>) styleObj;
        }
        return null;
    }

    private int getNewlineCount(Map<String, Object> contentMap) {
        Object newlineObj = contentMap.get("newline_count");
        if (newlineObj instanceof Integer) {
            return (Integer) newlineObj;
        }
        return 1;
    }

    private boolean getPageBreak(Map<String, Object> contentMap) {
        Object pageBreakObj = contentMap.get("page_break");
        if (pageBreakObj instanceof Boolean) {
            return (Boolean) pageBreakObj;
        }
        return false;
    }
}
