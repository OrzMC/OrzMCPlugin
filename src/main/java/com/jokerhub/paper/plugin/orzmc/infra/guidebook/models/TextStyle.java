package com.jokerhub.paper.plugin.orzmc.infra.guidebook.models;

public class TextStyle {
    private Boolean bold = false;
    private Boolean underlined = false;
    private String color = "";

    public TextStyle() {}

    public Boolean getBold() {
        return bold;
    }

    public void setBold(Boolean bold) {
        this.bold = bold;
    }

    public Boolean getUnderlined() {
        return underlined;
    }

    public void setUnderlined(Boolean underlined) {
        this.underlined = underlined;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "TextStyle{bold=" + bold + ", underlined=" + underlined + ", color='" + color + "'}";
    }
}
