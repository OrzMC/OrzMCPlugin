package com.jokerhub.paper.plugin.orzmc.infra.templates;

import java.util.Map;

public final class TemplateRenderer {
    private TemplateRenderer() {}

    public static String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) return "";
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String key = "{" + e.getKey() + "}";
                out = out.replace(key, e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }
}
