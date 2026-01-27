package com.jokerhub.paper.plugin.orzmc.utils.guidebook.models;

// 内容项包装类
public class ContentItem {
    private TextContent text;
    private LinkContent link;

    public ContentItem() {}

    // Getter 和 Setter
    public TextContent getText() {
        return text;
    }

    public void setText(TextContent text) {
        this.text = text;
    }

    public LinkContent getLink() {
        return link;
    }

    public void setLink(LinkContent link) {
        this.link = link;
    }

    // 判断内容类型
    public boolean isText() {
        return text != null;
    }

    public boolean isLink() {
        return link != null;
    }

    // 获取换行数量（根据类型）
    public int getNewlineCount() {
        if (isText()) return text.newlineCount();
        if (isLink()) return link.newlineCount();
        return 0;
    }

    public boolean getPageBreak() {
        if (isText()) return text.pageBreak();
        if (isLink()) return link.pageBreak();
        return false;
    }

    public TextStyle getStyle() {
        if (isText()) return text.style();
        if (isLink()) return link.style();
        return null;
    }

    @Override
    public String toString() {
        if (isText()) return "ContentItem{text=" + text + "}";
        if (isLink()) return "ContentItem{link=" + link + "}";
        return "ContentItem{unknown}";
    }
}
