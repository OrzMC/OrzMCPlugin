package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

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

    public static MessageEnvelope renderEnvelope(
            String templateKey, String template, Map<String, String> vars, FileConfiguration cfg) {
        String message = render(template, vars);
        MessageEnvelope.Format format = formatFromConfig(templateKey, cfg);
        return new MessageEnvelope(MessageEnvelope.TargetType.PUBLIC, message, null, format);
    }

    public static String resolveTemplate(String templateKey, FileConfiguration cfg, String fallback) {
        if (cfg == null || templateKey == null || templateKey.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String locale = cfg.getString("templates.locale", "zh-CN");
        String localized = cfg.getString("templates.i18n.command." + locale + "." + templateKey);
        if (localized != null && !localized.isEmpty()) {
            return localized;
        }
        String direct = cfg.getString("templates." + templateKey);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        return fallback == null ? "" : fallback;
    }

    private static MessageEnvelope.Format formatFromConfig(String templateKey, FileConfiguration cfg) {
        if (cfg == null || templateKey == null || templateKey.isEmpty()) {
            return MessageEnvelope.Format.DEFAULT;
        }
        String raw = cfg.getString("templates.format." + templateKey, "DEFAULT");
        if (raw.isEmpty()) {
            return MessageEnvelope.Format.DEFAULT;
        }
        try {
            return MessageEnvelope.Format.valueOf(raw.toUpperCase());
        } catch (Exception ignored) {
            return MessageEnvelope.Format.DEFAULT;
        }
    }
}
