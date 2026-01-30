package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

public class ContentItem {
    private TextContent text;
    private LinkContent link;

    public ContentItem() {}

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

    public boolean isText() {
        return text != null;
    }

    public boolean isLink() {
        return link != null;
    }

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
